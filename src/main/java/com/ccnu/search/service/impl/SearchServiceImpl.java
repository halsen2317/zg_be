package com.ccnu.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import co.elastic.clients.util.NamedValue;
import com.ccnu.counter.service.CounterService;
import com.ccnu.knowpost.api.dto.FeedItemResponse;
import com.ccnu.search.api.dto.SearchResponse;
import com.ccnu.search.api.dto.SuggestResponse;
import com.ccnu.search.service.SearchService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {

    private static final String INDEX = "zhiguang_content_index";
    private final ElasticsearchClient es;
    private final CounterService counterService;

    public SearchServiceImpl(ElasticsearchClient es, CounterService counterService) {
        this.es = es; this.counterService = counterService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SearchResponse search(String q, int size, String tagsCsv, String after, Long uid) {
        List<String> tags = parseCsv(tagsCsv);
        List<FieldValue> afterValues = parseAfter(after);
        List<SortOptions> sorts = List.of(
                SortOptions.of(s -> s.score(o -> o.order(SortOrder.Desc))),
                SortOptions.of(s -> s.field(f -> f.field("publish_time").order(SortOrder.Desc))),
                SortOptions.of(s -> s.field(f -> f.field("like_count").order(SortOrder.Desc))),
                SortOptions.of(s -> s.field(f -> f.field("view_count").order(SortOrder.Desc))),
                SortOptions.of(s -> s.field(f -> f.field("content_id").order(SortOrder.Desc)))
        );
        try {
            co.elastic.clients.elasticsearch.core.SearchResponse<Map<String, Object>> resp = es.search(s -> {
                var b = s.index(INDEX).size(size)
                        .query(qb -> qb.functionScore(fs -> fs
                                .query(qb2 -> qb2.bool(bq -> {
                                    bq.must(m -> m.multiMatch(mm -> mm.query(q).fields("title^3", "body")));
                                    bq.filter(f -> f.term(t -> t.field("status").value(v -> v.stringValue("published"))));
                                    if (tags != null && !tags.isEmpty())
                                        bq.filter(f -> f.terms(t -> t.field("tags").terms(tv -> tv.value(tags.stream().map(FieldValue::of).toList()))));
                                    return bq;
                                }))
                                .functions(fn -> fn.fieldValueFactor(fvf -> fvf.field("like_count").modifier(FieldValueFactorModifier.Log1p)).weight(2.0))
                                .functions(fn -> fn.fieldValueFactor(fvf -> fvf.field("view_count").modifier(FieldValueFactorModifier.Log1p)).weight(1.0))
                                .boostMode(FunctionBoostMode.Sum)))
                        .highlight(h -> h.fields(new NamedValue<>("title", new HighlightField.Builder().build()))
                                .fields(new NamedValue<>("body", new HighlightField.Builder().build())))
                        .sort(sorts);
                if (afterValues != null && !afterValues.isEmpty()) b = b.searchAfter(afterValues);
                return b;
            }, (Class<Map<String, Object>>) (Class<?>) Map.class);

            List<FeedItemResponse> items = new ArrayList<>();
            List<Hit<Map<String, Object>>> hits = resp.hits() == null ? List.of() : resp.hits().hits();
            for (Hit<Map<String, Object>> hit : hits) {
                Map<String, Object> src = hit.source();
                if (src == null) continue;
                String id = asStr(src.get("content_id"));
                String title = asStr(src.get("title"));
                String snippet = buildSnippet(hit);
                String desc = (snippet != null && !snippet.isBlank()) ? snippet : asStr(src.get("description"));
                List<String> tagList = asStrList(src.get("tags"));
                List<String> imgs = asStrList(src.get("img_urls"));
                items.add(new FeedItemResponse(id, title, desc,
                        imgs.isEmpty() ? null : imgs.getFirst(), tagList,
                        asStr(src.get("author_avatar")), asStr(src.get("author_nickname")), asStr(src.get("author_tag_json")),
                        asLong(src.get("like_count")), asLong(src.get("favorite_count")),
                        uid != null && counterService.isLiked("knowpost", id, uid),
                        uid != null && counterService.isFaved("knowpost", id, uid), null));
            }
            String nextAfter = null;
            if (!hits.isEmpty()) {
                List<FieldValue> sv = hits.getLast().sort();
                if (sv != null && !sv.isEmpty())
                    nextAfter = Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(sv.stream().map(this::fvToStr).collect(Collectors.joining(",")).getBytes());
            }
            return new SearchResponse(items, nextAfter, items.size() >= size);
        } catch (Exception e) { return new SearchResponse(Collections.emptyList(), null, false); }
    }

    @Override
    @SuppressWarnings("unchecked")
    public SuggestResponse suggest(String prefix, int size) {
        try {
            var resp = es.search(s -> s.index(INDEX)
                    .suggest(sug -> sug.suggesters("title_suggest",
                            sc -> sc.prefix(prefix).completion(c -> c.field("title_suggest").size(size)))),
                    (Class<Map<String, Object>>) (Class<?>) Map.class);
            List<String> items = new ArrayList<>();
            List<Suggestion<Map<String, Object>>> entry = resp.suggest() != null ? resp.suggest().get("title_suggest") : null;
            if (entry != null) for (var s : entry) if (s.completion() != null && s.completion().options() != null)
                for (var opt : s.completion().options()) if (opt.text() != null && !opt.text().isBlank()) items.add(opt.text());
            return new SuggestResponse(items);
        } catch (Exception e) { return new SuggestResponse(Collections.emptyList()); }
    }

    private String buildSnippet(Hit<Map<String, Object>> hit) {
        if (hit.highlight() == null) return null;
        StringBuilder sb = new StringBuilder();
        List<String> ht = hit.highlight().get("title");
        if (ht != null && !ht.isEmpty()) sb.append(String.join(" ", ht));
        List<String> hb = hit.highlight().get("body");
        if (hb != null && !hb.isEmpty()) { if (!sb.isEmpty()) sb.append(" "); sb.append(String.join(" ", hb)); }
        return sb.isEmpty() ? null : sb.toString();
    }

    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return null;
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private List<FieldValue> parseAfter(String after) {
        if (after == null || after.isBlank()) return null;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(after)).split(",");
            List<FieldValue> out = new ArrayList<>();
            for (int i = 0; i < parts.length; i++)
                out.add(i == 0 ? FieldValue.of(Double.parseDouble(parts[i])) : FieldValue.of(Long.parseLong(parts[i])));
            return out;
        } catch (Exception e) { return null; }
    }

    private String fvToStr(FieldValue fv) {
        if (fv.isDouble()) return String.valueOf(fv.doubleValue());
        if (fv.isLong()) return String.valueOf(fv.longValue());
        if (fv.isString()) return fv.stringValue();
        return String.valueOf(fv._get());
    }

    private String asStr(Object o) { return o == null ? null : String.valueOf(o); }
    private Long asLong(Object o) { if (o == null) return null; if (o instanceof Number n) return n.longValue(); try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; } }

    @SuppressWarnings("unchecked")
    private List<String> asStrList(Object o) {
        if (o == null) return Collections.emptyList();
        if (o instanceof List<?> l) { List<String> out = new ArrayList<>(); for (Object e : l) if (e != null) out.add(String.valueOf(e)); return out; }
        return Collections.emptyList();
    }
}
