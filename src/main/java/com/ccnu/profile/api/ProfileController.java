package com.ccnu.profile.api;

import com.ccnu.auth.token.JwtService;
import com.ccnu.profile.api.dto.ProfilePatchRequest;
import com.ccnu.profile.api.dto.ProfileResponse;
import com.ccnu.profile.service.ProfileService;
import com.ccnu.storage.service.OssStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人资料接口。
 */
@RestController
@RequestMapping("/api/v1/profile")
@Validated
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final JwtService jwtService;
    private final OssStorageService ossStorageService;

    /** 更新资料（部分字段）。 */
    @PatchMapping
    public ProfileResponse patch(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfilePatchRequest req) {
        return profileService.updateProfile(jwtService.extractUserId(jwt), req);
    }

    /** 上传头像。 */
    @PostMapping("/avatar")
    public ProfileResponse uploadAvatar(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file) {
        long userId = jwtService.extractUserId(jwt);
        String url = ossStorageService.uploadAvatar(userId, file);
        return profileService.updateAvatar(userId, url);
    }
}
