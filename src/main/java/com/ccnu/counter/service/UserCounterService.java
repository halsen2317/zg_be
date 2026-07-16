package com.ccnu.counter.service;

/** 用户维度计数服务接口。 */
public interface UserCounterService {
    void incrementFollowings(long userId, int delta);
    void incrementFollowers(long userId, int delta);
    void incrementPosts(long userId, int delta);
    void incrementLikesReceived(long userId, int delta);
    void incrementFavsReceived(long userId, int delta);
    void rebuildAllCounters(long userId);
}
