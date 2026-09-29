package com.example.pgsync.config;

/**
 * 无主键表的降级策略：
 * AUTO_FULL_OVERWRITE - 自动降级为 TRUNCATE/DELETE + INSERT（需 full-overwrite 允许）
 * APPEND_ONLY         - 纯 INSERT，不去重（仅适合空表初始化）
 * FAIL               - 直接失败跳过
 */
public enum NoPrimaryKeyStrategy {
    AUTO_FULL_OVERWRITE,
    APPEND_ONLY,
    FAIL
}
