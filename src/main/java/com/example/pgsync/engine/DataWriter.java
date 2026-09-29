package com.example.pgsync.engine;

import com.example.pgsync.config.SyncMode;
import com.example.pgsync.exception.SyncException;
import com.example.pgsync.mapping.ColumnMapping;
import com.example.pgsync.metadata.model.ColumnMetadata;
import com.example.pgsync.metadata.model.ConflictKey;
import com.example.pgsync.sql.SqlGuard;
import com.example.pgsync.sql.UpsertSqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * 目标库数据写入：批量 upsert / insert
 */
public class DataWriter {
    private static final Logger log = LoggerFactory.getLogger(DataWriter.class);

    private final String schema;
    private final String table;
    private final ColumnMapping mapping;
    private final ConflictKey conflictKey;
    private final SqlGuard sqlGuard;
    private final int commitEveryBatches;   // 每多少批提交一次
    private final SyncMode mode;

    public DataWriter(DataSource targetDs, String schema, String table, ColumnMapping mapping,
                      ConflictKey conflictKey, SqlGuard sqlGuard, int batchSize, SyncMode mode) {
        this.schema = schema;
        this.table = table;
        this.mapping = mapping;
        this.conflictKey = conflictKey;
        this.sqlGuard = sqlGuard;
        this.commitEveryBatches = Math.max(1, 20000 / Math.max(1, batchSize)); // 约 2 万行提交一次
        this.mode = mode;
    }

    /**
     * 在已开启的事务连接上批量写入
     */
    public int writeBatchInTx(Connection conn, List<Object[]> batch) {
        String sql = buildSql();
        sqlGuard.check(sql);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] row : batch) {
                for (int i = 0; i < mapping.getSyncColumns().size(); i++) {
                    ColumnMetadata tgtCol = mapping.getSyncColumns().get(i).getTargetColumn();
                    TypeConverter.set(ps, i + 1, row[i], tgtCol);
                }
                // 打印每条数据的完整 SQL（拼接参数值）
                StringBuilder sb = new StringBuilder(sql);
                sb.append(" VALUES (");
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) sb.append(", ");
                    Object val = row[i];
                    if (val == null) {
                        sb.append("NULL");
                    } else {
                        sb.append(formatValue(val));
                    }
                }
                sb.append(")");
                //log.info("[{}] {}", table, sb);
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            int affected = 0;
            for (int r : results) {
                if (r > 0) affected += r;
                // -2 表示 executeBatch 不支持统计（某些驱动返回 SUCCESS_NO_INFO）
            }
            return affected;
        } catch (SQLException e) {
            throw new SyncException("写入目标表失败: " + schema + "." + table + " - " + e.getMessage(), e);
        }
    }

    private String buildSql() {
        if (mode == SyncMode.INSERT_SKIP_EXISTING) {
            return UpsertSqlBuilder.buildInsertSkipExisting(schema, table, mapping, conflictKey);
        }
        return UpsertSqlBuilder.buildInsert(schema, table, mapping, conflictKey);
    }

    /** 将对象格式化为 SQL 字面量 */
    private static String formatValue(Object val) {
        if (val == null) return "NULL";
        if (val instanceof java.time.LocalDateTime) {
            return "'" + val.toString().replace("'", "''") + "'";
        }
        if (val instanceof java.time.LocalDate) {
            return "'" + val.toString().replace("'", "''") + "'";
        }
        if (val instanceof java.time.LocalTime) {
            return "'" + val.toString().replace("'", "''") + "'";
        }
        if (val instanceof java.sql.Timestamp) {
            return "'" + val.toString().replace("'", "''") + "'";
        }
        if (val instanceof String) {
            return "'" + val.toString().replace("'", "''") + "'";
        }
        // 数字、布尔等直接转字符串
        return val.toString();
    }

    /**
     * 清空目标表：DELETE 或 TRUNCATE
     * 仅允许在全量覆盖场景下执行（fullOverwrite=true，即 FULL_OVERWRITE 模式或无键降级全量覆盖）
     */
    public void clearTarget(Connection conn, String strategy, boolean fullOverwrite) {
        String sql;
        if ("TRUNCATE".equalsIgnoreCase(strategy)) {
            sql = "TRUNCATE TABLE " + com.example.pgsync.sql.SqlIdentifierUtils.qualify(schema, table);
        } else {
            sql = "DELETE FROM " + com.example.pgsync.sql.SqlIdentifierUtils.qualify(schema, table);
        }
        sqlGuard.checkClear(sql, strategy, fullOverwrite);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int n = ps.executeUpdate();
            log.info("清空目标表 {} ({}): 删除 {} 行", table, strategy, n);
        } catch (SQLException e) {
            throw new SyncException("清空目标表失败: " + schema + "." + table + " - " + e.getMessage(), e);
        }
    }

    public int getCommitEveryBatches() {
        return commitEveryBatches;
    }
}
