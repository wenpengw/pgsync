package com.example.pgsync.config;

/**
 * 全量覆盖参数
 */
public class FullOverwriteConfig {
    private String clearStrategy = "DELETE";   // TRUNCATE | DELETE
    private boolean cascade = false;            // 恒为 false

    public String getClearStrategy() {
        return clearStrategy;
    }

    public void setClearStrategy(String clearStrategy) {
        this.clearStrategy = clearStrategy;
    }

    public boolean isCascade() {
        return cascade;
    }

    public void setCascade(boolean cascade) {
        // 强制禁止级联，避免误清其他表
        this.cascade = false;
    }
}
