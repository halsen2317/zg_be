package com.ccnu.knowpost.api.dto;

import jakarta.validation.constraints.NotNull;

/** 置顶状态更新请求。 */
public record KnowPostTopPatchRequest(@NotNull boolean isTop) {}