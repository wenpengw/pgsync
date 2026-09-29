package com.example.pgsync.metadata.model;

import java.sql.Types;

/**
 * 列元数据
 */
public class ColumnMetadata {
    private final String name;            // 原始名（保留大小写）
    private final int ordinalPosition;    // 列序
    private int jdbcType;                  // java.sql.Types
    private String pgTypeName;            // int8 / varchar / jsonb / uuid / _text ...
    private boolean nullable;
    private boolean hasDefault;           // 含 identity / serial / generated
    private String defaultExpr;
    private boolean identity;             // GENERATED AS IDENTITY
    private boolean generated;            // GENERATED ALWAYS AS (...) STORED

    public ColumnMetadata(String name, int ordinalPosition) {
        this.name = name;
        this.ordinalPosition = ordinalPosition;
    }

    public String getName() {
        return name;
    }

    /** 小写归一化名称，用于跨库比较 */
    public String lowerName() {
        return name.toLowerCase();
    }

    public int getOrdinalPosition() {
        return ordinalPosition;
    }

    public int getJdbcType() {
        return jdbcType;
    }

    public void setJdbcType(int jdbcType) {
        this.jdbcType = jdbcType;
    }

    public String getPgTypeName() {
        return pgTypeName;
    }

    public void setPgTypeName(String pgTypeName) {
        this.pgTypeName = pgTypeName;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public boolean isHasDefault() {
        return hasDefault;
    }

    public void setHasDefault(boolean hasDefault) {
        this.hasDefault = hasDefault;
    }

    public String getDefaultExpr() {
        return defaultExpr;
    }

    public void setDefaultExpr(String defaultExpr) {
        this.defaultExpr = defaultExpr;
    }

    public boolean isIdentity() {
        return identity;
    }

    public void setIdentity(boolean identity) {
        this.identity = identity;
    }

    public boolean isGenerated() {
        return generated;
    }

    public void setGenerated(boolean generated) {
        this.generated = generated;
    }

    /** 该列是否可被写入（生成列不可写，必须排除） */
    public boolean isWritable() {
        return !generated;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name);
        sb.append(' ').append(pgTypeName == null ? "?" : pgTypeName);
        if (!nullable) sb.append(" NOT NULL");
        if (identity) sb.append(" IDENTITY");
        if (generated) sb.append(" GENERATED");
        if (hasDefault && !identity) sb.append(" DEFAULT");
        return sb.toString();
    }

    /** java.sql.Types 转为可读名（调试用） */
    public static String typeName(int type) {
        return switch (type) {
            case Types.BIGINT -> "BIGINT";
            case Types.INTEGER -> "INTEGER";
            case Types.SMALLINT -> "SMALLINT";
            case Types.NUMERIC, Types.DECIMAL -> "NUMERIC";
            case Types.VARCHAR -> "VARCHAR";
            case Types.CHAR -> "CHAR";
            case Types.LONGVARCHAR -> "TEXT";
            case Types.TIMESTAMP -> "TIMESTAMP";
            case Types.TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMPTZ";
            case Types.DATE -> "DATE";
            case Types.TIME -> "TIME";
            case Types.BOOLEAN -> "BOOLEAN";
            case Types.BIT -> "BIT";
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> "BYTEA";
            case Types.OTHER -> "OTHER";
            case Types.REAL, Types.FLOAT, Types.DOUBLE -> "FLOAT8";
            default -> "TYPE(" + type + ")";
        };
    }
}
