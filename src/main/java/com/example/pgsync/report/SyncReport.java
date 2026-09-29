package com.example.pgsync.report;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局同步报告
 */
public class SyncReport {
    private final List<TableSyncResult> results = new ArrayList<>();
    private long startMs;
    private long endMs;

    public void add(TableSyncResult r) {
        results.add(r);
    }

    public List<TableSyncResult> getResults() {
        return results;
    }

    public void setStartMs(long startMs) {
        this.startMs = startMs;
    }

    public void setEndMs(long endMs) {
        this.endMs = endMs;
    }

    public int successCount() {
        return (int) results.stream().filter(TableSyncResult::isSuccess).count();
    }

    public int failCount() {
        return (int) results.stream().filter(r -> !r.isSuccess()).count();
    }

    public long totalDurationMs() {
        return endMs - startMs;
    }
}
