package com.example.pgsync.mapping;

import com.example.pgsync.metadata.model.ColumnMetadata;

import java.util.Set;

/**
 * 跨库类型兼容性检查（基于 PG 类型名 + jdbc 类族）
 */
public class TypeCompatibilityChecker {
    // 数值类族
    private static final Set<String> NUMERIC = Set.of("int2", "int4", "int8", "numeric", "decimal", "float4", "float8", "smallint", "integer", "bigint", "real", "double precision");
    // 字符类族
    private static final Set<String> STRING = Set.of("varchar", "char", "bpchar", "text", "character varying", "name");
    // 时间类族
    private static final Set<String> TEMPORAL = Set.of("timestamp", "timestamptz", "date", "time");
    // 布尔
    private static final Set<String> BOOLEAN = Set.of("bool", "boolean");
    // 二进制
    private static final Set<String> BINARY = Set.of("bytea");

    public CompatibilityLevel check(ColumnMetadata source, ColumnMetadata target) {
        String s = normalize(source.getPgTypeName());
        String t = normalize(target.getPgTypeName());

        if (s.equals(t)) {
            return CompatibilityLevel.IDENTICAL;
        }

        String sf = family(s);
        String tf = family(t);
        if (sf == null || tf == null) {
            return CompatibilityLevel.INCOMPATIBLE;  // 未知类型直接判为不兼容，保守
        }
        if (!sf.equals(tf)) {
            return CompatibilityLevel.INCOMPATIBLE;
        }

        // 同类族内进一步判定风险
        return switch (sf) {
            case "NUMERIC" -> riskyNumeric(s, t);
            case "STRING" -> riskyString(s, t);
            case "TEMPORAL" -> CompatibilityLevel.COMPATIBLE;
            case "BOOLEAN" -> CompatibilityLevel.IDENTICAL;
            case "BINARY" -> CompatibilityLevel.COMPATIBLE;
            default -> CompatibilityLevel.COMPATIBLE;
        };
    }

    private CompatibilityLevel riskyNumeric(String s, String t) {
        int ps = precisionRank(s);
        int pt = precisionRank(t);
        if (ps == pt) return CompatibilityLevel.IDENTICAL;
        return ps <= pt ? CompatibilityLevel.COMPATIBLE : CompatibilityLevel.RISKY;
    }

    private CompatibilityLevel riskyString(String s, String t) {
        int ps = lengthRank(s);
        int pt = lengthRank(t);
        if (ps == pt) return CompatibilityLevel.IDENTICAL;
        return ps <= pt ? CompatibilityLevel.COMPATIBLE : CompatibilityLevel.RISKY;
    }

    private int precisionRank(String t) {
        return switch (t) {
            case "int2", "smallint" -> 1;
            case "int4", "integer" -> 2;
            case "int8", "bigint" -> 3;
            case "float4", "real" -> 4;
            case "float8", "double precision" -> 5;
            case "numeric", "decimal" -> 9;  // 不确定精度，按最宽处理
            default -> 2;
        };
    }

    private int lengthRank(String t) {
        if (t.equals("text") || t.equals("name")) return 9999;
        return 5;
    }

    private String family(String t) {
        if (NUMERIC.contains(t)) return "NUMERIC";
        if (STRING.contains(t)) return "STRING";
        if (TEMPORAL.contains(t)) return "TEMPORAL";
        if (BOOLEAN.contains(t)) return "BOOLEAN";
        if (BINARY.contains(t)) return "BINARY";
        return null;
    }

    private String normalize(String pgType) {
        if (pgType == null) return "";
        String t = pgType.toLowerCase().trim();
        // 去除长度/精度修饰，如 varchar(50) -> varchar
        int idx = t.indexOf('(');
        if (idx > 0) t = t.substring(0, idx);
        return t;
    }
}
