package com.ccnu.relation.service.impl;

import com.ccnu.counter.service.UserCounterService;
import com.ccnu.profile.api.dto.ProfileResponse;
import com.ccnu.relation.mapper.RelationMapper;
import com.ccnu.relation.service.RelationService;
import com.ccnu.user.domain.User;
import com.ccnu.user.mapper.UserMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;

/**
 * 关系服务实现（简化版：同步双写 + Redis ZSet 缓存）。
 */
@Service
public class RelationServiceImpl implements RelationService {

    private final RelationMapper mapper;
    private final StringRedisTemplate redis;
    private final UserMapper userMapper;
    private final UserCounterService userCounterService;

    public RelationServiceImpl(RelationMapper mapper, StringRedisTemplate redis,
                               UserMapper userMapper, UserCounterService userCounterService) {
        this.mapper = mapper; this.redis = redis;
        this.userMapper = userMapper; this.userCounterService = userCounterService;
    }

    @Override @Transactional
    public boolean follow(long fromUserId, long toUserId) {
        long id = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        long now = System.currentTimeMillis();
        int inserted = mapper.insertFollowing(id, fromUserId, toUserId, 1);
        if (inserted > 0) {
            long fid = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
            mapper.insertFollower(fid, toUserId, fromUserId, 1);
            redis.opsForZSet().add("uf:flws:" + fromUserId, String.valueOf(toUserId), now);
            redis.opsForZSet().add("uf:fans:" + toUserId, String.valueOf(fromUserId), now);
            redis.expire("uf:flws:" + fromUserId, Duration.ofHours(2));
            redis.expire("uf:fans:" + toUserId, Duration.ofHours(2));
            try { userCounterService.incrementFollowings(fromUserId, 1); } catch (Exception ignored) {}
            try { userCounterService.incrementFollowers(toUserId, 1); } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    @Override @Transactional
    public boolean unfollow(long fromUserId, long toUserId) {
        int updated = mapper.cancelFollowing(fromUserId, toUserId);
        if (updated > 0) {
            mapper.cancelFollower(toUserId, fromUserId);
            redis.opsForZSet().remove("uf:flws:" + fromUserId, String.valueOf(toUserId));
            redis.opsForZSet().remove("uf:fans:" + toUserId, String.valueOf(fromUserId));
            redis.expire("uf:flws:" + fromUserId, Duration.ofHours(2));
            redis.expire("uf:fans:" + toUserId, Duration.ofHours(2));
            try { userCounterService.incrementFollowings(fromUserId, -1); } catch (Exception ignored) {}
            try { userCounterService.incrementFollowers(toUserId, -1); } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    @Override public boolean isFollowing(long fromUserId, long toUserId) { return mapper.existsFollowing(fromUserId, toUserId) > 0; }

    @Override public Map<String, Boolean> relationStatus(long userId, long otherUserId) {
        boolean f = isFollowing(userId, otherUserId), b = isFollowing(otherUserId, userId);
        Map<String, Boolean> m = new LinkedHashMap<>();
        m.put("following", f); m.put("followedBy", b); m.put("mutual", f && b);
        return m;
    }

    @Override public List<Long> following(long uid, int limit, int offset)    { return getList("uf:flws:" + uid, offset, limit, n -> mapper.listFollowingRows(uid, n, 0), "toUserId", "createdAt"); }
    @Override public List<Long> followers(long uid, int limit, int offset)    { return getList("uf:fans:" + uid, offset, limit, n -> mapper.listFollowerRows(uid, n, 0), "fromUserId", "createdAt"); }
    @Override public List<Long> followingCursor(long uid, int limit, Long c)  { return getListCursor("uf:flws:" + uid, limit, c, n -> mapper.listFollowingRows(uid, n, 0), "toUserId", "createdAt"); }
    @Override public List<Long> followersCursor(long uid, int limit, Long c)  { return getListCursor("uf:fans:" + uid, limit, c, n -> mapper.listFollowerRows(uid, n, 0), "fromUserId", "createdAt"); }

    @Override public List<ProfileResponse> followingProfiles(long uid, int limit, int offset, Long cursor) {
        return toProfiles(cursor != null ? followingCursor(uid, limit, cursor) : following(uid, limit, offset));
    }
    @Override public List<ProfileResponse> followersProfiles(long uid, int limit, int offset, Long cursor) {
        return toProfiles(cursor != null ? followersCursor(uid, limit, cursor) : followers(uid, limit, offset));
    }

    private List<ProfileResponse> toProfiles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<User> users = userMapper.listByIds(ids);
        Map<Long, User> m = new LinkedHashMap<>();
        for (User u : users) m.put(u.getId(), u);
        List<ProfileResponse> out = new ArrayList<>();
        for (Long id : ids) {
            User u = m.get(id);
            if (u == null) continue;
            out.add(new ProfileResponse(u.getId(), u.getNickname(), u.getAvatar(), u.getBio(), u.getZgId(),
                    u.getGender(), u.getBirthday(), u.getSchool(), u.getPhone(), u.getEmail(), u.getTagsJson()));
        }
        return out;
    }

    private List<Long> getList(String key, int offset, int limit,
                                IntFunction<Map<Long, Map<String, Object>>> fetcher, String idField, String tsField) {
        Set<String> cached = redis.opsForZSet().reverseRange(key, offset, offset + limit - 1L);
        if (cached != null && !cached.isEmpty()) return toLongs(cached);
        int need = Math.max(1, limit + offset);
        Map<Long, Map<String, Object>> rows = fetcher.apply(Math.min(need, 1000));
        if (rows != null && !rows.isEmpty()) {
            fillZSet(key, rows, idField, tsField, null);
            redis.expire(key, Duration.ofHours(2));
            Set<String> filled = redis.opsForZSet().reverseRange(key, offset, offset + limit - 1L);
            return filled == null ? Collections.emptyList() : toLongs(filled);
        }
        return Collections.emptyList();
    }

    private List<Long> getListCursor(String key, int limit, Long cursor,
                                      IntFunction<Map<Long, Map<String, Object>>> fetcher, String idField, String tsField) {
        double max = cursor == null ? Double.POSITIVE_INFINITY : cursor.doubleValue();
        Set<String> cached = redis.opsForZSet().reverseRangeByScore(key, Double.NEGATIVE_INFINITY, max, 0, limit);
        if (cached != null && !cached.isEmpty()) return toLongs(cached);
        Map<Long, Map<String, Object>> rows = fetcher.apply(Math.min(Math.max(limit, 100), 1000));
        if (rows != null && !rows.isEmpty()) {
            fillZSet(key, rows, idField, tsField, cursor);
            redis.expire(key, Duration.ofHours(2));
            Set<String> filled = redis.opsForZSet().reverseRangeByScore(key, Double.NEGATIVE_INFINITY, max, 0, limit);
            return filled == null ? Collections.emptyList() : toLongs(filled);
        }
        return Collections.emptyList();
    }

    private void fillZSet(String key, Map<Long, Map<String, Object>> rows, String idField, String tsField, Long cursor) {
        for (Map<String, Object> r : rows.values()) {
            Object idObj = r.get(idField), tsObj = r.get(tsField);
            if (idObj == null || tsObj == null) continue;
            long score = tsObj instanceof Timestamp ts ? ts.getTime() : tsObj instanceof Date d ? d.getTime() : System.currentTimeMillis();
            if (cursor == null || score <= cursor) redis.opsForZSet().add(key, String.valueOf(idObj), score);
        }
    }

    private List<Long> toLongs(Set<String> set) { List<Long> out = new ArrayList<>(set.size()); for (String s : set) out.add(Long.valueOf(s)); return out; }
}
