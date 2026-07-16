package com.ccnu.knowpost.service.impl;

import com.ccnu.counter.service.CounterService;
import com.ccnu.knowpost.api.dto.FeedItemResponse;
import com.ccnu.knowpost.api.dto.FeedPageResponse;
import com.ccnu.knowpost.mapper.KnowPostMapper;
import com.ccnu.knowpost.model.KnowPostFeedRow;
import com.ccnu.knowpost.service.KnowPostFeedService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Feed 流服务实现（简化版：Redis 片段缓存 + MySQL 回源，固定 TTL）。
 */
@Service
public class KnowPostFeedServiceImpl implements KnowPostFeedService {

    private static final Logger log = LoggerFactory.getLogger(KnowPostFeedServiceImpl.class);

    private final KnowPostMapper mapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CounterService counterService;

    public KnowPostFeedServiceImpl(KnowPostMapper mapper, StringRedisTemplate redis,
                                   ObjectMapper objectMapper, CounterService counterService) {
        this.mapper = mapper; this.redis = redis;
        this.objectMapper = objectMapper; this.counterService = counterService;
    }

    @Override
    public FeedPageResponse getPublicFeed(int page, int size, Long uid) {
        int s = Math.min(Math.max(size, 1), 50);
        int p = Math.max(page, 1);
        long hour = System.currentTimeMillis() / 3600000L;
        String idsKey = "feed:public:ids:" + s + ":" + hour + ":" + p;
        String moreKey = idsKey + ":hasMore";

        // 尝试 Redis 片段缓存
        FeedPageResponse cached = assemble(idsKey, moreKey, p, s, uid);
        if (cached != null) { log.info("feed.public source=cache p={} s={}", p, s); return cached; }

        // DB 回源
        int off = (p - 1) * s;
        List<KnowPostFeedRow> rows = mapper.listFeedPublic(s + 1, off);
        boolean hasMore = rows.size() > s;
        if (hasMore) rows = rows.subList(0, s);

        List<FeedItemResponse> items = mapRows(rows, null, false);
        Duration ttl = Duration.ofSeconds(60 + ThreadLocalRandom.current().nextInt(30));
        writeFragments(idsKey, moreKey, rows, items, hasMore, ttl);
        log.info("feed.public source=db p={} s={} hasMore={}", p, s, hasMore);
        return new FeedPageResponse(enrich(items, uid), p, s, hasMore);
    }

    @Override
    public FeedPageResponse getMyPublished(long userId, int page, int size) {
        int s = Math.min(Math.max(size, 1), 50);
        int p = Math.max(page, 1);
        String key = "feed:mine:" + userId + ":" + s + ":" + p;

        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            try {
                FeedPageResponse r = objectMapper.readValue(cached, FeedPageResponse.class);
                log.info("feed.mine source=cache key={}", key);
                return new FeedPageResponse(enrich(r.items(), userId), r.page(), r.size(), r.hasMore());
            } catch (Exception ignored) {}
        }

        int off = (p - 1) * s;
        List<KnowPostFeedRow> rows = mapper.listMyPublished(userId, s + 1, off);
        boolean hasMore = rows.size() > s;
        if (hasMore) rows = rows.subList(0, s);

