package com.ccnu.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.ccnu.counter.service.CounterService;
import com.ccnu.knowpost.mapper.KnowPostMapper;
import com.ccnu.knowpost.model.KnowPostDetailRow;
import com.ccnu.knowpost.model.KnowPostFeedRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 搜索索引服务：ES 文档的 upsert / 软删 / 启动回灌。
 */
@Service
public class SearchIndexService {
    private static final Logger log = LoggerFactory.getLogger(SearchIndexService.class);
    private static final String INDEX = "zhiguang_content_index";

    private final ElasticsearchClient es;
    private final KnowPostMapper knowPostMapper;
    private final CounterService counterService;
    private final ObjectMapper objectMapper;
    private final RestTemplate http = new RestTemplate();

    public SearchIndexService(ElasticsearchClient es, KnowPostMapper knowPostMapper,
                              CounterService counterService, ObjectMapper objectMapper) {
        this.es = es; this.knowPostMapper = knowPostMapper;
        this.counterService = counterService; this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void ensureBackfill() {
        try {
            if (es.count(c -> c.index(INDEX)).count() > 0) return;
            int limit = 500, offset = 0;
            while (true) {
                List<KnowPostFeedRow> rows = knowPostMapper.listFeedPublic(limit, offset);
                if (rows == null || rows.isEmpty()) break;
                for (KnowPostFeedRow r : rows) upsertKnowPost(r.getId());
                offset += rows.size();
            }
            log.info("Search index backfill completed");
        } catch (Exception e) { log.warn("Search index backfill skipped: {}", e.getMessage()); }
    }

    public void upsertKnowPost(long id) {
        try {
            KnowPostDetailRow row = knowPostMapper.findDetailById(id);
            if (row == null) return;
            Map<String, Object> doc = new HashMap<>();
            doc.put("content_id", row.getId()); doc.put("content_type", row.getType());
            doc.put("title", row.getTitle()); doc.put("description", row.getDescription());
            doc.put("author_id", row.getCreatorId()); doc.put("author_avatar", row.getAuthorAvatar());
            doc.put("author_nickname", row.getAuthorNickname()); doc.put("author_tag_json", row.getAuthorTagJson());
            if (row.getPublishTime() != null) doc.put("publish_time", row.getPublishTime().toEpochMilli());
            doc.put("status", row.getStatus()); doc.put("tags", parseArr(row.getTags()));
            doc.put("img_urls", parseArr(row.getImgUrls()));
            if (row.getIsTop() != null) doc.put("is_top", row.getIsTop());
            doc.put("body", truncate(fetchContent(row.getContentUrl(), row.getDescription()), 4000));
            Map<String, Long> cnt = counterService.getCounts("knowpost", String.valueOf(id), List.of("like", "fav"));
            doc.put("like_count", cnt.getOrDefault("like", 0L));
            doc.put("favorite_count", cnt.getOrDefault("fav", 0L));
            doc.put("view_count", 0L);
            if (row.getTitle() != null && !row.getTitle().isBlank()) doc.put("title_suggest", row.getTitle());
            es.index(IndexRequest.of(b -> b.index(INDEX).id(String.valueOf(id)).document(doc).refresh(Refresh.WaitFor)));
            log.info("Indexed post {}", id);
        } catch (Exception e) { log.error("Index upsert failed for {}: {}", id, e.getMessage()); }
    }

    public void softDeleteKnowPost(long id) {
        try {
            Map<String, Object> doc = Map.of("content_id", id, "status", "deleted");
            es.index(IndexRequest.of(b -> b.index(INDEX).id(String.valueOf(id)).document(doc).refresh(Refresh.WaitFor)));
        } catch (Exception e) { log.error("Index soft delete failed for {}: {}", id, e.getMessage()); }
    }

    private String fetchContent(String url, String fallback) {
        if (url == null || url.isBlank()) return fallback;
        try { return new RestTemplate().getForObject(url, String.class); } catch (Exception e) { return fallback; }
    }

    private String truncate(String s, int max) { return s == null ? null : s.length() <= max ? s : s.substring(0, max); }

    private List<String> parseArr(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); } catch (Exception e) { return Collections.emptyList(); }
    }
}
