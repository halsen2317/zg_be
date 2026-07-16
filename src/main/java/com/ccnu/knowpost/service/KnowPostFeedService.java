package com.ccnu.knowpost.service;

import com.ccnu.knowpost.api.dto.FeedPageResponse;

/**
 * Feed 流服务接口。
 */
public interface KnowPostFeedService {

    /** 公开 Feed（published + public），按发布时间倒序。 */
    FeedPageResponse getPublicFeed(int page, int size, Long currentUserIdNullable);

    /** 我的发布列表。 */
    FeedPageResponse getMyPublished(long userId, int page, int size);
}
