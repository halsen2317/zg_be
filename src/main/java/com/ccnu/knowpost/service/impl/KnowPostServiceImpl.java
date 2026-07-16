package com.ccnu.knowpost.service.impl;

import com.ccnu.common.exception.BusinessException;
import com.ccnu.common.exception.ErrorCode;
import com.ccnu.counter.service.CounterService;
import com.ccnu.counter.service.UserCounterService;
import com.ccnu.knowpost.api.dto.KnowPostDetailResponse;
import com.ccnu.knowpost.id.SnowflakeIdGenerator;
import com.ccnu.knowpost.mapper.KnowPostMapper;
import com.ccnu.knowpost.model.KnowPost;
import com.ccnu.knowpost.model.KnowPostDetailRow;
import com.ccnu.knowpost.service.KnowPostService;
import com.ccnu.llm.rag.RagIndexService;
import com.ccnu.search.index.SearchIndexService;
import com.ccnu.storage.config.OssProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 知文服务实现（简化版）。
 *
 * <p>草稿 → 上传确认 → 元数据编辑 → 发布 的完整流程。
 * 取消：Outbox 事件、Caffeine 本地缓存、HotKeyDetector、SingleFlight 锁。</p>
 */
@Service
public class KnowPostServiceImpl implements KnowPostService {

    private static final Logger log = LoggerFactory.getLogger(KnowPostServiceImpl.class);
    private static final int DETAIL_LAYOUT_VER = 1;

    private final KnowPostMapper mapper;
    private final SnowflakeIdGenerator idGen;
    private final ObjectMapper objectMapper;
    private final OssProperties ossProperties;
    private final StringRedisTemplate redis;
    private final CounterService counterService;
    private final UserCounterService userCounterService;
    private final SearchIndexService searchIndexService;
    private final RagIndexService ragIndexService;

    public KnowPostServiceImpl(KnowPostMapper mapper, SnowflakeIdGenerator idGen,
                               ObjectMapper objectMapper, OssProperties ossProperties,
                               StringRedisTemplate redis, CounterService counterService,
                               UserCounterService userCounterService,
                               SearchIndexService searchIndexService,
                               RagIndexService ragIndexService) {
        this.mapper = mapper;
        this.idGen = idGen;
        this.objectMapper = objectMapper;
        this.ossProperties = ossProperties;
        this.redis = redis;
        this.counterService = counterService;
        this.userCounterService = userCounterService;
        this.searchIndexService = searchIndexService;
        this.ragIndexService = ragIndexService;
    }

    @Override
    @Transactional
    public long createDraft(long creatorId) {
        long id = idGen.nextId();
        Instant now = Instant.now();
        KnowPost post = KnowPost.builder()
                .id(id).creatorId(creatorId).status("draft")
                .type("image_text").visible("public").isTop(false)
                .createTime(now).updateTime(now).build();
        mapper.insertDraft(post);
        return id;
    }

