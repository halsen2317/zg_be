package com.ccnu.user.mapper;

import com.ccnu.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 用户数据访问层。
 */
@Mapper
public interface UserMapper {

    int insert(User user);

    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    User findById(@Param("id") Long id);

    Optional<User> findByPhone(@Param("phone") String phone);

    Optional<User> findByEmail(@Param("email") String email);

    boolean existsByPhone(@Param("phone") String phone);

    boolean existsByEmail(@Param("email") String email);

    /** 批量根据 ID 列表查询，保持输入顺序。 */
    List<User> listByIds(@Param("ids") List<Long> ids);

    /** 部分更新用户资料（只更新非 null 字段）。 */
    int updateProfile(User user);

    /** 检查 zgId 是否被其他用户占用。 */
    boolean existsByZgIdExceptId(@Param("zgId") String zgId, @Param("excludeId") Long excludeId);
}