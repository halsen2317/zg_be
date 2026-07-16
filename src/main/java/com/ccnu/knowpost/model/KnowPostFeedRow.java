package com.ccnu.knowpost.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Feed 流列表行（精简字段，JOIN users）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowPostFeedRow {
    private Long id;
    private String title;
    private String description;
    private String tags;
    private String imgUrls;
    private String authorAvatar;
    private String authorNickname;
    private String authorTagJson;
    private Boolean isTop;
    private String status;
    private String visible;
    private Long creatorId;
}