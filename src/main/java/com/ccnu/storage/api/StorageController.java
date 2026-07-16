package com.ccnu.storage.api;

import com.ccnu.auth.token.JwtService;
import com.ccnu.common.exception.BusinessException;
import com.ccnu.common.exception.ErrorCode;
import com.ccnu.knowpost.mapper.KnowPostMapper;
import com.ccnu.knowpost.model.KnowPost;
import com.ccnu.storage.api.dto.StoragePresignRequest;
import com.ccnu.storage.api.dto.StoragePresignResponse;
import com.ccnu.storage.service.OssStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 存储 API 控制器。
 *
 * <p>提供 OSS 预签名 URL，前端直传文件到阿里云 OSS。</p>
 */
@RestController
@RequestMapping("/api/v1/storage")
@Validated
@RequiredArgsConstructor
public class StorageController {

    private final OssStorageService ossStorageService;
    private final JwtService jwtService;
    private final KnowPostMapper knowPostMapper;

    /**
     * 获取 PUT 预签名直传 URL。
     */
    @PostMapping("/presign")
    public StoragePresignResponse presign(@Valid @RequestBody StoragePresignRequest req,
                                          @AuthenticationPrincipal Jwt jwt) {
        long userId = jwtService.extractUserId(jwt);

        long postId;
        try {
            postId = Long.parseLong(req.postId());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "postId 非法");
        }

        // 权限：postId 必须属于当前用户
        KnowPost post = knowPostMapper.findById(postId);
        if (post == null || post.getCreatorId() == null || post.getCreatorId() != userId) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }

        String scene = req.scene();
        String ext = normalizeExt(req.ext(), req.contentType(), scene);
        String objectKey;

        if ("knowpost_content".equals(scene)) {
            objectKey = "posts/" + postId + "/content" + ext;
        } else if ("knowpost_image".equals(scene)) {
            String date = DateTimeFormatter.ofPattern("yyyyMMdd")
                    .withZone(ZoneId.of("UTC")).format(Instant.now());
            String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            objectKey = "posts/" + postId + "/images/" + date + "/" + rand + ext;
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的上传场景");
        }

        int expiresIn = 600;
        String putUrl = ossStorageService.generatePresignedPutUrl(objectKey, req.contentType(), expiresIn);
        Map<String, String> headers = Map.of("Content-Type", req.contentType());
        return new StoragePresignResponse(objectKey, putUrl, headers, expiresIn);
    }

    private String normalizeExt(String ext, String contentType, String scene) {
        if (ext != null && !ext.isBlank()) {
            return ext.startsWith(".") ? ext : "." + ext;
        }
        if ("knowpost_content".equals(scene)) {
            return switch (contentType) {
                case "text/markdown" -> ".md";
                case "text/html" -> ".html";
                case "text/plain" -> ".txt";
                case "application/json" -> ".json";
                default -> ".bin";
            };
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".img";
        };
    }
}