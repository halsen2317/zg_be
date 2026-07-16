package com.ccnu.knowpost.service;

import com.ccnu.knowpost.api.dto.KnowPostDetailResponse;

import java.util.List;

/**
 * 知文服务接口。
 */
public interface KnowPostService {

    /** 创建草稿，返回新 ID。 */
    long createDraft(long creatorId);

    /** 确认内容上传。 */
    void confirmContent(long creatorId, long id, String objectKey, String etag, Long size, String sha256);

    /** 更新元数据。 */
    void updateMetadata(long creatorId, long id, String title, Long tagId, List<String> tags,
                        List<String> imgUrls, String visible, Boolean isTop, String description);

    /** 发布。 */
    void publish(long creatorId, long id);

    /** 置顶/取消置顶。 */
    void updateTop(long creatorId, long id, boolean isTop);

    /** 设置可见性。 */
    void updateVisibility(long creatorId, long id, String visible);

    /** 软删除。 */
    void delete(long creatorId, long id);

    /** 获取详情（含作者信息）。 */
    KnowPostDetailResponse getDetail(long id, Long currentUserIdNullable);
}