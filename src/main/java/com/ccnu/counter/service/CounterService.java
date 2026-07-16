package com.ccnu.counter.service;

import java.util.List;
import java.util.Map;

/** 内容实体计数服务接口。 */
public interface CounterService {
    boolean like(String entityType, String entityId, long userId);
    boolean unlike(String entityType, String entityId, long userId);
    boolean fav(String entityType, String entityId, long userId);
    boolean unfav(String entityType, String entityId, long userId);
    Map<String, Long> getCounts(String entityType, String entityId, List<String> metrics);
    Map<String, Map<String, Long>> getCountsBatch(String entityType, List<String> entityIds, List<String> metrics);
    boolean isLiked(String entityType, String entityId, long userId);
    boolean isFaved(String entityType, String entityId, long userId);
}
