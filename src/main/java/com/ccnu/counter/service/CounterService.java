package com.ccnu.counter.service;

import java.util.List;
import java.util.Map;

/**
 * 计数服务接口（stub，Commit 8 实现）。
 */
public interface CounterService {
    Map<String, Long> getCounts(String entityType, String entityId, List<String> metrics);
    boolean isLiked(String entityType, String entityId, Long userId);
    boolean isFaved(String entityType, String entityId, Long userId);
}