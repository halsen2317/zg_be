package com.ccnu.user.service;

import com.ccnu.user.domain.User;

import java.util.Optional;

/**
 * 用户服务接口。
 */
public interface UserService {

    /** 创建新用户。 */
    void createUser(User user);

    /** 更新密码。 */
    void updatePassword(User user);

    /** 按 ID 查询。 */
    Optional<User> findById(Long id);

    /** 按手机号查询。 */
    Optional<User> findByPhone(String phone);

    /** 按邮箱查询。 */
    Optional<User> findByEmail(String email);

    /** 手机号是否已存在。 */
    boolean existsByPhone(String phone);

    /** 邮箱是否已存在。 */
    boolean existsByEmail(String email);
}