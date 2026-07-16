package com.ccnu.search.api.dto;

import com.ccnu.knowpost.api.dto.FeedItemResponse;

import java.util.List;

public record SearchResponse(List<FeedItemResponse> items, String nextAfter, boolean hasMore) {}