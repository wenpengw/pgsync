package com.example.pgsync.sql;

import com.example.pgsync.metadata.model.ConflictKey;
import com.example.pgsync.mapping.ColumnMapping;

import java.util.List;
import java.util.stream.Collectors;

/**
 * UPSERT / INSERT 语句构建
 */
public class UpsertSqlBuilder {

    /**
     * 构建 INSERT ... ON CONFLICT ... DO UPDATE 语句
     *
     * @param schema     目标 schema
     * @param table      目标表
     * @param mapping    字段映射（同步字段，按目标列序）
     * @param conflictKey 冲突键（可为空 → 纯 INSERT）
     */
    public static String buildInsert(String schema, String table, ColumnMapping mapping, ConflictKey conflictKey) {
        List<String> colNames = mapping.getSyncColumns().stream()
                .map(sc -> SqlIdentifierUtils.quote(sc.getTargetName()))
                .collect(Collectors.toList());

        String cols = String.join(", ", colNames);
        String placeholders = colNames.stream().map(c -> "?").collect(Collectors.joining(", "));

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(SqlIdentifierUtils.qualify(schema, table))
                .append(" (").append(cols).append(") VALUES (").append(placeholders).append(")");

        if (conflictKey != null && conflictKey.isUpsertable() && !conflictKey.getColumns().isEmpty()) {
            String conflictCols = conflictKey.getColumns().stream()
                    .map(SqlIdentifierUtils::quote)
                    .collect(Collectors.joining(", "));

            // DO UPDATE SET 排除冲突键列
            List<String> updateCols = colNames.stream()
                    .filter(c -> !conflictKey.getColumns().stream()
                            .anyMatch(pk -> pk.equalsIgnoreCase(stripQuotes(c))))
                    .collect(Collectors.toList());

            if (updateCols.isEmpty()) {
                // 同步字段全是冲突键 → DO NOTHING
                sb.append(" ON CONFLICT (").append(conflictCols).append(") DO NOTHING");
            } else {
                String setClause = updateCols.stream()
                        .map(c -> c + " = EXCLUDED." + c)
                        .collect(Collectors.joining(", "));
                sb.append(" ON CONFLICT (").append(conflictCols).append(") DO UPDATE SET ")
                        .append(setClause);
            }
        }
        return sb.toString();
    }

    /**
     * 构建 INSERT ... ON CONFLICT DO NOTHING 语句（已存在则跳过）
     */
    public static String buildInsertSkipExisting(String schema, String table, ColumnMapping mapping, ConflictKey conflictKey) {
        List<String> colNames = mapping.getSyncColumns().stream()
                .map(sc -> SqlIdentifierUtils.quote(sc.getTargetName()))
                .collect(Collectors.toList());

        String cols = String.join(", ", colNames);
        String placeholders = colNames.stream().map(c -> "?").collect(Collectors.joining(", "));

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(SqlIdentifierUtils.qualify(schema, table))
                .append(" (").append(cols).append(") VALUES (").append(placeholders).append(")");

        if (conflictKey != null && conflictKey.isUpsertable() && !conflictKey.getColumns().isEmpty()) {
            String conflictCols = conflictKey.getColumns().stream()
                    .map(SqlIdentifierUtils::quote)
                    .collect(Collectors.joining(", "));
            sb.append(" ON CONFLICT (").append(conflictCols).append(") DO NOTHING");
        }
        return sb.toString();
    }

    private static String stripQuotes(String s) {
        return s.replace("\"", "");
    }
}
