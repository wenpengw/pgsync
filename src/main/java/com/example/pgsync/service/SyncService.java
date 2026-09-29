package com.example.pgsync.service;

import com.example.pgsync.config.SyncProperties;
import com.example.pgsync.engine.TableSyncTask;
import com.example.pgsync.mapping.ColumnDiff;
import com.example.pgsync.mapping.SchemaDiffAnalyzer;
import com.example.pgsync.metadata.MetadataService;
import com.example.pgsync.metadata.model.ColumnMetadata;
import com.example.pgsync.metadata.model.TableMetadata;
import com.example.pgsync.report.ReportPrinter;
import com.example.pgsync.report.SyncReport;
import com.example.pgsync.report.TableSyncResult;
import com.example.pgsync.sql.SqlGuard;
import com.example.pgsync.sql.SqlIdentifierUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 同步总调度：遍历表清单、异常隔离、汇总报告
 */
@Service
public class SyncService {
    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final SyncProperties props;
    private final DataSource sourceDs;
    private final DataSource targetDs;
    private final MetadataService metadataService;
    private final SqlGuard sqlGuard;
    private final SyncLock lock;

    public SyncService(SyncProperties props,
                       @Qualifier("sourceDataSource") DataSource sourceDs,
                       @Qualifier("targetDataSource") DataSource targetDs,
                       MetadataService metadataService,
                       SqlGuard sqlGuard, SyncLock lock) {
        this.props = props;
        this.sourceDs = sourceDs;
        this.targetDs = targetDs;
        this.metadataService = metadataService;
        this.sqlGuard = sqlGuard;
        this.lock = lock;
    }

    public SyncReport syncAll() {
        return syncTables(props.getTables());
    }

