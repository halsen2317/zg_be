package com.ccnu.profile.api.dto;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 资料部分更新请求（PATCH），仅需提交欲更新字段。
 */
public record ProfilePatchRequest(
        @Size(min = 1, max = 64, message = "昵称长度需在 1-64 之间") String nickname,
        @Size(max = 512, message = "个人描述长度不能超过 512") String bio,
        @Pattern(regexp = "(?i)MALE|FEMALE|OTHER|UNKNOWN", message = "性别取值为 MALE/FEMALE/OTHER/UNKNOWN") String gender,
        @PastOrPresent(message = "生日不能晚于今天") LocalDate birthday,
        @Pattern(regexp = "^[a-zA-Z0-9_]{4,32}$", message = "知光号仅支持字母、数字、下划线，长度 4-32") String zgId,
        @Size(max = 128, message = "学校名称长度不能超过 128") String school,
        String tagJson
) {}
