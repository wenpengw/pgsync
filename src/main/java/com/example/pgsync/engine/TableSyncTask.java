package com.example.pgsync.engine;

import com.example.pgsync.config.SyncMode;
import com.example.pgsync.config.SyncProperties;
import com.example.pgsync.exception.SyncException;
import com.example.pgsync.exception.TargetTableNotFoundException;
import com.example.pgsync.mapping.ColumnDiff;
import com.example.pgsync.mapping.ColumnMapper;
import com.example.pgsync.mapping.ColumnMapping;
import com.example.pgsync.mapping.MappingValidator;
import com.example.pgsync.mapping.SchemaDiffAnalyzer;
import com.example.pgsync.metadata.KeyResolver;
import com.example.pgsync.metadata.MetadataService;
import com.example.pgsync.metadata.model.ColumnMetadata;
import com.example.pgsync.metadata.model.ConflictKey;
import com.example.pgsync.metadata.model.TableMetadata;
import com.example.pgsync.report.TableSyncResult;
import com.example.pgsync.sql.SqlGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 单表同步任务编排
 */
public class TableSyncTask {
    private static final Logger log = LoggerFactory.getLogger(TableSyncTask.class);

    private final String tableName;
    private final DataSource sourceDs;
    private final DataSource targetDs;
    private final SyncProperties props;
    private final MetadataService metadataService;
    private final SqlGuard sqlGuard;

    public TableSyncTask(String tableName, DataSource sourceDs, DataSource targetDs,
                         SyncProperties props, MetadataService metadataService,
                         SqlGuard sqlGuard) {
        this.tableName = tableName;
        this.sourceDs = sourceDs;
        this.targetDs = targetDs;
        this.props = props;
        this.metadataService = metadataService;
        this.sqlGuard = sqlGuard;
    }

    public TableSyncResult execute() {
        return execute(false);
    }

    /**
     * 执行同步或仅分析结构
     *
     * @param analyzeOnly true=仅加载元数据/映射/冲突键并打印分析报告，不读数据、不写目标库
     */
    public TableSyncResult execute(boolean analyzeOnly) {
        long start = System.currentTimeMillis();
        TableSyncResult result = new TableSyncResult(tableName);
        String schema = props.getSchema();

        try {
            // 1. 加载元数据
            TableMetadata sourceMeta = metadataService.load(sourceDs, schema, tableName);
            TableMetadata targetMeta = metadataService.load(targetDs, schema, tableName);

            if (!targetMeta.isExists()) {
                throw new TargetTableNotFoundException(
                        "目标库 " + schema + "." + tableName + " 不存在。"
                                + "本工具不创建表，请先在目标库手工建表后重试。");
            }

            // 2. 字段映射
            ColumnMapper mapper = new ColumnMapper();
            ColumnMapping mapping = mapper.map(sourceMeta, targetMeta);

            // 3. 冲突键探测
            List<String> syncColNames = mapping.getSyncColumns().stream()
                    .map(c -> c.getTargetName()).toList();
            List<String> fallback = props.getFallbackKeys().get(tableName);
            SyncMode mode = props.getMode();
            boolean fullOverwrite = mode == SyncMode.FULL_OVERWRITE;
            KeyResolver keyResolver = new KeyResolver(props.getNoPrimaryKeyStrategy());
            ConflictKey conflictKey = keyResolver.resolve(sourceMeta, targetMeta,
                    syncColNames, fallback, fullOverwrite);

            // 4. 日志输出结构分析（含"字段不存在/不同"逐列对比）
            logAnalysis(sourceMeta, targetMeta, conflictKey, fullOverwrite);

            if (analyzeOnly) {
                // 仅分析模式：跳过严格校验，不读源表、不写目标库
                result.setSuccess(true);
                result.setDetail("ANALYZE_ONLY：未读取/写入数据");
                return result;
            }

            // 5. 同步前严格校验（阻塞列、无可同步字段等）
            MappingValidator.validate(mapping);

            // 6. 执行同步（按 RetryConfig 重试）
            long sourceCount = new DataReader(sourceDs, schema, tableName, mapping, props.getFetchSize())
                    .estimateCount();
            executeTransferWithRetry(targetMeta, mapping, conflictKey, result, fullOverwrite, sourceCount, mode);

            result.setSuccess(true);
            result.setReadRows(sourceCount >= 0 ? sourceCount : result.getWrittenRows());

        } catch (TargetTableNotFoundException e) {
            fail(result, "TARGET_TABLE_NOT_FOUND", e.getMessage());
        } catch (com.example.pgsync.exception.SyncValidationException e) {
            fail(result, "VALIDATION_FAILED", e.getMessage());
        } catch (SyncException e) {
            fail(result, "SYNC_ERROR", e.getMessage());
        } catch (Exception e) {
            fail(result, "UNEXPECTED", e.getMessage());
        } finally {
            result.setDurationMs(System.currentTimeMillis() - start);
        }
        return result;
    }

