package com.example.pgsync.config;

/**
 * 同步模式：
 * UPSERT_MERGE - 基于主键 upsert，保留历史数据
 * FULL_OVERWRITE - 同步前清空目标表再全量导入
 */
public enum SyncMode {
    UPSERT_MERGE,
    FULL_OVERWRITE,
    INSERT_SKIP_EXISTING
}
