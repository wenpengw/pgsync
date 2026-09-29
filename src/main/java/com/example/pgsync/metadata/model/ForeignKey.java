package com.example.pgsync.metadata.model;

import java.util.List;

/**
 * 外键依赖（仅用于提示，不影响同步逻辑）
 */
public class ForeignKey {
    private final String constraintName;
    private final List<String> columns;
    private final String refTable;

    public ForeignKey(String constraintName, List<String> columns, String refTable) {
        this.constraintName = constraintName;
        this.columns = columns;
        this.refTable = refTable;
    }

    public String getConstraintName() {
        return constraintName;
    }

    public List<String> getColumns() {
        return columns;
    }

    public String getRefTable() {
        return refTable;
    }

    @Override
    public String toString() {
        return columns + " -> " + refTable;
    }
}
