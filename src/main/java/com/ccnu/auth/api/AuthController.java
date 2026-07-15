package com.ccnu.auth.api;

import com.ccnu.auth.api.dto.*;
import com.ccnu.auth.service.AuthService;
import com.ccnu.auth.token.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 认证 API 控制器。
 *
 * <p>提供注册、登录、令牌刷新、登出、密码重置、当前用户查询。
 * 所有路径与前端约定一致，无需修改前端代码。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    /** 发送验证码（兼容前端，实际不发送）。 */
    @PostMapping("/send-code")
    public SendCodeResponse sendCode(@Valid @RequestBody SendCodeRequest request) {
        return authService.sendCode(request);
    }

    /** 注册。 */
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** 登录。 */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** 刷新令牌。 */
    @PostMapping("/token/refresh")
    public TokenResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return authService.refresh(request);
    }

    /** 登出。 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** 重置密码。 */
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    /** 当前用户信息。 */
    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        long userId = jwtService.extractUserId(jwt);
        return authService.me(userId);
    }
}