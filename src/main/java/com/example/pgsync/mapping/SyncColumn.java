package com.example.pgsync.mapping;

import com.example.pgsync.metadata.model.ColumnMetadata;

/**
 * 同步字段：源列引用 + 目标列引用（用目标列的类型信息写库）
 */
public class SyncColumn {
    private final ColumnMetadata sourceColumn;
    private final ColumnMetadata targetColumn;

    public SyncColumn(ColumnMetadata sourceColumn, ColumnMetadata targetColumn) {
        this.sourceColumn = sourceColumn;
        this.targetColumn = targetColumn;
    }

    public ColumnMetadata getSourceColumn() {
        return sourceColumn;
    }

    public ColumnMetadata getTargetColumn() {
        return targetColumn;
    }

    public String getSourceName() {
        return sourceColumn.getName();
    }

    public String getTargetName() {
        return targetColumn.getName();
    }
}
