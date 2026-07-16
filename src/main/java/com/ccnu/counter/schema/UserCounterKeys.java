package com.ccnu.counter.schema;

/** 用户维度计数键。 */
public final class UserCounterKeys {
    private UserCounterKeys() {}

    public static String sdsKey(long userId) {
        return "ucnt:" + userId;
    }
}