package com.ccnu.counter.schema;

/** 位图分片：每片 32K 位，按用户 ID 分片避免单键膨胀。 */
public final class BitmapShard {
    public static final int CHUNK_SIZE = 32_768;

    public static long chunkOf(long userId) { return userId / CHUNK_SIZE; }
    public static long bitOf(long userId)   { return userId % CHUNK_SIZE; }

    private BitmapShard() {}
}