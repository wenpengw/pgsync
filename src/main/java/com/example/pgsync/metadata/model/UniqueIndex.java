package com.example.pgsync.metadata.model;

import java.util.List;

/**
 * 唯一索引（含主键、唯一约束），用于无主键表降级探测
 */
public class UniqueIndex {
    private final String indexName;
    private final List<String> columns;     // 按索引定义列序
    private final boolean primary;

    public UniqueIndex(String indexName, List<String> columns, boolean primary) {
        this.indexName = indexName;
        this.columns = columns;
        this.primary = primary;
    }

    public String getIndexName() {
        return indexName;
    }

    public List<String> getColumns() {
        return columns;
    }

    public boolean isPrimary() {
        return primary;
    }

    /** 该索引是否所有列均非空（NULL 不参与唯一判定，不可用） */
    public boolean allNotNull(java.util.function.Function<String, ColumnMetadata> colLookup) {
        return columns.stream()
                .map(c -> colLookup.apply(c))
                .allMatch(c -> c != null && !c.isNullable());
    }
}
