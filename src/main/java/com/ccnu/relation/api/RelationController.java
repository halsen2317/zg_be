package com.ccnu.relation.api;

import com.ccnu.auth.token.JwtService;
import com.ccnu.counter.service.UserCounterService;
import com.ccnu.profile.api.dto.ProfileResponse;
import com.ccnu.relation.mapper.RelationMapper;
import com.ccnu.relation.service.RelationService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

@RestController
@RequestMapping("/api/v1/relation")
public class RelationController {

    private final RelationService relationService;
    private final JwtService jwtService;
    private final StringRedisTemplate redis;
    private final UserCounterService userCounterService;
    private final RelationMapper relationMapper;

    public RelationController(RelationService relationService, JwtService jwtService,
                              StringRedisTemplate redis, UserCounterService userCounterService,
                              RelationMapper relationMapper) {
        this.relationService = relationService; this.jwtService = jwtService;
        this.redis = redis; this.userCounterService = userCounterService;
        this.relationMapper = relationMapper;
    }

    @PostMapping("/follow")
    public boolean follow(@RequestParam long toUserId, @AuthenticationPrincipal Jwt jwt) {
        return relationService.follow(jwtService.extractUserId(jwt), toUserId);
    }

    @PostMapping("/unfollow")
    public boolean unfollow(@RequestParam long toUserId, @AuthenticationPrincipal Jwt jwt) {
        return relationService.unfollow(jwtService.extractUserId(jwt), toUserId);
    }

    @GetMapping("/status")
    public Map<String, Boolean> status(@RequestParam long toUserId, @AuthenticationPrincipal Jwt jwt) {
        return relationService.relationStatus(jwtService.extractUserId(jwt), toUserId);
    }

    @GetMapping("/following")
    public List<ProfileResponse> following(@RequestParam long userId,
                                           @RequestParam(defaultValue = "20") int limit,
                                           @RequestParam(defaultValue = "0") int offset,
                                           @RequestParam(required = false) Long cursor) {
        return relationService.followingProfiles(userId, clamp(limit), Math.max(offset, 0), cursor);
    }

    @GetMapping("/followers")
    public List<ProfileResponse> followers(@RequestParam long userId,
                                           @RequestParam(defaultValue = "20") int limit,
                                           @RequestParam(defaultValue = "0") int offset,
                                           @RequestParam(required = false) Long cursor) {
        return relationService.followersProfiles(userId, clamp(limit), Math.max(offset, 0), cursor);
    }

    @GetMapping("/counter")
    public Map<String, Long> counter(@RequestParam long userId) {
        Map<String, Long> m = new LinkedHashMap<>();
        try {
            m.put("followings", (long) relationMapper.countFollowingActive(userId));
            m.put("followers", (long) relationMapper.countFollowerActive(userId));
        } catch (Exception e) {
            m.put("followings", 0L); m.put("followers", 0L);
        }
        try {
            byte[] raw = redis.execute((org.springframework.data.redis.core.RedisCallback<byte[]>)
                    c -> c.stringCommands().get(("ucnt:" + userId).getBytes(StandardCharsets.UTF_8)));
            if (raw != null && raw.length >= 20) {
                final byte[] buf = raw;
                IntFunction<Long> read = idx -> { int off = (idx - 1) * 4; long n = 0; for (int i = 0; i < 4; i++) n = (n << 8) | (buf[off + i] & 0xFFL); return n; };
                m.put("posts", read.apply(3)); m.put("likedPosts", read.apply(4)); m.put("favedPosts", read.apply(5));
            } else { m.put("posts", 0L); m.put("likedPosts", 0L); m.put("favedPosts", 0L); }
        } catch (Exception e) { m.put("posts", 0L); m.put("likedPosts", 0L); m.put("favedPosts", 0L); }
        return m;
    }

    private int clamp(int v) { return Math.min(Math.max(v, 1), 100); }
}
