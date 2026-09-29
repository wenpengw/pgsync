package com.example.pgsync.metadata.model;

/**
 * 冲突键来源
 */
public enum KeySource {
    TARGET_PRIMARY_KEY,     // 目标表主键
    TARGET_UNIQUE_INDEX,    // 目标表唯一索引
    SOURCE_PK_MATCHED,      // 源表主键在目标表有唯一约束
    CONFIGURED_FALLBACK,    // 配置的备用键
    NONE_FULL_OVERWRITE,    // 无键，降级全量覆盖
    NONE_APPEND_ONLY,       // 无键，纯追加
    NONE_FAIL               // 无键，失败
}