        List<FeedItemResponse> items = mapRows(rows, userId, true);
        FeedPageResponse r = new FeedPageResponse(items, p, s, hasMore);
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(r),
                    Duration.ofSeconds(30 + ThreadLocalRandom.current().nextInt(20)));
        } catch (Exception ignored) {}
        log.info("feed.mine source=db key={} p={} s={} u={} hasMore={}", key, p, s, userId, hasMore);
        return r;
    }

    // ─── 私有方法 ───

    private FeedPageResponse assemble(String idsKey, String moreKey, int p, int s, Long uid) {
        List<String> ids = redis.opsForList().range(idsKey, 0, s - 1);
        if (ids == null || ids.isEmpty()) return null;
        List<String> itemKeys = ids.stream().map(id -> "feed:item:" + id).toList();
        List<String> jsons = redis.opsForValue().multiGet(itemKeys);
        List<FeedItemResponse> items = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            if (jsons == null || jsons.get(i) == null) return null;
            try { items.add(objectMapper.readValue(jsons.get(i), FeedItemResponse.class)); } catch (Exception e) { return null; }
        }
        List<FeedItemResponse> enriched = new ArrayList<>();
        for (FeedItemResponse base : items) {
            Map<String, Long> cnt = counterService.getCounts("knowpost", base.id(), List.of("like", "fav"));
            enriched.add(new FeedItemResponse(base.id(), base.title(), base.description(), base.coverImage(), base.tags(),
                    base.authorAvatar(), base.authorNickname(), base.tagJson(),
                    cnt.getOrDefault("like", 0L), cnt.getOrDefault("fav", 0L),
                    uid != null && counterService.isLiked("knowpost", base.id(), uid),
                    uid != null && counterService.isFaved("knowpost", base.id(), uid), base.isTop()));
        }
        String more = redis.opsForValue().get(moreKey);
        return new FeedPageResponse(enriched, p, s, more != null ? "1".equals(more) : ids.size() == s);
    }

    private void writeFragments(String idsKey, String moreKey, List<KnowPostFeedRow> rows,
                                List<FeedItemResponse> items, boolean hasMore, Duration ttl) {
        List<String> idVals = rows.stream().map(r -> String.valueOf(r.getId())).toList();
        if (!idVals.isEmpty()) {
            redis.opsForList().leftPushAll(idsKey, idVals);
            redis.expire(idsKey, ttl);
            redis.opsForValue().set(moreKey, hasMore && idVals.size() == 50 ? "1" : "0",
                    Duration.ofSeconds(10 + ThreadLocalRandom.current().nextInt(11)));
        }
        for (FeedItemResponse it : items) {
            try { redis.opsForValue().set("feed:item:" + it.id(), objectMapper.writeValueAsString(it), ttl); } catch (Exception ignored) {}
        }
    }

    private List<FeedItemResponse> enrich(List<FeedItemResponse> base, Long uid) {
        List<FeedItemResponse> out = new ArrayList<>();
        for (FeedItemResponse it : base) {
            out.add(new FeedItemResponse(it.id(), it.title(), it.description(), it.coverImage(), it.tags(),
                    it.authorAvatar(), it.authorNickname(), it.tagJson(), it.likeCount(), it.favoriteCount(),
                    uid != null && counterService.isLiked("knowpost", it.id(), uid),
                    uid != null && counterService.isFaved("knowpost", it.id(), uid), it.isTop()));
        }
        return out;
    }

    private List<FeedItemResponse> mapRows(List<KnowPostFeedRow> rows, Long uid, boolean isTop) {
        List<FeedItemResponse> items = new ArrayList<>();
        for (KnowPostFeedRow r : rows) {
            List<String> tags = parseArr(r.getTags()), imgs = parseArr(r.getImgUrls());
            Map<String, Long> cnt = counterService.getCounts("knowpost", String.valueOf(r.getId()), List.of("like", "fav"));
            items.add(new FeedItemResponse(String.valueOf(r.getId()), r.getTitle(), r.getDescription(),
                    imgs.isEmpty() ? null : imgs.getFirst(), tags, r.getAuthorAvatar(), r.getAuthorNickname(),
                    r.getAuthorTagJson(), cnt.getOrDefault("like", 0L), cnt.getOrDefault("fav", 0L),
                    uid != null && counterService.isLiked("knowpost", String.valueOf(r.getId()), uid),
                    uid != null && counterService.isFaved("knowpost", String.valueOf(r.getId()), uid),
                    isTop ? r.getIsTop() : null));
        }
        return items;
    }

    private List<String> parseArr(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); } catch (Exception e) { return Collections.emptyList(); }
    }
}
