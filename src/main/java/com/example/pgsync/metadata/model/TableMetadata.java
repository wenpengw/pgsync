package com.example.pgsync.metadata.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表元数据
 */
public class TableMetadata {
    private String schema;
    private String tableName;
    private boolean exists;
    private final List<ColumnMetadata> columns = new ArrayList<>();
    private final Map<String, ColumnMetadata> columnMap = new LinkedHashMap<>();
    private final List<String> primaryKeys = new ArrayList<>();      // 按主键内序号排序
    private final List<UniqueIndex> uniqueIndexes = new ArrayList<>();
    private final List<ForeignKey> foreignKeys = new ArrayList<>();

    public TableMetadata(String schema, String tableName) {
        this.schema = schema;
        this.tableName = tableName;
    }

    public String getSchema() {
        return schema;
    }

    public String getTableName() {
        return tableName;
    }

    public boolean isExists() {
        return exists;
    }

    public void setExists(boolean exists) {
        this.exists = exists;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public void addColumn(ColumnMetadata col) {
        columns.add(col);
        columnMap.put(col.lowerName(), col);
    }

    public List<ColumnMetadata> getColumns() {
        return columns;
    }

    public ColumnMetadata getColumn(String name) {
        return columnMap.get(name.toLowerCase());
    }

    public boolean hasColumn(String name) {
        return columnMap.containsKey(name.toLowerCase());
    }

    public void setPrimaryKeys(List<String> pks) {
        this.primaryKeys.clear();
        this.primaryKeys.addAll(pks);
    }

    public List<String> getPrimaryKeys() {
        return primaryKeys;
    }

    public boolean hasPrimaryKey() {
        return !primaryKeys.isEmpty();
    }

    public List<UniqueIndex> getUniqueIndexes() {
        return uniqueIndexes;
    }

    public void addUniqueIndex(UniqueIndex idx) {
        uniqueIndexes.add(idx);
    }

    public List<ForeignKey> getForeignKeys() {
        return foreignKeys;
    }

    public void addForeignKey(ForeignKey fk) {
        foreignKeys.add(fk);
    }

    /** 可写列（剔除生成列） */
    public List<ColumnMetadata> writableColumns() {
        return columns.stream().filter(ColumnMetadata::isWritable).toList();
    }
}
