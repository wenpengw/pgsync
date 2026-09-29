package com.example.pgsync.metadata;

import com.example.pgsync.exception.MetadataException;
import com.example.pgsync.metadata.model.ColumnMetadata;
import com.example.pgsync.metadata.model.ForeignKey;
import com.example.pgsync.metadata.model.TableMetadata;
import com.example.pgsync.metadata.model.UniqueIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL 元数据探测实现
 * 采用双通道：DatabaseMetaData 取基础骨架 + pg_catalog 补齐 PG 特有属性
 */
public class PgMetadataService implements MetadataService {

    private static final Logger log = LoggerFactory.getLogger(PgMetadataService.class);

    private final MetadataCache cache;

    public PgMetadataService(MetadataCache cache) {
        this.cache = cache;
    }

    @Override
    public TableMetadata load(DataSource ds, String schema, String tableName) {
        // 用 JDBC URL 构建缓存 key，避免 ds.toString() 在不同 HikariCP 版本下行为不一致
        // 导致源库/目标库元数据互相串用（目标库误带上源库的 fk_r8p0_* 扩展列）
        String key = buildCacheKey(ds, schema, tableName);
        TableMetadata cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        TableMetadata meta;
        try (Connection conn = ds.getConnection()) {
            meta = doLoad(conn, schema, tableName);
        } catch (SQLException e) {
            throw new MetadataException("加载表元数据失败: " + schema + "." + tableName + " - " + e.getMessage(), e);
        }
        cache.put(key, meta);
        log.info("[元数据] {}  共 {} 列, 主键[{}], 唯一索引[{}]",
                key, meta.getColumns().size(),
                String.join(",", meta.getPrimaryKeys()),
                meta.getUniqueIndexes().stream()
                        .map(ui -> String.join("+", ui.getColumns())).reduce((a, b) -> a + "; " + b).orElse("-"));
        return meta;
    }

    private String buildCacheKey(DataSource ds, String schema, String tableName) {
        String url;
        try (Connection conn = ds.getConnection()) {
            url = conn.getMetaData().getURL();
        } catch (SQLException e) {
            // fallback：identityHashCode 保证不同 DataSource 实例不会碰撞
            url = "ds@" + System.identityHashCode(ds);
        }
        return url + ":" + schema + ":" + tableName;
    }

    private TableMetadata doLoad(Connection conn, String schema, String tableName) {
        TableMetadata meta = new TableMetadata(schema, tableName);
        String realSchema = resolveSchema(conn, schema);
        String realTable = resolveTable(conn, realSchema, tableName);
        if (realTable == null) {
            meta.setExists(false);
            return meta;
        }
        meta.setExists(true);
        meta.setSchema(realSchema);
        meta.setTableName(realTable);

        loadColumnsViaPgCatalog(conn, realSchema, realTable, meta);
        loadPrimaryKeysViaMeta(conn, realSchema, realTable, meta);
        loadUniqueIndexesViaPgCatalog(conn, realSchema, realTable, meta);
        loadForeignKeysViaPgCatalog(conn, realSchema, realTable, meta);
        return meta;
    }

    /** 表名大小写回退：原样 → 小写 → 大写 */
    private String resolveTable(Connection conn, String schema, String tableName) {
        if (tableExistsRaw(conn, schema, tableName)) return tableName;
        String lower = tableName.toLowerCase();
        if (!lower.equals(tableName) && tableExistsRaw(conn, schema, lower)) return lower;
        String upper = tableName.toUpperCase();
        if (!upper.equals(tableName) && tableExistsRaw(conn, schema, upper)) return upper;
        return null;
    }

    private String resolveSchema(Connection conn, String schema) {
        try (ResultSet rs = conn.getMetaData().getSchemas(null, schema)) {
            if (rs.next()) return schema;
        } catch (SQLException ignored) {
        }
        String lower = schema.toLowerCase();
        try (ResultSet rs = conn.getMetaData().getSchemas(null, lower)) {
            if (rs.next()) return lower;
        } catch (SQLException ignored) {
        }
        return schema;
    }

