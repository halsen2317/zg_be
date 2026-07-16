package com.ccnu.counter.schema;

import java.util.Map;
import java.util.Set;

/**
 * 计数 Schema 常量。
 * <p>SDS 固定结构：5 段 × 4 字节（大端 Int32），下标 1=like, 2=fav。</p>
 */
public final class CounterSchema {
    public static final String SCHEMA_ID = "v1";
    public static final int FIELD_SIZE = 4;
    public static final int SCHEMA_LEN = 5;

    public static final int IDX_LIKE = 1;
    public static final int IDX_FAV = 2;

    public static final Map<String, Integer> NAME_TO_IDX = Map.of("like", IDX_LIKE, "fav", IDX_FAV);
    public static final Set<String> SUPPORTED_METRICS = NAME_TO_IDX.keySet();

    private CounterSchema() {}
}
