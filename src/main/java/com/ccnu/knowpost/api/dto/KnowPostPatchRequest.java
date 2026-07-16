package com.ccnu.knowpost.api.dto;

import java.util.List;

/** 知文元数据更新请求（所有字段可选）。 */
public record KnowPostPatchRequest(
        String title,
        Long tagId,
        List<String> tags,
        List<String> imgUrls,
        String visible,
        Boolean isTop,
        String description
) {}