    @Override
    public boolean tableExists(Connection conn, String schema, String tableName) {
        String realSchema = resolveSchema(conn, schema);
        return resolveTable(conn, realSchema, tableName) != null;
    }

    private boolean tableExistsRaw(Connection conn, String schema, String tableName) {
        try (ResultSet rs = conn.getMetaData().getTables(null, schema, tableName, new String[]{"TABLE"})) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    /** 通道 A：基础列信息（DatabaseMetaData） */
    private void loadColumnsViaMeta(Connection conn, String schema, String table, TableMetadata meta) {
        try (ResultSet rs = conn.getMetaData().getColumns(null, schema, table, "%")) {
            Map<Integer, ColumnMetadata> byPos = new LinkedHashMap<>();
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                int pos = rs.getInt("ORDINAL_POSITION");
                ColumnMetadata col = new ColumnMetadata(name, pos);
                col.setJdbcType(rs.getInt("DATA_TYPE"));
                col.setNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                String def = rs.getString("COLUMN_DEF");
                col.setHasDefault(def != null);
                col.setDefaultExpr(def);
                byPos.put(pos, col);
            }
            // 按列序补入
            byPos.keySet().stream().sorted().forEach(p -> meta.addColumn(byPos.get(p)));
        } catch (SQLException e) {
            throw new MetadataException("读取列失败: " + schema + "." + table, e);
        }
    }

