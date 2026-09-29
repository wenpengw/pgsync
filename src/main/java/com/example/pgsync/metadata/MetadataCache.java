package com.example.pgsync.metadata;

import com.example.pgsync.metadata.model.TableMetadata;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 元数据缓存（单次同步任务内有效，避免重复查询）
 */
public class MetadataCache {
    private final Map<String, TableMetadata> store = new ConcurrentHashMap<>();

    public TableMetadata get(String key) {
        return store.get(key);
    }

    public void put(String key, TableMetadata meta) {
        store.put(key, meta);
    }

    public void clear() {
        store.clear();
    }
}
