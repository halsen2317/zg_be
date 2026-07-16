package com.ccnu.knowpost.api;

import com.ccnu.auth.token.JwtService;
import com.ccnu.knowpost.api.dto.*;
import com.ccnu.knowpost.service.KnowPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 知文 API 控制器。
 *
 * <p>提供草稿 → 上传 → 发布 完整流程，以及详情查询。
 * Feed 端点见 {@code KnowPostFeedController}（Commit 10）。</p>
 */
@RestController
@RequestMapping("/api/v1/knowposts")
@Validated
@RequiredArgsConstructor
public class KnowPostController {

    private final KnowPostService service;
    private final JwtService jwtService;

    /** 创建草稿。 */
    @PostMapping("/drafts")
    public KnowPostDraftCreateResponse createDraft(@AuthenticationPrincipal Jwt jwt) {
        long id = service.createDraft(jwtService.extractUserId(jwt));
        return new KnowPostDraftCreateResponse(String.valueOf(id));
    }

    /** 确认内容上传。 */
    @PostMapping("/{id}/content/confirm")
    public ResponseEntity<Void> confirmContent(@PathVariable long id,
                                               @Valid @RequestBody KnowPostContentConfirmRequest req,
                                               @AuthenticationPrincipal Jwt jwt) {
        service.confirmContent(jwtService.extractUserId(jwt), id, req.objectKey(), req.etag(), req.size(), req.sha256());
        return ResponseEntity.noContent().build();
    }

    /** 更新元数据。 */
    @PatchMapping("/{id}")
    public ResponseEntity<Void> patchMetadata(@PathVariable long id,
                                              @Valid @RequestBody KnowPostPatchRequest req,
                                              @AuthenticationPrincipal Jwt jwt) {
        service.updateMetadata(jwtService.extractUserId(jwt), id,
                req.title(), req.tagId(), req.tags(), req.imgUrls(), req.visible(), req.isTop(), req.description());
        return ResponseEntity.noContent().build();
    }

    /** 发布。 */
    @PostMapping("/{id}/publish")
    public ResponseEntity<Void> publish(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        service.publish(jwtService.extractUserId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /** 置顶/取消置顶。 */
    @PatchMapping("/{id}/top")
    public ResponseEntity<Void> patchTop(@PathVariable long id,
                                         @Valid @RequestBody KnowPostTopPatchRequest req,
                                         @AuthenticationPrincipal Jwt jwt) {
        service.updateTop(jwtService.extractUserId(jwt), id, req.isTop());
        return ResponseEntity.noContent().build();
    }

    /** 设置可见性。 */
    @PatchMapping("/{id}/visibility")
    public ResponseEntity<Void> patchVisibility(@PathVariable long id,
                                                @Valid @RequestBody KnowPostVisibilityPatchRequest req,
                                                @AuthenticationPrincipal Jwt jwt) {
        service.updateVisibility(jwtService.extractUserId(jwt), id, req.visible());
        return ResponseEntity.noContent().build();
    }

    /** 软删除。 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        service.delete(jwtService.extractUserId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /** 知文详情。 */
    @GetMapping("/detail/{id}")
    public KnowPostDetailResponse detail(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        Long uid = (jwt == null) ? null : jwtService.extractUserId(jwt);
        return service.getDetail(id, uid);
    }
}