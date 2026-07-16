package com.ccnu.knowpost.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 可见性更新请求。 */
public record KnowPostVisibilityPatchRequest(@NotBlank String visible) {}