    public SyncReport syncTables(List<String> tables) {
        if (!lock.tryLock()) {
            // 不抛异常，返回带明确提示的报告，便于 HTTP 直接展示
            SyncReport busy = new SyncReport();
            busy.setStartMs(System.currentTimeMillis());
            busy.setEndMs(System.currentTimeMillis());
            TableSyncResult r = new TableSyncResult("(global)");
            r.setSuccess(false);
            r.setFailReason("BUSY");
            r.setDetail("已有同步任务正在执行，请稍后重试，避免对目标库并发写入");
            busy.add(r);
            log.warn("同步被拒绝：已有任务正在执行");
            return busy;
        }
        try {
            SyncReport report = new SyncReport();
            report.setStartMs(System.currentTimeMillis());

            List<String> targets = (tables == null || tables.isEmpty())
                    ? props.getTables() : tables;

            log.info("========== 开始同步，共 {} 张表 ==========", targets.size());

            for (String tableName : targets) {
                TableSyncTask task = new TableSyncTask(tableName, sourceDs, targetDs,
                        props, metadataService, sqlGuard);
                TableSyncResult r = task.execute();
                report.add(r);
                if (!r.isSuccess() && props.isFailFast()) {
                    log.error("fail-fast 模式：遇失败即终止");
                    break;
                }
            }

            report.setEndMs(System.currentTimeMillis());
            ReportPrinter.print(report);
            return report;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 连通性预检（只读）
     */
    public java.util.Map<String, Object> health() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("source", checkDb(sourceDs, "源库"));
        result.put("target", checkDb(targetDs, "目标库"));
        result.put("tables", props.getTables());
        result.put("ddlForbidden", props.getSafety().isDdlForbidden());
        return result;
    }

    private java.util.Map<String, Object> checkDb(DataSource ds, String label) {
        java.util.Map<String, Object> info = new java.util.LinkedHashMap<>();
        try (java.sql.Connection conn = ds.getConnection()) {
            java.sql.DatabaseMetaData md = conn.getMetaData();
            info.put("status", "OK");
            info.put("product", md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
            info.put("database", conn.getCatalog());
            info.put("user", conn.getMetaData().getUserName());
            info.put("readOnly", conn.isReadOnly());
        } catch (java.sql.SQLException e) {
            info.put("status", "FAIL");
            info.put("error", e.getMessage());
        }
        return info;
    }

    /**
     * 仅分析结构（只读，不动数据）
     */
    public void analyzeAll() {
        analyzeTables(props.getTables());
    }

    public void analyzeTables(List<String> tables) {
        List<String> targets = (tables == null || tables.isEmpty())
                ? props.getTables() : tables;
        for (String tableName : targets) {
            TableSyncTask task = new TableSyncTask(tableName, sourceDs, targetDs,
                    props, metadataService, sqlGuard);
            task.execute(true);   // analyzeOnly=true：仅分析结构，不读写数据
        }
    }

    /**
     * 清空目标表原始数据（只删目标库，不读源库、不改表结构）
     * 表清单默认取自 clear-tables.yml 的 pgsync.clear.tables，
     * 清空方式（DELETE / TRUNCATE）取自 full-overwrite.clear-strategy。
     * 单表失败不影响其他表。
     */
    public SyncReport clearTargetTables(List<String> tables) {
        if (!lock.tryLock()) {
            SyncReport busy = new SyncReport();
            busy.setStartMs(System.currentTimeMillis());
            busy.setEndMs(System.currentTimeMillis());
            TableSyncResult r = new TableSyncResult("(global)");
            r.setSuccess(false);
            r.setFailReason("BUSY");
            r.setDetail("已有同步/清表任务正在执行，请稍后重试，避免对目标库并发写入");
            busy.add(r);
            log.warn("清表被拒绝：已有任务正在执行");
            return busy;
        }
        try {
            List<String> targets = (tables == null || tables.isEmpty())
                    ? props.getClear().getTables() : tables;
            String strategy = props.getFullOverwrite().getClearStrategy();

            SyncReport report = new SyncReport();
            report.setStartMs(System.currentTimeMillis());
            log.warn("========== 开始清空目标表数据，共 {} 张表，策略={} ==========",
                    targets.size(), strategy);

            for (String tableName : targets) {
                TableSyncResult r = new TableSyncResult(tableName);
                long start = System.currentTimeMillis();
                try {
                    clearOneTable(tableName, strategy, r);
                } catch (Exception e) {
                    r.setSuccess(false);
                    r.setFailReason("CLEAR_FAILED");
                    r.setDetail(e.getMessage());
                    log.error("[{}] 清表失败: {}", tableName, e.getMessage());
                } finally {
                    r.setDurationMs(System.currentTimeMillis() - start);
                    report.add(r);
                }
            }

            report.setEndMs(System.currentTimeMillis());
            ReportPrinter.print(report);
            return report;
        } finally {
            lock.unlock();
        }
    }

    private void clearOneTable(String tableName, String strategy, TableSyncResult r) throws java.sql.SQLException {
        TableMetadata meta = metadataService.load(targetDs, props.getSchema(), tableName);
        if (!meta.isExists()) {
            r.setSuccess(false);
            r.setFailReason("TARGET_TABLE_NOT_FOUND");
            r.setDetail("目标库 " + props.getSchema() + "." + tableName + " 不存在，已跳过（不自动建表）");
            return;
        }

        String sql = "TRUNCATE".equalsIgnoreCase(strategy)
                ? "TRUNCATE TABLE " + SqlIdentifierUtils.qualify(props.getSchema(), tableName)
                : "DELETE FROM " + SqlIdentifierUtils.qualify(props.getSchema(), tableName);
        // 清表属于"全量覆盖"动作，第三个参数必须为 true 才允许 DELETE
        sqlGuard.checkClear(sql, strategy, true);

        try (Connection conn = targetDs.getConnection();
             Statement st = conn.createStatement()) {
            conn.setAutoCommit(true);   // 逐表独立提交，单表失败不影响其他表
            int n = st.executeUpdate(sql);
            r.setSuccess(true);
            r.setWrittenRows(n);
            r.setDetail("已清空 " + n + " 行");
            log.info("[{}] 已清空 {} 行", tableName, n);
        }
    }

    /**
     * 根据分析结果同步目标表结构
     * - 若目标表不存在，从源表创建（仅结构，无数据）
     * - 若目标表存在，同步字段差异（添加、修改、删除）
     * 
     * @return true 表示修改了现有表结构，false 表示新建了表
     */
    public boolean syncTableStructure(String schema, String tableName) {
        log.info("========== 开始同步表结构: {}.{} ==========", schema, tableName);
        
        try {
            // 1. 检查目标表是否存在
            TableMetadata targetMeta = metadataService.load(targetDs, schema, tableName);
            
            if (!targetMeta.isExists()) {
                // 目标表不存在 → 从源表创建
                log.info("  ℹ️ 目标表不存在，从源表创建...");
                createTableFromSource(schema, tableName);
                log.info("  ✅ 目标表已新建");
                return false;  // 新建表
            }
            
            // 2. 目标表存在 → 分析差异并修改
            List<ColumnDiff> diffs = analyzeSchemaDifferences(schema, tableName);
            executeDDLOperations(schema, tableName, diffs);
            return true;  // 修改现有表
            
        } catch (Exception e) {
            log.error("❌ 表结构同步失败: {}", e.getMessage());
            throw new RuntimeException("表结构同步失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从源表创建目标表（仅结构，无数据）
     * 根据源表元数据逐列构建 CREATE TABLE 语句
     * （避免跨库 AS SELECT 问题）
     */
    private void createTableFromSource(String schema, String tableName) throws Exception {
        TableMetadata sourceMeta = metadataService.load(sourceDs, schema, tableName);
        
        if (!sourceMeta.isExists()) {
            throw new RuntimeException("源表 " + schema + "." + tableName + " 不存在");
        }
        
        String qualified = SqlIdentifierUtils.qualify(schema, tableName);
        List<ColumnMetadata> cols = sourceMeta.getColumns();
        
        if (cols.isEmpty()) {
            throw new RuntimeException("源表 " + schema + "." + tableName + " 无列信息");
        }
        
        // 构建 CREATE TABLE 语句
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE ").append(qualified).append(" (\n");
        
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) ddl.append(",\n");
            
            ColumnMetadata col = cols.get(i);
            ddl.append("  ").append(SqlIdentifierUtils.quote(col.getName()))
               .append(" ").append(mapJdbcTypeToSQLType(col));
            
            // 默认值
            if (col.isHasDefault() && col.getDefaultExpr() != null) {
                ddl.append(" DEFAULT ").append(col.getDefaultExpr());
            }
            
            // 可空性
            if (!col.isNullable()) {
                ddl.append(" NOT NULL");
            }
        }
        
        // 主键约束
        List<String> pkCols = sourceMeta.getPrimaryKeys();
        if (!pkCols.isEmpty()) {
            ddl.append(",\n  PRIMARY KEY (");
            for (int i = 0; i < pkCols.size(); i++) {
                if (i > 0) ddl.append(", ");
                ddl.append(SqlIdentifierUtils.quote(pkCols.get(i)));
            }
            ddl.append(")");
        }
        
        ddl.append("\n)");
        
        String createTableDdl = ddl.toString();
        log.info("     DDL: {}", createTableDdl);
        
        try (Connection conn = targetDs.getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(true);
            stmt.execute(createTableDdl);
        } catch (java.sql.SQLException e) {
            log.error("❌ 创建表失败 —— {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 分析源表和目标表的字段差异
     */
    private List<ColumnDiff> analyzeSchemaDifferences(String schema, String tableName) throws Exception {
        TableMetadata sourceMeta = metadataService.load(sourceDs, schema, tableName);
        TableMetadata targetMeta = metadataService.load(targetDs, schema, tableName);
        
        if (!targetMeta.isExists()) {
            throw new RuntimeException("目标表 " + schema + "." + tableName + " 不存在，无法同步结构");
        }
        
        SchemaDiffAnalyzer analyzer = new SchemaDiffAnalyzer();
        return analyzer.analyze(sourceMeta, targetMeta);
    }
    
    /**
     * 执行DDL操作：新增、修改、删除字段。
     * 逐条执行（每条独立提交），单条失败不影响其它列，保证"尽力对齐"源表结构。
     * 可空性对齐时，若源列 NOT NULL 而目标列存在 NULL 值，则删除该列并按源定义重建（数据由后续同步灌入）。
     */
    private void executeDDLOperations(String schema, String tableName, List<ColumnDiff> diffs) {
        // 1. 收集所有 DDL 语句（按列分组，保持顺序）；需连接做 SET NOT NULL 的 NULL 值预检查
        List<String> ddls = new ArrayList<>();
        int addCount = 0, alterCount = 0, dropCount = 0;
        try (Connection conn = targetDs.getConnection()) {
            for (ColumnDiff diff : diffs) {
                if (diff.isSourceOnly()) {
                    ddls.add(buildAddColumnDDL(diff.getSourceColumn(), schema, tableName));
                    addCount++;
                } else if (diff.isTargetOnly()) {
                    ddls.add(buildDropColumnDDL(diff.getTargetColumn(), schema, tableName));
                    dropCount++;
                } else if (!diff.getDifferences().isEmpty()) {
                    ColumnMetadata srcCol = diff.getSourceColumn();
                    ColumnMetadata tgtCol = diff.getTargetColumn();
                    log.info("  🔄 修改字段: {}  类型({} → {}) 可空性(目标{} → 源{})",
                            srcCol.getName(),
                            mapJdbcTypeToSQLType(tgtCol), mapJdbcTypeToSQLType(srcCol),
                            tgtCol.isNullable() ? "可空" : "NOT NULL",
                            srcCol.isNullable() ? "可空" : "NOT NULL");
                    List<String> colDdls = buildAlterColumnDDLs(srcCol, tgtCol, schema, tableName, conn);
                    if (!colDdls.isEmpty()) {
                        ddls.addAll(colDdls);
                        alterCount++;
                    }
                }
            }

            if (ddls.isEmpty()) {
                log.info("ℹ️ 无需变更，表结构已一致");
                return;
            }

            // 2. 逐条执行（autoCommit=true，单条失败不影响其它）
            int ok = 0, failed = 0;
            try (Statement stmt = conn.createStatement()) {
                conn.setAutoCommit(true);
                for (String ddl : ddls) {
                    log.info("     DDL: {}", ddl);
                    try {
                        stmt.execute(ddl);
                        ok++;
                    } catch (java.sql.SQLException ex) {
                        log.error("  ❌ DDL 执行失败: {} —— {}", ddl, ex.getMessage());
                        failed++;
                    }
                }
            }
            log.info("✅ 表结构同步完成: 新增 {} 列 / 修改 {} 列 / 删除 {} 列 | 执行成功 {} 条, 失败 {} 条",
                    addCount, alterCount, dropCount, ok, failed);
        } catch (java.sql.SQLException e) {
            log.error("❌ 获取目标连接失败: {}", e.getMessage());
            throw new RuntimeException("DDL执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查目标表指定列是否存在 NULL 值（用于判断是否需要删除重建而非 SET NOT NULL）。
     */
    private boolean columnHasNull(Connection conn, String schema, String tableName, String columnName) {
        String sql = "SELECT 1 FROM " + SqlIdentifierUtils.qualify(schema, tableName)
                + " WHERE " + SqlIdentifierUtils.quote(columnName) + " IS NULL LIMIT 1";
        try (Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        } catch (java.sql.SQLException e) {
            log.warn("  检查列 [{}] NULL 值失败: {}", columnName, e.getMessage());
            return true; // 保守：检查失败视为有 NULL，跳过 SET NOT NULL
        }
    }
    
    /**
     * 构建 ADD COLUMN DDL（含类型、默认值、NOT NULL，与源列完全一致）。
     * 注意：若表已有数据且源列 NOT NULL 但无默认值，ADD 会失败（无法填充已有行），
     * 这种情况由 buildAlterColumnDDLs 的删除重建分支处理（先 DROP 再 ADD）。
     */
    private String buildAddColumnDDL(ColumnMetadata column, String schema, String tableName) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("ALTER TABLE ").append(SqlIdentifierUtils.qualify(schema, tableName))
           .append(" ADD COLUMN ").append(SqlIdentifierUtils.quote(column.getName()));

        String typeName = mapJdbcTypeToSQLType(column);
        ddl.append(" ").append(typeName);

        boolean hasDefault = !column.isIdentity() && column.isHasDefault() && column.getDefaultExpr() != null;
        if (column.isIdentity()) {
            ddl.append(" GENERATED AS IDENTITY");
        } else if (hasDefault) {
            ddl.append(" DEFAULT ").append(column.getDefaultExpr());
        }

        // 与源列一致：源列 NOT NULL → 新列也 NOT NULL
        if (!column.isNullable()) {
            ddl.append(" NOT NULL");
        }

        return ddl.toString();
    }
    
    /**
     * 构建 DROP COLUMN DDL
     */
    private String buildDropColumnDDL(ColumnMetadata column, String schema, String tableName) {
        return String.format(
            "ALTER TABLE %s DROP COLUMN %s",
            SqlIdentifierUtils.qualify(schema, tableName),
            SqlIdentifierUtils.quote(column.getName())
        );
    }
    
    /**
     * 构建字段变更 DDL 列表（对齐源表）：
     * 0. 源列 NOT NULL 而目标列可空 → 若目标列存在 NULL 值，删除重建（跳过后续 ALTER，数据由同步灌入）
     * 1. 类型不同 → ALTER COLUMN ... TYPE ... USING
     * 2. 可空性不同 → SET / DROP NOT NULL（方向以源为准）
     * 3. 默认值不同 → SET / DROP DEFAULT
     */
    private List<String> buildAlterColumnDDLs(ColumnMetadata sourceCol, ColumnMetadata targetCol,
                                              String schema, String tableName, Connection conn) {
        List<String> ddls = new ArrayList<>();
        String qualified = SqlIdentifierUtils.qualify(schema, tableName);
        String col = SqlIdentifierUtils.quote(sourceCol.getName());
        String sourceType = mapJdbcTypeToSQLType(sourceCol);
        String targetType = mapJdbcTypeToSQLType(targetCol);

        // 0. 源列 NOT NULL 而目标列可空：若目标列存在 NULL 值 → 删除重建（无法 SET NOT NULL）
        if (!sourceCol.isNullable() && targetCol.isNullable()) {
            if (columnHasNull(conn, schema, tableName, sourceCol.getName())) {
                log.warn("  ⚠️ 列 [{}] 存在 NULL 值，删除重建以对齐源表 NOT NULL 约束", sourceCol.getName());
                return buildRebuildColumnDDLs(sourceCol, schema, tableName);
            }
        }

        // 1. 类型不同 → 改类型（USING 显式转换，避免隐式转换失败）
        if (!sourceType.equals(targetType)) {
            ddls.add("ALTER TABLE " + qualified + " ALTER COLUMN " + col
                    + " TYPE " + sourceType + " USING " + col + "::" + sourceType);
        }

        // 2. 可空性对齐源表（方向以源为准：源可空而目标 NOT NULL → DROP NOT NULL 放宽）
        if (sourceCol.isNullable() != targetCol.isNullable()) {
            if (sourceCol.isNullable()) {
                ddls.add("ALTER TABLE " + qualified + " ALTER COLUMN " + col + " DROP NOT NULL");
            } else {
                ddls.add("ALTER TABLE " + qualified + " ALTER COLUMN " + col + " SET NOT NULL");
            }
        }

        // 3. 默认值对齐源表
        boolean srcSeq = sourceCol.getDefaultExpr() != null && sourceCol.getDefaultExpr().startsWith("nextval(");
        boolean tgtSeq = targetCol.getDefaultExpr() != null && targetCol.getDefaultExpr().startsWith("nextval(");
        boolean defaultDiff = sourceCol.isHasDefault() != targetCol.isHasDefault()
                || (sourceCol.isHasDefault() && targetCol.isHasDefault()
                    && !Objects.equals(sourceCol.getDefaultExpr(), targetCol.getDefaultExpr())
                    && !(srcSeq && tgtSeq));
        if (defaultDiff) {
            if (sourceCol.isHasDefault() && sourceCol.getDefaultExpr() != null && !srcSeq) {
                ddls.add("ALTER TABLE " + qualified + " ALTER COLUMN " + col
                        + " SET DEFAULT " + sourceCol.getDefaultExpr());
            } else if (!sourceCol.isHasDefault() && targetCol.isHasDefault()) {
                ddls.add("ALTER TABLE " + qualified + " ALTER COLUMN " + col + " DROP DEFAULT");
            }
        }

        return ddls;
    }

    /**
     * 构建删除重建列的 DDL 列表（DROP COLUMN + ADD COLUMN，ADD 含完整源定义）。
     * 用于：源列 NOT NULL 但目标列存在 NULL 值，无法直接 SET NOT NULL 的场景。
     * 注意：会丢失该列现有数据，需由后续数据同步重新灌入。
     */
    private List<String> buildRebuildColumnDDLs(ColumnMetadata sourceCol, String schema, String tableName) {
        List<String> ddls = new ArrayList<>();
        ddls.add(buildDropColumnDDL(sourceCol, schema, tableName));
        ddls.add(buildAddColumnDDL(sourceCol, schema, tableName));
        return ddls;
    }
    
    /**
     * 将 JDBC 类型映射为 SQL 类型字符串
     */
    private String mapJdbcTypeToSQLType(ColumnMetadata column) {
        String pgTypeName = column.getPgTypeName();
        if (pgTypeName != null && !pgTypeName.equals("?")) {
            return pgTypeName.toUpperCase();
        }
        
        return switch (column.getJdbcType()) {
            case java.sql.Types.BIGINT -> "BIGINT";
            case java.sql.Types.INTEGER -> "INTEGER";
            case java.sql.Types.SMALLINT -> "SMALLINT";
            case java.sql.Types.NUMERIC, java.sql.Types.DECIMAL -> "NUMERIC(38,0)";
            case java.sql.Types.VARCHAR -> "VARCHAR(255)";
            case java.sql.Types.CHAR -> "CHAR(1)";
            case java.sql.Types.LONGVARCHAR -> "TEXT";
            case java.sql.Types.TIMESTAMP -> "TIMESTAMP";
            case java.sql.Types.TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMPTZ";
            case java.sql.Types.DATE -> "DATE";
            case java.sql.Types.TIME -> "TIME";
            case java.sql.Types.BOOLEAN -> "BOOLEAN";
            case java.sql.Types.BIT -> "BIT";
            case java.sql.Types.BINARY, java.sql.Types.VARBINARY, java.sql.Types.LONGVARBINARY -> "BYTEA";
            case java.sql.Types.OTHER -> "JSONB";
            case java.sql.Types.REAL, java.sql.Types.FLOAT, java.sql.Types.DOUBLE -> "DOUBLE PRECISION";
            default -> "TEXT";
        };
    }
}
