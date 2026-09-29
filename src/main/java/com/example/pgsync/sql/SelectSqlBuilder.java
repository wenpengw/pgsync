package com.example.pgsync.sql;

import com.example.pgsync.mapping.ColumnMapping;

import java.util.stream.Collectors;

/**
 * SELECT 语句构建（显式列，不用 SELECT *）
 */
public class SelectSqlBuilder {

    public static String build(String schema, String table, ColumnMapping mapping, Integer limit) {
        String cols = mapping.getSyncColumns().stream()
                .map(sc -> SqlIdentifierUtils.quote(sc.getSourceName()))
                .collect(Collectors.joining(", "));
        String sql = "SELECT " + cols + " FROM "
                + SqlIdentifierUtils.qualify(schema, table);
        if (limit != null && limit > 0) {
            sql += " LIMIT " + limit;
        }
        return sql;
    }
}
