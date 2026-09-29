package com.example.pgsync.report;

/**
 * 单表同步结果
 */
public class TableSyncResult {
    private final String tableName;
    private boolean success;
    private long readRows;
    private long writtenRows;          // 总的 upsert 处理条数
    private long insertRows;           // 估算插入数（precise-count 时）
    private long updateRows;           // 估算更新数
    private long durationMs;
    private String failReason;         // 失败原因分类
    private String detail;             // 详细描述

    public TableSyncResult(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public long getReadRows() {
        return readRows;
    }

    public void setReadRows(long readRows) {
        this.readRows = readRows;
    }

    public long getWrittenRows() {
        return writtenRows;
    }

    public void setWrittenRows(long writtenRows) {
        this.writtenRows = writtenRows;
    }

    public long getInsertRows() {
        return insertRows;
    }

    public void setInsertRows(long insertRows) {
        this.insertRows = insertRows;
    }

    public long getUpdateRows() {
        return updateRows;
    }

    public void setUpdateRows(long updateRows) {
        this.updateRows = updateRows;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
