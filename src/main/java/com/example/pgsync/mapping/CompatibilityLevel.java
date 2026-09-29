package com.example.pgsync.mapping;

/**
 * 类型兼容级别
 */
public enum CompatibilityLevel {
    IDENTICAL,     // 完全一致
    COMPATIBLE,    // 同类族可安全转换
    RISKY,         // 可能截断/溢出
    INCOMPATIBLE   // 跨类族，禁止同步
}