    @Override
    @Transactional
    public void confirmContent(long creatorId, long id, String objectKey, String etag, Long size, String sha256) {
        invalidateCache(id);
        KnowPost post = KnowPost.builder()
                .id(id).creatorId(creatorId)
                .contentObjectKey(objectKey).contentEtag(etag)
                .contentSize(size).contentSha256(sha256)
                .contentUrl(publicUrl(objectKey)).updateTime(Instant.now()).build();
        if (mapper.updateContent(post) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        invalidateCache(id);
        try { ragIndexService.ensureIndexed(id); } catch (Exception ignored) {}
    }

    @Override
    @Transactional
    public void updateMetadata(long creatorId, long id, String title, Long tagId, List<String> tags,
                               List<String> imgUrls, String visible, Boolean isTop, String description) {
        invalidateCache(id);
        KnowPost post = KnowPost.builder()
                .id(id).creatorId(creatorId).title(title).tagId(tagId)
                .tags(toJsonOrNull(tags)).imgUrls(toJsonOrNull(imgUrls))
                .visible(visible).isTop(isTop).description(description)
                .type("image_text").updateTime(Instant.now()).build();
        if (mapper.updateMetadata(post) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        try { searchIndexService.upsertKnowPost(id); } catch (Exception ignored) {}
        invalidateCache(id);
    }

    @Override
    @Transactional
    public void publish(long creatorId, long id) {
        if (mapper.publish(id, creatorId) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        try { userCounterService.incrementPosts(creatorId, 1); } catch (Exception ignored) {}
        try { searchIndexService.upsertKnowPost(id); } catch (Exception ignored) {}
        try { ragIndexService.ensureIndexed(id); } catch (Exception ignored) {}
    }

    @Override
    @Transactional
    public void updateTop(long creatorId, long id, boolean isTop) {
        invalidateCache(id);
        if (mapper.updateTop(id, creatorId, isTop) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        invalidateCache(id);
    }

    @Override
    @Transactional
    public void updateVisibility(long creatorId, long id, String visible) {
        if (!isValidVisible(visible)) throw new BusinessException(ErrorCode.BAD_REQUEST, "可见性取值非法");
        invalidateCache(id);
        if (mapper.updateVisibility(id, creatorId, visible) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        invalidateCache(id);
    }

    @Override
    @Transactional
    public void delete(long creatorId, long id) {
        invalidateCache(id);
        if (mapper.softDelete(id, creatorId) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        try { searchIndexService.softDeleteKnowPost(id); } catch (Exception ignored) {}
        invalidateCache(id);
    }

    @Override
    @Transactional(readOnly = true)
    public KnowPostDetailResponse getDetail(long id, Long uid) {
        String pageKey = "knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER;

        // 查 Redis 缓存
        String cached = redis.opsForValue().get(pageKey);
        if (cached != null) {
            if ("NULL".equals(cached)) throw new BusinessException(ErrorCode.BAD_REQUEST, "内容不存在");
            try {
                KnowPostDetailResponse base = objectMapper.readValue(cached, KnowPostDetailResponse.class);
                return enrichDetail(base, uid, true);
            } catch (Exception ignored) {}
        }

        // 查 DB
        KnowPostDetailRow row = mapper.findDetailById(id);
        if (row == null || "deleted".equals(row.getStatus())) {
            redis.opsForValue().set(pageKey, "NULL", Duration.ofSeconds(30 + ThreadLocalRandom.current().nextInt(31)));
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容不存在");
        }

        boolean isPublic = "published".equals(row.getStatus()) && "public".equals(row.getVisible());
        boolean isOwner = uid != null && uid.equals(row.getCreatorId());
        if (!isPublic && !isOwner) throw new BusinessException(ErrorCode.BAD_REQUEST, "无权限查看");

        List<String> images = parseStringArray(row.getImgUrls());
        List<String> tags = parseStringArray(row.getTags());
        Map<String, Long> counts = counterService.getCounts("knowpost", String.valueOf(row.getId()), List.of("like", "fav"));

        KnowPostDetailResponse resp = new KnowPostDetailResponse(
                String.valueOf(row.getId()), row.getTitle(), row.getDescription(),
                row.getContentUrl(), images, tags, String.valueOf(row.getCreatorId()),
                row.getAuthorAvatar(), row.getAuthorNickname(), row.getAuthorTagJson(),
                counts.getOrDefault("like", 0L), counts.getOrDefault("fav", 0L),
                null, null, row.getIsTop(), row.getVisible(), row.getType(), row.getPublishTime());

        try {
            String json = objectMapper.writeValueAsString(resp);
            int ttl = 60 + ThreadLocalRandom.current().nextInt(30);
            redis.opsForValue().set(pageKey, json, Duration.ofSeconds(ttl));
        } catch (Exception ignored) {}
        return enrichDetail(resp, uid, false);
    }

    // ──────────── 私有方法 ────────────

    private KnowPostDetailResponse enrichDetail(KnowPostDetailResponse base, Long uid, boolean refreshCounts) {
        Long lc = base.likeCount(), fc = base.favoriteCount();
        if (refreshCounts) {
            Map<String, Long> counts = counterService.getCounts("knowpost", base.id(), List.of("like", "fav"));
            if (counts != null) { lc = counts.getOrDefault("like", lc); fc = counts.getOrDefault("fav", fc); }
        }
        return new KnowPostDetailResponse(base.id(), base.title(), base.description(), base.contentUrl(),
                base.images(), base.tags(), base.authorId(), base.authorAvatar(), base.authorNickname(),
                base.authorTagJson(), lc, fc,
                uid != null && counterService.isLiked("knowpost", base.id(), uid),
                uid != null && counterService.isFaved("knowpost", base.id(), uid),
                base.isTop(), base.visible(), base.type(), base.publishTime());
    }

    private void invalidateCache(long id) {
        redis.delete("knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER);
    }

    private boolean isValidVisible(String v) {
        return v != null && switch (v) {
            case "public", "followers", "school", "private", "unlisted" -> true;
            default -> false;
        };
    }

    private String toJsonOrNull(List<String> list) {
        if (list == null) return null;
        try { return objectMapper.writeValueAsString(list); } catch (JsonProcessingException e) { return null; }
    }

    private String publicUrl(String objectKey) {
        String domain = ossProperties.getPublicDomain();
        if (domain != null && !domain.isBlank()) return domain.replaceAll("/$", "") + "/" + objectKey;
        return "https://" + ossProperties.getBucket() + "." + ossProperties.getEndpoint() + "/" + objectKey;
    }

    private List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); } catch (Exception e) { return Collections.emptyList(); }
    }
}