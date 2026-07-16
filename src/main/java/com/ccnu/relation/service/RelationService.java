package com.ccnu.relation.service;

import com.ccnu.profile.api.dto.ProfileResponse;

import java.util.List;
import java.util.Map;

public interface RelationService {
    boolean follow(long fromUserId, long toUserId);
    boolean unfollow(long fromUserId, long toUserId);
    boolean isFollowing(long fromUserId, long toUserId);
    List<Long> following(long userId, int limit, int offset);
    List<Long> followers(long userId, int limit, int offset);
    Map<String, Boolean> relationStatus(long userId, long otherUserId);
    List<Long> followingCursor(long userId, int limit, Long cursor);
    List<Long> followersCursor(long userId, int limit, Long cursor);
    List<ProfileResponse> followingProfiles(long userId, int limit, int offset, Long cursor);
    List<ProfileResponse> followersProfiles(long userId, int limit, int offset, Long cursor);
}