    /** 通道 B：pg_catalog 补齐 pgTypeName / identity / generated 等 PG 特有属性 */
    private void loadColumnsViaPgCatalog(Connection conn, String schema, String table, TableMetadata meta) {
        // 先用基础通道拿到列骨架
        loadColumnsViaMeta(conn, schema, table, meta);
        String sql = """
                SELECT a.attname AS col_name,
                       format_type(a.atttypid, a.atttypmod) AS full_type,
                       t.typname AS pg_type,
                       a.attnotnull,
                       a.atthasdef,
                       a.attidentity,
                       a.attgenerated,
                       pg_get_expr(d.adbin, d.adrelid) AS default_expr
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_type t ON t.oid = a.atttypid
                LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
                WHERE n.nspname = ? AND c.relname = ?
                  AND a.attnum > 0 AND NOT a.attisdropped
                ORDER BY a.attnum
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString("col_name");
                    ColumnMetadata col = meta.getColumn(colName);
                    if (col == null) continue;
                    String pgType = rs.getString("pg_type");
                    col.setPgTypeName(pgType);
                    col.setNullable(!rs.getBoolean("attnotnull"));
                    col.setHasDefault(rs.getBoolean("atthasdef"));
                    col.setDefaultExpr(rs.getString("default_expr"));
                    String ident = rs.getString("attidentity");
                    col.setIdentity(ident != null && !ident.isEmpty());
                    String gen = rs.getString("attgenerated");
                    col.setGenerated(gen != null && !gen.isEmpty());
                    // 补充 jdbcType：用 pg_type 推断，避免 DatabaseMetaData 在某些驱动下偏差
                    int jdbcType = mapPgTypeToJdbc(pgType);
                    if (jdbcType != Types.OTHER) {
                        col.setJdbcType(jdbcType);
                    }
                }
            }
        } catch (SQLException e) {
            // pg_catalog 失败不致命，退回基础通道的结果
            log.warn("pg_catalog 列属性补齐失败（已退回基础通道）: {} - {}", schema + "." + table, e.getMessage());
        }
    }

    private void loadPrimaryKeysViaMeta(Connection conn, String schema, String table, TableMetadata meta) {
        try (ResultSet rs = conn.getMetaData().getPrimaryKeys(null, schema, table)) {
            Map<Short, String> pkMap = new LinkedHashMap<>();
            while (rs.next()) {
                short seq = rs.getShort("KEY_SEQ");
                pkMap.put(seq, rs.getString("COLUMN_NAME"));
            }
            pkMap.keySet().stream().sorted().forEach(s -> meta.getPrimaryKeys().add(pkMap.get(s)));
        } catch (SQLException e) {
            throw new MetadataException("读取主键失败: " + schema + "." + table, e);
        }
    }

    private void loadUniqueIndexesViaPgCatalog(Connection conn, String schema, String table, TableMetadata meta) {
        String sql = """
                SELECT i.relname AS index_name,
                       ix.indisprimary AS is_primary,
                       ix.indkey AS indkey,
                       ARRAY(SELECT a.attname FROM pg_attribute a
                             WHERE a.attrelid = ix.indrelid AND a.attnum = ANY(ix.indkey)
                             ORDER BY array_position(ix.indkey, a.attnum)) AS cols
                FROM pg_index ix
                JOIN pg_class c ON c.oid = ix.indrelid
                JOIN pg_class i ON i.oid = ix.indexrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relname = ? AND ix.indisunique = true
                  AND ix.indpred IS NULL AND ix.indexprs IS NULL
                ORDER BY ix.indisprimary DESC, i.relname
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String idxName = rs.getString("index_name");
                    boolean isPrimary = rs.getBoolean("is_primary");
                    String colsStr = rs.getString("cols");   // {a,b,c}
                    List<String> cols = parseArray(colsStr);
                    meta.addUniqueIndex(new UniqueIndex(idxName, cols, isPrimary));
                }
            }
        } catch (SQLException e) {
            log.warn("唯一索引探测失败（不影响基本同步）: {} - {}", schema + "." + table, e.getMessage());
        }
    }

    private void loadForeignKeysViaPgCatalog(Connection conn, String schema, String table, TableMetadata meta) {
        String sql = """
                SELECT con.conname AS fk_name,
                       ARRAY(SELECT a.attname FROM pg_attribute a
                             WHERE a.attrelid = con.conrelid AND a.attnum = ANY(con.conkey)
                             ORDER BY array_position(con.conkey, a.attnum)) AS fk_cols,
                       ref.relname AS ref_table
                FROM pg_constraint con
                JOIN pg_class c ON c.oid = con.conrelid
                JOIN pg_class ref ON ref.oid = con.confrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relname = ? AND con.contype = 'f'
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String fkName = rs.getString("fk_name");
                    List<String> cols = parseArray(rs.getString("fk_cols"));
                    String refTable = rs.getString("ref_table");
                    meta.addForeignKey(new ForeignKey(fkName, cols, refTable));
                }
            }
        } catch (SQLException e) {
            log.warn("外键探测失败（仅提示用）: {} - {}", schema + "." + table, e.getMessage());
        }
    }

    private List<String> parseArray(String pgArray) {
        List<String> result = new ArrayList<>();
        if (pgArray == null) return result;
        String inner = pgArray.trim();
        if (inner.startsWith("{")) inner = inner.substring(1);
        if (inner.endsWith("}")) inner = inner.substring(0, inner.length() - 1);
        if (inner.isEmpty()) return result;
        for (String part : inner.split(",")) {
            String trimmed = part.trim().replace("\"", "");
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    /** pg 类型名 → java.sql.Types（仅覆盖常见类型，未知返回 OTHER） */
    private int mapPgTypeToJdbc(String pgType) {
        if (pgType == null) return Types.OTHER;
        return switch (pgType) {
            case "int8", "bigint" -> Types.BIGINT;
            case "int4", "integer" -> Types.INTEGER;
            case "int2", "smallint" -> Types.SMALLINT;
            case "numeric", "decimal" -> Types.NUMERIC;
            case "varchar", "character varying" -> Types.VARCHAR;
            case "bpchar", "char" -> Types.CHAR;
            case "text" -> Types.LONGVARCHAR;
            case "timestamp", "timestamptz" -> Types.TIMESTAMP;
            case "date" -> Types.DATE;
            case "time" -> Types.TIME;
            case "bool", "boolean" -> Types.BOOLEAN;
            case "bytea" -> Types.BINARY;
            case "uuid" -> Types.OTHER;
            case "json", "jsonb" -> Types.OTHER;
            case "float4", "real" -> Types.REAL;
            case "float8", "double precision" -> Types.DOUBLE;
            default -> {
                if (pgType.startsWith("_")) yield Types.OTHER;  // 数组
                yield Types.OTHER;
            }
        };
    }
}
