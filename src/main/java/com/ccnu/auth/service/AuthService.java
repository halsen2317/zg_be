package com.ccnu.auth.service;

import com.ccnu.auth.api.dto.*;
import com.ccnu.auth.config.AuthProperties;
import com.ccnu.auth.model.IdentifierType;
import com.ccnu.auth.token.JwtService;
import com.ccnu.auth.token.TokenPair;
import com.ccnu.common.exception.BusinessException;
import com.ccnu.common.exception.ErrorCode;
import com.ccnu.user.domain.User;
import com.ccnu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 认证服务（简化版）。
 *
 * <p>仅支持密码登录/注册，无验证码、无刷新令牌白名单、无登录审计。
 * JWT 使用 HS256 对称签名，单令牌有效期 7 天。</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w.+-]+$");

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties authProperties;

    // ──────────── 发送验证码（兼容前端，直接返回成功） ────────────

    public SendCodeResponse sendCode(SendCodeRequest request) {
        String normalized = normalizeIdentifier(request.identifierType(), request.identifier());
        return new SendCodeResponse(normalized, request.scene(), 300);
    }

    // ──────────── 注册 ────────────

    public AuthResponse register(RegisterRequest request) {
        if (!request.agreeTerms()) {
            throw new BusinessException(ErrorCode.TERMS_NOT_ACCEPTED);
        }
        validateIdentifier(request.identifierType(), request.identifier());
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());

        if (identifierExists(request.identifierType(), identifier)) {
            throw new BusinessException(ErrorCode.IDENTIFIER_EXISTS);
        }
        if (!StringUtils.hasText(request.password())) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "密码不能为空");
        }
        validatePassword(request.password());

        User user = User.builder()
                .phone(request.identifierType() == IdentifierType.PHONE ? identifier : null)
                .email(request.identifierType() == IdentifierType.EMAIL ? identifier : null)
                .nickname(generateNickname())
                .avatar("https://static.zhiguang.cn/default-avatar.png")
                .tagsJson("[]")
                .build();
        user.setPasswordHash(passwordEncoder.encode(request.password().trim()));
        userService.createUser(user);

        TokenPair pair = jwtService.issueTokenPair(user);
        return new AuthResponse(mapUser(user), mapToken(pair));
    }

    // ──────────── 登录 ────────────

    public AuthResponse login(LoginRequest request) {
        validateIdentifier(request.identifierType(), request.identifier());
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());

        User user = findUserByIdentifier(request.identifierType(), identifier)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));

        if (!StringUtils.hasText(request.password())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供密码");
        }
        if (!StringUtils.hasText(user.getPasswordHash())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        TokenPair pair = jwtService.issueTokenPair(user);
        return new AuthResponse(mapUser(user), mapToken(pair));
    }

    // ──────────── 刷新令牌 ────────────

    public TokenResponse refresh(TokenRefreshRequest request) {
        try {
            var jwt = jwtService.decode(request.refreshToken());
            long userId = jwtService.extractUserId(jwt);
            User user = findUserById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
            return mapToken(jwtService.issueTokenPair(user));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    // ──────────── 登出 ────────────

    public void logout(String refreshToken) {
        // 无状态 JWT，无需服务端清理
    }

    // ──────────── 重置密码 ────────────

    public void resetPassword(PasswordResetRequest request) {
        validateIdentifier(request.identifierType(), request.identifier());
        validatePassword(request.newPassword());
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());
        User user = findUserByIdentifier(request.identifierType(), identifier)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword().trim()));
        userService.updatePassword(user);
    }

    // ──────────── 当前用户 ────────────

    public AuthUserResponse me(long userId) {
        User user = findUserById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        return mapUser(user);
    }

    // ──────────── 内部工具方法 ────────────

    private void validateIdentifier(IdentifierType type, String identifier) {
        if (type == IdentifierType.PHONE && !PHONE_PATTERN.matcher(identifier).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式错误");
        }
        if (type == IdentifierType.EMAIL && !EMAIL_PATTERN.matcher(identifier).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱格式错误");
        }
    }

    private void validatePassword(String password) {
        String trimmed = password.trim();
        if (trimmed.length() < authProperties.getPassword().getMinLength()) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION,
                    "密码长度至少" + authProperties.getPassword().getMinLength() + "位");
        }
        boolean hasLetter = trimmed.chars().anyMatch(Character::isLetter);
        boolean hasDigit = trimmed.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "密码需包含字母和数字");
        }
    }

    private boolean identifierExists(IdentifierType type, String id) {
        return switch (type) {
            case PHONE -> userService.existsByPhone(id);
            case EMAIL -> userService.existsByEmail(id);
        };
    }

    private Optional<User> findUserByIdentifier(IdentifierType type, String id) {
        return switch (type) {
            case PHONE -> userService.findByPhone(id);
            case EMAIL -> userService.findByEmail(id);
        };
    }

    private Optional<User> findUserById(long userId) {
        return userService.findById(userId);
    }

    private String normalizeIdentifier(IdentifierType type, String id) {
        return switch (type) {
            case PHONE -> id.trim();
            case EMAIL -> id.trim().toLowerCase(Locale.ROOT);
        };
    }

    private AuthUserResponse mapUser(User u) {
        return new AuthUserResponse(u.getId(), u.getNickname(), u.getAvatar(),
                u.getPhone(), u.getZgId(), u.getBirthday(), u.getSchool(),
                u.getBio(), u.getGender(), u.getTagsJson());
    }

    private TokenResponse mapToken(TokenPair p) {
        return new TokenResponse(p.accessToken(), p.accessTokenExpiresAt(),
                p.refreshToken(), p.refreshTokenExpiresAt());
    }

    private String generateNickname() {
        return "知光用户" + UUID.randomUUID().toString().substring(0, 8);
    }
}