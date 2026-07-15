package com.ccnu.user.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 用户实体。
 *
 * <p>phone / email 作为登录标识（至少其一必填），
 * passwordHash 为 BCrypt 加密结果。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String phone;
    private String email;
    private String passwordHash;
    private String nickname;
    private String avatar;
    private String bio;
    private String zgId;
    private String gender;
    private LocalDate birthday;
    private String school;
    private String tagsJson;
    private Instant createdAt;
    private Instant updatedAt;
}