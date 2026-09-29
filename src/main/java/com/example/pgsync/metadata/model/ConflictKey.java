package com.example.pgsync.metadata.model;

import java.util.List;

/**
 * 冲突键（去重依据）探测结果
 */
public class ConflictKey {
    private final List<String> columns;        // 冲突键列（目标表原始列名），可为空
    private final KeySource source;            // 来源
    private final boolean upsertable;          // 是否可做 upsert
    private final String description;          // 日志描述

    public ConflictKey(List<String> columns, KeySource source, boolean upsertable, String description) {
        this.columns = columns;
        this.source = source;
        this.upsertable = upsertable;
        this.description = description;
    }

    public static ConflictKey upsertable(List<String> cols, KeySource src, String desc) {
        return new ConflictKey(cols, src, true, desc);
    }

    public static ConflictKey none(KeySource src, String desc) {
        return new ConflictKey(List.of(), src, false, desc);
    }

    public List<String> getColumns() {
        return columns;
    }

    public KeySource getSource() {
        return source;
    }

    public boolean isUpsertable() {
        return upsertable;
    }

    public String getDescription() {
        return description;
    }
}