    private void executeTransferWithRetry(TableMetadata targetMeta, ColumnMapping mapping,
                                           ConflictKey conflictKey, TableSyncResult result,
                                           boolean fullOverwrite, long sourceCount, SyncMode mode) {
        com.example.pgsync.config.RetryConfig rc = props.getRetry();
        int maxAttempts = (rc != null && rc.isEnabled()) ? Math.max(1, rc.getMaxAttempts()) : 1;
        long backoff = (rc != null) ? rc.getBackoffMs() : 0L;
        SyncException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                executeTransfer(targetMeta, mapping, conflictKey, result, fullOverwrite, sourceCount, mode);
                return;
            } catch (SyncException e) {
                last = e;
                if (attempt < maxAttempts) {
                    log.warn("  [{}] 第 {} 次同步失败，{}ms 后重试（共 {} 次）: {}",
                            tableName, attempt, backoff, maxAttempts, e.getMessage());
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw last != null ? last : new SyncException("同步失败（未知原因）: " + tableName);
    }

    private void executeTransfer(TableMetadata targetMeta, ColumnMapping mapping,
                                 ConflictKey conflictKey, TableSyncResult result,
                                 boolean fullOverwrite, long sourceCount, SyncMode mode) {
        DataReader reader = new DataReader(sourceDs, props.getSchema(), tableName,
                mapping, props.getFetchSize());
        DataWriter writer = new DataWriter(targetDs, props.getSchema(), tableName,
                mapping, conflictKey, sqlGuard, props.getBatchSize(), mode);

        boolean useClear = fullOverwrite || (!conflictKey.isUpsertable()
                && conflictKey.getSource() == com.example.pgsync.metadata.model.KeySource.NONE_FULL_OVERWRITE);

        final Connection[] holder = new Connection[1];
        try {
            Connection conn = targetDs.getConnection();
            holder[0] = conn;
            conn.setAutoCommit(false);
            if (useClear) {
                String strategy = props.getFullOverwrite().getClearStrategy();
                writer.clearTarget(conn, strategy, useClear);
            }

            final long[] written = {0};
            final long[] batchCount = {0};
            reader.readInBatches(props.getBatchSize(), batch -> {
                int affected = writer.writeBatchInTx(conn, batch);
                written[0] += Math.max(affected, batch.size()); // 处理条数（executeBatch 可能返回 -2）
                batchCount[0]++;
                if (batchCount[0] % writer.getCommitEveryBatches() == 0) {
                    try {
                        conn.commit();
                    } catch (java.sql.SQLException e) {
                        throw new com.example.pgsync.exception.SyncException("提交事务失败: " + tableName, e);
                    }
                    log.info("  [{}] 已处理 {} 行...", tableName, written[0]);
                }
            });
            try {
                conn.commit();
            } catch (java.sql.SQLException e) {
                throw new com.example.pgsync.exception.SyncException("提交事务失败: " + tableName, e);
            }

            result.setWrittenRows(written[0]);
            if (props.isPreciseCount() && sourceCount >= 0) {
                // 估算：插入 = 同步后总数 - 同步前基数（近似）
                result.setInsertRows(Math.max(0, written[0]));
                result.setUpdateRows(Math.max(0, written[0] - result.getInsertRows()));
            }
        } catch (SQLException e) {
            throw new SyncException("事务提交失败: " + tableName, e);
        } finally {
            // 关键：Hikari 连接从池借出时被改成了非自动提交，归还前必须复位，
            // 否则后续从池拿到的连接仍是非自动提交状态，导致普通操作"看起来不生效"。
            Connection conn = holder[0];
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // 复位失败不阻断主流程
                }
                try {
                    conn.close();
                } catch (SQLException ignored) {
                    // 关闭失败不阻断主流程
                }
            }
        }
    }

    private long countTarget(Connection conn, TableMetadata targetMeta) {
        String sql = "SELECT COUNT(*) FROM "
                + com.example.pgsync.sql.SqlIdentifierUtils.qualify(props.getSchema(), tableName);
        try (Statement st = conn.createStatement(); java.sql.ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            log.warn("统计目标表行数失败: {}", e.getMessage());
        }
        return -1;
    }

    private void logAnalysis(TableMetadata sourceMeta, TableMetadata targetMeta,
                             ConflictKey conflictKey, boolean fullOverwrite) {
        StringBuilder sb = new StringBuilder("\n");
        sb.append("[").append(tableName).append("] 表结构分析\n");
        sb.append("  源表   ").append(sourceMeta.getSchema()).append(".")
                .append(sourceMeta.getTableName()).append(" : ")
                .append(sourceMeta.getColumns().size()).append(" 列")
                .append(sourceMeta.hasPrimaryKey() ? " 主键[" + String.join(",", sourceMeta.getPrimaryKeys()) + "]" : " 无主键")
                .append("\n");
        sb.append("  目标表 ").append(targetMeta.getSchema()).append(".")
                .append(targetMeta.getTableName()).append(" : ")
                .append(targetMeta.getColumns().size()).append(" 列")
                .append(targetMeta.hasPrimaryKey() ? " 主键[" + String.join(",", targetMeta.getPrimaryKeys()) + "]" : " 无主键")
                .append("\n");
        sb.append("  ───────────────────────────────────\n");
        appendColumnDiffs(sb, sourceMeta, targetMeta);
        sb.append("  ───────────────────────────────────\n");
        sb.append("  冲突键 : ").append(conflictKey.getDescription())
                .append("  upsertable=").append(conflictKey.isUpsertable()).append("\n");
        String modeDesc = fullOverwrite ? "FULL_OVERWRITE" : "UPSERT_MERGE";
        if (!conflictKey.isUpsertable()) {
            modeDesc = fullOverwrite
                    ? "FULL_OVERWRITE (无冲突键：清表后全量 INSERT)"
                    : "FULL_OVERWRITE (降级)";
        }
        sb.append("  模式   : ").append(modeDesc).append(", batch=").append(props.getBatchSize()).append("\n");
        if (!targetMeta.getForeignKeys().isEmpty()) {
            sb.append("  ⚠ 外键依赖: ");
            targetMeta.getForeignKeys().forEach(fk -> sb.append(fk).append("; "));
            sb.append("\n");
        }
        log.info(sb.toString());
    }

    private void fail(TableSyncResult result, String reason, String detail) {
        result.setSuccess(false);
        result.setFailReason(reason);
        result.setDetail(detail);
        log.error("[{}] 同步失败 [{}]: {}", tableName, reason, detail);
    }

    /**
     * 逐列对比源表/目标表，输出"字段不存在或不同"的差异清单：
     * - ✗ 仅源表存在 → 目标表缺列（同步时忽略）
     * - + 仅目标表存在 → 源表缺列（自动填充 / 阻塞）
     * - ≠ 同名但属性不同（类型 / 可空性 / 默认值等）
     */
    private void appendColumnDiffs(StringBuilder sb, TableMetadata sourceMeta, TableMetadata targetMeta) {
        List<ColumnDiff> diffs = new SchemaDiffAnalyzer().analyze(sourceMeta, targetMeta);

        int same = 0;
        int maxNameLen = 0;
        for (ColumnDiff d : diffs) {
            if (d.isSame()) {
                same++;
            }
            maxNameLen = Math.max(maxNameLen, d.getName().length());
        }
        int pad = Math.max(maxNameLen, 10);

        sb.append("  字段差异对比 (源 ").append(sourceMeta.getColumns().size())
                .append(" 列 → 目标 ").append(targetMeta.getColumns().size()).append(" 列):\n");

        for (ColumnDiff d : diffs) {
            if (d.isSame()) {
                continue;
            }
            sb.append(renderDiffLine(d, pad));
        }
        if (same > 0) {
            StringBuilder names = new StringBuilder();
            for (ColumnDiff d : diffs) {
                if (d.isSame()) {
                    if (names.length() > 0) {
                        names.append(", ");
                    }
                    names.append(d.getName());
                }
            }
            sb.append("    .. ").append(same).append(" 列完全一致: ")
                    .append(truncate(names.toString())).append("\n");
        }
    }

    private String renderDiffLine(ColumnDiff d, int pad) {
        String name = String.format("%-" + pad + "s", d.getName());
        if (d.isSourceOnly()) {
            return String.format("    ✗ %s 源[%s] → 目标[不存在]  已忽略\n",
                    name, shortType(d.getSourceColumn()));
        }
        if (d.isTargetOnly()) {
            ColumnMetadata tc = d.getTargetColumn();
            boolean blocking = !tc.isNullable() && !tc.isHasDefault() && !tc.isIdentity();
            return String.format("    + %s 源[不存在] → 目标[%s]  %s\n",
                    name, ColumnDiff.describe(tc),
                    blocking ? "⚠ 阻塞(NOT NULL 无默认)" : "自动填充");
        }
        return String.format("    ≠ %s 源[%s] → 目标[%s]  %s\n",
                name, shortType(d.getSourceColumn()), shortType(d.getTargetColumn()),
                String.join("; ", d.getDifferences()));
    }

    private String shortType(ColumnMetadata col) {
        if (col == null) {
            return "(不存在)";
        }
        String t = col.getPgTypeName() == null ? "?" : col.getPgTypeName();
        return col.isNullable() ? t : t + " NOT NULL";
    }

    private String truncate(String s) {
        if (s.length() > 200) return s.substring(0, 200) + " ...";
        return s;
    }
}
