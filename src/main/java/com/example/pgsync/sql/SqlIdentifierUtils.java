package com.example.pgsync.sql;

/**
 * SQL 标识符工具：双引号包裹，防止大小写 / 关键字冲突
 */
public class SqlIdentifierUtils {
    public static String quote(String identifier) {
        if (identifier == null) return null;
        // 去除已有的引号，避免重复
        String clean = identifier.replace("\"", "");
        return "\"" + clean + "\"";
    }

    public static String qualify(String schema, String table) {
        return quote(schema) + "." + quote(table);
    }
}
