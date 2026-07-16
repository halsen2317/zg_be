package com.ccnu.counter.service;

/**
 * 用户维度计数服务接口（stub，Commit 8 实现）。
 */
public interface UserCounterService {
    void incrementPosts(long userId, int delta);
    void incrementFollowings(long userId, int delta);
    void incrementFollowers(long userId, int delta);
}