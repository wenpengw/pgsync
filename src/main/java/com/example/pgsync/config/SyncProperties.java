package com.example.pgsync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局同步配置
 */
@Component
@ConfigurationProperties(prefix = "pgsync")
public class SyncProperties {

    private DbConfig source = new DbConfig();
    private DbConfig target = new DbConfig();
    private String schema = "public";
    private int batchSize = 1000;
    private int fetchSize = 1000;
    private SyncMode mode = SyncMode.UPSERT_MERGE;
    private boolean failFast = false;
    private NoPrimaryKeyStrategy noPrimaryKeyStrategy = NoPrimaryKeyStrategy.FAIL;
    private int parallelism = 1;
    private List<String> tables = new ArrayList<>();
    private Map<String, List<String>> fallbackKeys = new LinkedHashMap<>();
    private ClearConfig clear = new ClearConfig();
    private SafetyConfig safety = new SafetyConfig();
    private FullOverwriteConfig fullOverwrite = new FullOverwriteConfig();
    private RetryConfig retry = new RetryConfig();
    private boolean preciseCount = true;

    public DbConfig getSource() {
        return source;
    }

    public void setSource(DbConfig source) {
        this.source = source;
    }

    public DbConfig getTarget() {
        return target;
    }

    public void setTarget(DbConfig target) {
        this.target = target;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getFetchSize() {
        return fetchSize;
    }

    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }

    public SyncMode getMode() {
        return mode;
    }

    public void setMode(SyncMode mode) {
        this.mode = mode;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public NoPrimaryKeyStrategy getNoPrimaryKeyStrategy() {
        return noPrimaryKeyStrategy;
    }

    public void setNoPrimaryKeyStrategy(NoPrimaryKeyStrategy noPrimaryKeyStrategy) {
        this.noPrimaryKeyStrategy = noPrimaryKeyStrategy;
    }

    public int getParallelism() {
        return parallelism;
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    public List<String> getTables() {
        return tables;
    }

    public void setTables(List<String> tables) {
        this.tables = tables;
    }

    public Map<String, List<String>> getFallbackKeys() {
        return fallbackKeys;
    }

    public void setFallbackKeys(Map<String, List<String>> fallbackKeys) {
        this.fallbackKeys = fallbackKeys;
    }

    public ClearConfig getClear() {
        return clear;
    }

    public void setClear(ClearConfig clear) {
        this.clear = clear;
    }

    public SafetyConfig getSafety() {
        return safety;
    }

    public void setSafety(SafetyConfig safety) {
        this.safety = safety;
    }

    public FullOverwriteConfig getFullOverwrite() {
        return fullOverwrite;
    }

    public void setFullOverwrite(FullOverwriteConfig fullOverwrite) {
        this.fullOverwrite = fullOverwrite;
    }

    public RetryConfig getRetry() {
        return retry;
    }

    public void setRetry(RetryConfig retry) {
        this.retry = retry;
    }

    public boolean isPreciseCount() {
        return preciseCount;
    }

    public void setPreciseCount(boolean preciseCount) {
        this.preciseCount = preciseCount;
    }
}
