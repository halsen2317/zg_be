package com.ccnu.counter.service.impl;

import com.ccnu.counter.event.CounterEvent;
import com.ccnu.counter.event.CounterEventProducer;
import com.ccnu.counter.schema.BitmapShard;
import com.ccnu.counter.schema.CounterKeys;
import com.ccnu.counter.schema.CounterSchema;
import com.ccnu.counter.service.CounterService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 计数服务实现：Redis 分片位图（事实层）+ Kafka 异步聚合 → SDS 汇总。
 */
@Slf4j
@Service
public class CounterServiceImpl implements CounterService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> toggleScript;
    private final CounterEventProducer eventProducer;
    private final ApplicationEventPublisher eventPublisher;
    private final RedissonClient redisson;

    @Value("${counter.rebuild.lock.ttl-ms:5000}") private long lockTtlMs;
    @Value("${counter.rebuild.rate.permits:3}") private int ratePermits;
    @Value("${counter.rebuild.rate.window-seconds:10}") private int rateWindowSeconds;
    @Value("${counter.rebuild.backoff.base-ms:500}") private long backoffBaseMs;
    @Value("${counter.rebuild.backoff.max-ms:30000}") private long backoffMaxMs;

    public CounterServiceImpl(StringRedisTemplate redis, CounterEventProducer eventProducer,
                              ApplicationEventPublisher eventPublisher, RedissonClient redisson) {
        this.redis = redis; this.eventProducer = eventProducer; this.eventPublisher = eventPublisher;
        this.redisson = redisson;
        this.toggleScript = new DefaultRedisScript<>();
        this.toggleScript.setResultType(Long.class);
        this.toggleScript.setScriptText(TOGGLE_LUA);
    }

    @Override public boolean like(String et, String eid, long uid)   { return toggle(et, eid, uid, "like", CounterSchema.IDX_LIKE, true); }
    @Override public boolean unlike(String et, String eid, long uid) { return toggle(et, eid, uid, "like", CounterSchema.IDX_LIKE, false); }
    @Override public boolean fav(String et, String eid, long uid)    { return toggle(et, eid, uid, "fav", CounterSchema.IDX_FAV, true); }
    @Override public boolean unfav(String et, String eid, long uid)  { return toggle(et, eid, uid, "fav", CounterSchema.IDX_FAV, false); }

    private boolean toggle(String etype, String eid, long uid, String metric, int idx, boolean add) {
        long chunk = BitmapShard.chunkOf(uid);
        long bit = BitmapShard.bitOf(uid);
        String bmKey = CounterKeys.bitmapKey(metric, etype, eid, chunk);
        Long changed = redis.execute(toggleScript, List.of(bmKey),
                List.of(String.valueOf(bit), add ? "add" : "remove").toArray());
        boolean ok = changed == 1L;
        if (ok) {
            int delta = add ? 1 : -1;
            eventProducer.publish(CounterEvent.of(etype, eid, metric, idx, uid, delta));
            eventPublisher.publishEvent(CounterEvent.of(etype, eid, metric, idx, uid, delta));
        }
        return ok;
    }

    @Override
    public Map<String, Long> getCounts(String entityType, String entityId, List<String> metrics) {
        String sdsKey = CounterKeys.sdsKey(entityType, entityId);
        int expectedLen = CounterSchema.SCHEMA_LEN * CounterSchema.FIELD_SIZE;
        byte[] raw = getRaw(sdsKey);
        boolean needRebuild = (raw == null || raw.length != expectedLen);
        Map<String, Long> result = new LinkedHashMap<>();

        if (needRebuild) {
            if (inBackoff(entityType, entityId)) { for (String m : metrics) result.put(m, 0L); return result; }
            if (!allowedByRateLimiter(entityType, entityId)) { escalateBackoff(entityType, entityId); for (String m : metrics) result.put(m, 0L); return result; }
            String lockKey = String.format("lock:sds-rebuild:%s:%s", entityType, entityId);
            RLock lock = redisson.getLock(lockKey);
            boolean locked = false;
            try {
                locked = lock.tryLock(0L, TimeUnit.MILLISECONDS);
                if (!locked) { escalateBackoff(entityType, entityId); for (String m : metrics) result.put(m, 0L); return result; }
                byte[] newSds = new byte[expectedLen];
                for (String m : metrics) {
                    Integer i = CounterSchema.NAME_TO_IDX.get(m);
                    if (i == null) continue;
                    long sum = bitCountShardsPipelined(m, entityType, entityId);
                    writeInt32BE(newSds, i * CounterSchema.FIELD_SIZE, sum);
                    result.put(m, sum);
                }
                setRaw(sdsKey, newSds);
                resetBackoff(entityType, entityId);
            } catch (InterruptedException ie) { Thread.currentThread().interrupt(); escalateBackoff(entityType, entityId); for (String m : metrics) result.put(m, 0L); }
            finally { if (locked) { try { lock.unlock(); } catch (Exception ignored) {} } }
        } else {
            for (String m : metrics) {
                Integer i = CounterSchema.NAME_TO_IDX.get(m);
                if (i == null) continue;
                result.put(m, readInt32BE(raw, i * CounterSchema.FIELD_SIZE));
            }
        }
        return result;
    }

    @Override
    public Map<String, Map<String, Long>> getCountsBatch(String entityType, List<String> entityIds, List<String> metrics) {
        Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        if (entityIds == null || entityIds.isEmpty()) return out;
        List<String> keys = new ArrayList<>(entityIds.size());
        for (String eid : entityIds) keys.add(CounterKeys.sdsKey(entityType, eid));
        List<Object> raws = redis.executePipelined((RedisCallback<Object>) connection -> { for (String k : keys) connection.stringCommands().get(k.getBytes(StandardCharsets.UTF_8)); return null; });
        int expectedLen = CounterSchema.SCHEMA_LEN * CounterSchema.FIELD_SIZE;
        for (int i = 0; i < entityIds.size(); i++) {
            byte[] raw = (raws.get(i) instanceof byte[] b) ? b : null;
            Map<String, Long> m = new LinkedHashMap<>();
            if (raw != null && raw.length == expectedLen) for (String name : metrics) {
                Integer idx = CounterSchema.NAME_TO_IDX.get(name);
                if (idx != null) m.put(name, readInt32BE(raw, idx * CounterSchema.FIELD_SIZE));
            } else for (String name : metrics) m.put(name, 0L);
            out.put(entityIds.get(i), m);
        }
        return out;
    }

    @Override public boolean isLiked(String et, String eid, long uid) { return getBit(CounterKeys.bitmapKey("like", et, eid, BitmapShard.chunkOf(uid)), BitmapShard.bitOf(uid)); }
    @Override public boolean isFaved(String et, String eid, long uid) { return getBit(CounterKeys.bitmapKey("fav", et, eid, BitmapShard.chunkOf(uid)), BitmapShard.bitOf(uid)); }

    private boolean getBit(String key, long offset) { Boolean b = redis.execute((RedisCallback<Boolean>) c -> c.stringCommands().getBit(key.getBytes(StandardCharsets.UTF_8), offset)); return Boolean.TRUE.equals(b); }
    private byte[] getRaw(String key) { return redis.execute((RedisCallback<byte[]>) c -> c.stringCommands().get(key.getBytes(StandardCharsets.UTF_8))); }
    private void setRaw(String key, byte[] val) { redis.execute((RedisCallback<Void>) c -> { c.stringCommands().set(key.getBytes(StandardCharsets.UTF_8), val); return null; }); }

    private boolean inBackoff(String et, String eid) { RBucket<Long> b = redisson.getBucket(String.format("backoff:sds-rebuild:until:%s:%s", et, eid)); Long until = b.get(); return until != null && System.currentTimeMillis() < until; }
    private void escalateBackoff(String et, String eid) { RBucket<Integer> eb = redisson.getBucket(String.format("backoff:sds-rebuild:exp:%s:%s", et, eid)); RBucket<Long> ub = redisson.getBucket(String.format("backoff:sds-rebuild:until:%s:%s", et, eid)); Integer exp = eb.get(); int next = Math.min(exp == null ? 0 : exp + 1, 10); long delay = Math.min(backoffBaseMs * (1L << next), backoffMaxMs); ub.set(System.currentTimeMillis() + delay, Duration.ofMillis(delay + 1000)); eb.set(next); }
    private void resetBackoff(String et, String eid) { try { redisson.getBucket(String.format("backoff:sds-rebuild:exp:%s:%s", et, eid)).delete(); } catch (Exception ignored) {} try { redisson.getBucket(String.format("backoff:sds-rebuild:until:%s:%s", et, eid)).delete(); } catch (Exception ignored) {} }
    private boolean allowedByRateLimiter(String et, String eid) { RRateLimiter limiter = redisson.getRateLimiter(String.format("rl:sds-rebuild:%s:%s", et, eid)); limiter.trySetRate(RateType.OVERALL, ratePermits, Duration.ofSeconds(rateWindowSeconds)); return limiter.tryAcquire(1); }

    private long bitCountShardsPipelined(String metric, String etype, String eid) {
        Set<String> keys = redis.keys(String.format("bm:%s:%s:%s:*", metric, etype, eid));
        if (keys.isEmpty()) return 0L;
        List<Object> res = redis.executePipelined((RedisCallback<Object>) connection -> { for (String k : keys) connection.stringCommands().bitCount(k.getBytes(StandardCharsets.UTF_8)); return null; });
        long sum = 0L;
        for (Object o : res) if (o instanceof Number n) sum += n.longValue();
        return sum;
    }

    private static long readInt32BE(byte[] buf, int off) { long n = 0; for (int i = 0; i < 4; i++) n = (n << 8) | (buf[off + i] & 0xFFL); return n; }
    private static void writeInt32BE(byte[] buf, int off, long val) { long n = Math.max(0, Math.min(val, 0xFFFF_FFFFL)); buf[off] = (byte) ((n >>> 24) & 0xFF); buf[off + 1] = (byte) ((n >>> 16) & 0xFF); buf[off + 2] = (byte) ((n >>> 8) & 0xFF); buf[off + 3] = (byte) (n & 0xFF); }

    private static final String TOGGLE_LUA = """
            local bmKey = KEYS[1]
            local offset = tonumber(ARGV[1])
            local op = ARGV[2]
            local prev = redis.call('GETBIT', bmKey, offset)
            if op == 'add' then
              if prev == 1 then return 0 end
              redis.call('SETBIT', bmKey, offset, 1)
              return 1
            elseif op == 'remove' then
              if prev == 0 then return 0 end
              redis.call('SETBIT', bmKey, offset, 0)
              return 1
            end
            return -1
            """;
}
