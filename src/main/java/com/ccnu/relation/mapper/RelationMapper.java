package com.ccnu.relation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 关系数据访问层（stub，Commit 9 完整实现）。
 */
@Mapper
public interface RelationMapper {
    int countFollowingActive(@Param("fromUserId") Long fromUserId);
    int countFollowerActive(@Param("toUserId") Long toUserId);
}
