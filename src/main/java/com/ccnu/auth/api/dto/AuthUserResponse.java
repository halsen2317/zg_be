package com.ccnu.auth.api.dto;

import java.time.LocalDate;

/**
 * 用户信息响应。
 */
public record AuthUserResponse(
        Long id,
        String nickname,
        String avatar,
        String phone,
        String zhId,
        LocalDate birthday,
        String school,
        String bio,
        String gender,
        String tagJson
) {}