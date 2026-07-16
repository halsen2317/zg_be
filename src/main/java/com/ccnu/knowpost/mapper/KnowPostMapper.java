package com.ccnu.knowpost.mapper;

import com.ccnu.knowpost.model.KnowPost;
import com.ccnu.knowpost.model.KnowPostDetailRow;
import com.ccnu.knowpost.model.KnowPostFeedRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知文数据访问层。
 */
@Mapper
public interface KnowPostMapper {

    int insertDraft(KnowPost post);

    int updateContent(KnowPost post);

    int updateMetadata(KnowPost post);

    int publish(@Param("id") long id, @Param("creatorId") long creatorId);

    int updateTop(@Param("id") long id, @Param("creatorId") long creatorId, @Param("isTop") boolean isTop);

    int updateVisibility(@Param("id") long id, @Param("creatorId") long creatorId, @Param("visible") String visible);

    int softDelete(@Param("id") long id, @Param("creatorId") long creatorId);

    KnowPost findById(@Param("id") long id);

    KnowPostDetailRow findDetailById(@Param("id") long id);

    /** 公开 Feed 列表（published + public），按发布时间倒序。 */
    List<KnowPostFeedRow> listFeedPublic(@Param("limit") int limit, @Param("offset") int offset);

    /** 我的发布列表。 */
    List<KnowPostFeedRow> listMyPublished(@Param("userId") long userId, @Param("limit") int limit, @Param("offset") int offset);
}