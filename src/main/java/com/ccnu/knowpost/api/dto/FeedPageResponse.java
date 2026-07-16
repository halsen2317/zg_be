package com.ccnu.knowpost.api.dto;

import java.util.List;

/**
 * Feed 分页响应。
 */
public record FeedPageResponse(
        List<FeedItemResponse> items,
        int page,
        int size,
        boolean hasMore
) {}