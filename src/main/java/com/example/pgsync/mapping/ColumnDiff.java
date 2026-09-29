package com.example.pgsync.mapping;

import com.example.pgsync.metadata.model.ColumnMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * 同一字段在源表 / 目标表的对比明细：
 * - 仅源表存在：目标表缺列（源有目标无）
 * - 仅目标表存在：源表缺列（目标独有）
 * - 两侧都有：记录类型 / 可空性 / 默认值等属性差异（无差异 = 完全一致）
 */
public class ColumnDiff {
    private final String name;
    private final ColumnMetadata sourceColumn;   // 仅目标表存在时为 null
    private final ColumnMetadata targetColumn;   // 仅源表存在时为 null
    private final List<String> differences = new ArrayList<>();

    public ColumnDiff(ColumnMetadata sourceColumn, ColumnMetadata targetColumn) {
        this.sourceColumn = sourceColumn;
        this.targetColumn = targetColumn;
        this.name = sourceColumn != null ? sourceColumn.getName() : targetColumn.getName();
    }

    public String getName() {
        return name;
    }

    public ColumnMetadata getSourceColumn() {
        return sourceColumn;
    }

    public ColumnMetadata getTargetColumn() {
        return targetColumn;
    }

    public List<String> getDifferences() {
        return differences;
    }

    public void addDifference(String difference) {
        differences.add(difference);
    }

    /** 仅源表存在（目标表无此列） */
    public boolean isSourceOnly() {
        return sourceColumn != null && targetColumn == null;
    }

    /** 仅目标表存在（源表无此列） */
    public boolean isTargetOnly() {
        return sourceColumn == null && targetColumn != null;
    }

    /** 两侧字段完全一致（无单边、无属性差异） */
    public boolean isSame() {
        return !isSourceOnly() && !isTargetOnly() && differences.isEmpty();
    }

    /**
     * 人类可读列描述：类型 + 约束
     * 例如: varchar(50) NOT NULL DEFAULT 'a'::character varying
     */
    public static String describe(ColumnMetadata col) {
        if (col == null) {
            return "(不存在)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(col.getPgTypeName() == null ? "?" : col.getPgTypeName());
        sb.append(col.isNullable() ? "" : " NOT NULL");
        if (col.isIdentity()) {
            sb.append(" IDENTITY");
        }
        if (col.isGenerated()) {
            sb.append(" GENERATED");
        } else if (col.isHasDefault()) {
            sb.append(" DEFAULT");
            if (col.getDefaultExpr() != null) {
                sb.append(" ").append(col.getDefaultExpr());
            }
        }
        return sb.toString();
    }
}