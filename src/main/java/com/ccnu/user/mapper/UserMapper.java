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

    Optional<User> findById(@Param("id") Long id);

    Optional<User> findByPhone(@Param("phone") String phone);

    Optional<User> findByEmail(@Param("email") String email);

    boolean existsByPhone(@Param("phone") String phone);

    boolean existsByEmail(@Param("email") String email);

    /** 批量根据 ID 列表查询，保持输入顺序。 */
    List<User> listByIds(@Param("ids") List<Long> ids);
}