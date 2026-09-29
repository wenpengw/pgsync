package com.example.pgsync.mapping;

import com.example.pgsync.metadata.model.ColumnMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * 字段映射结果
 */
public class ColumnMapping {
    private final List<SyncColumn> syncColumns = new ArrayList<>();
    private final List<String> ignoredSourceColumns = new ArrayList<>();  // 源有目标无 → 忽略
    private final List<String> targetOnlyFillable = new ArrayList<>();    // 目标独有且可自动填充
    private final List<String> targetOnlyBlocking = new ArrayList<>();     // 目标独有 NOT NULL 无默认 → 失败
    private final List<String> skippedGeneratedColumns = new ArrayList<>(); // 生成列，两边都跳过
    private final List<String> typeWarnings = new ArrayList<>();          // 类型 RISKY 提示

    public List<SyncColumn> getSyncColumns() {
        return syncColumns;
    }

    public List<String> getIgnoredSourceColumns() {
        return ignoredSourceColumns;
    }

    public List<String> getTargetOnlyFillable() {
        return targetOnlyFillable;
    }

    public List<String> getTargetOnlyBlocking() {
        return targetOnlyBlocking;
    }

    public List<String> getSkippedGeneratedColumns() {
        return skippedGeneratedColumns;
    }

    public List<String> getTypeWarnings() {
        return typeWarnings;
    }

    /** 同步字段（按目标表列序） */
    public List<ColumnMetadata> targetColumns() {
        return syncColumns.stream().map(SyncColumn::getTargetColumn).toList();
    }
}
