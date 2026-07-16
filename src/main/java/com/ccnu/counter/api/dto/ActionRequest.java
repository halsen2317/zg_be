package com.ccnu.counter.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActionRequest {
    @NotBlank private String entityType;
    @NotBlank private String entityId;
}
