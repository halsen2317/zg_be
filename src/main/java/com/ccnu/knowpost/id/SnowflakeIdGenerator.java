package com.ccnu.knowpost.id;

import org.springframework.stereotype.Component;

/**
 * 简化版雪花 ID 生成器。
 *
 * <p>41 位时间戳（毫秒）+ 10 位机器 ID + 12 位序列号。
 * 单机部署时 machineId 固定为 1。</p>
 */
@Component
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1700000000000L; // 2023-11-15 左右
    private static final long MACHINE_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private final long machineId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator() {
        this(1L);
    }

    public SnowflakeIdGenerator(long machineId) {
        if (machineId < 0 || machineId >= (1L << MACHINE_BITS)) {
            throw new IllegalArgumentException("machineId out of range");
        }
        this.machineId = machineId;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << (MACHINE_BITS + SEQUENCE_BITS))
                | (machineId << SEQUENCE_BITS)
                | sequence;
    }

    private long waitNextMillis(long last) {
        long ts = System.currentTimeMillis();
        while (ts <= last) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}