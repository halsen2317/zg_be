package com.ccnu.counter.event;

import lombok.Data;

/** 计数增量事件，由 Kafka 异步传输。 */
@Data
public class CounterEvent {
    private String entityType;
    private String entityId;
    private String metric;
    private int idx;
    private long userId;
    private int delta;

    public CounterEvent() {}

    public CounterEvent(String entityType, String entityId, String metric, int idx, long userId, int delta) {
        this.entityType = entityType; this.entityId = entityId; this.metric = metric;
        this.idx = idx; this.userId = userId; this.delta = delta;
    }

    public static CounterEvent of(String et, String eid, String m, int idx, long uid, int d) {
        return new CounterEvent(et, eid, m, idx, uid, d);
    }
}