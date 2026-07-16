package com.ccnu.counter.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class CountsResponse {
    private String entityType;
    private String entityId;
    private Map<String, Long> counts;
}
