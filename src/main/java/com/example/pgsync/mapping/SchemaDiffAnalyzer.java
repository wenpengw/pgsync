package com.example.pgsync.mapping;

import com.example.pgsync.metadata.model.ColumnMetadata;
import com.example.pgsync.metadata.model.TableMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表结构差异分析：逐列对比源表 / 目标表，
 * 找出「目标表不存在的字段」与「属性不同的字段」。
 *
 * 输出顺序：按目标表列序，末尾追加仅源表存在的字段。
 */
public class SchemaDiffAnalyzer {

    private final TypeCompatibilityChecker typeChecker = new TypeCompatibilityChecker();

    /**
     * 逐列对比源表与目标表。
     *
     * @param source 源表元数据
     * @param target 目标表元数据
     * @return 字段对比清单（含完全一致、仅单边存在、属性不同三类）
     */
    public List<ColumnDiff> analyze(TableMetadata source, TableMetadata target) {
        Map<String, ColumnDiff> diffs = new LinkedHashMap<>();

        // 按目标表列序遍历
        for (ColumnMetadata tc : target.getColumns()) {
            ColumnMetadata sc = source.getColumn(tc.getName());
            ColumnDiff diff = new ColumnDiff(sc, tc);
            if (sc != null) {
                compareProperties(sc, tc, diff);
            }
            diffs.put(diff.getName().toLowerCase(), diff);
        }

        // 追加仅源表存在的字段
        for (ColumnMetadata sc : source.getColumns()) {
            if (!target.hasColumn(sc.getName())) {
                diffs.put(sc.getName().toLowerCase(), new ColumnDiff(sc, null));
            }
        }
        return new ArrayList<>(diffs.values());
    }

    private void compareProperties(ColumnMetadata sc, ColumnMetadata tc, ColumnDiff diff) {
        // 1. 类型差异（兼容级别）
        CompatibilityLevel level = typeChecker.check(sc, tc);
        switch (level) {
            case INCOMPATIBLE ->
                    diff.addDifference("类型不兼容: " + sc.getPgTypeName() + " → " + tc.getPgTypeName());
            case RISKY ->
                    diff.addDifference("类型不同(可能截断/溢出): " + sc.getPgTypeName() + " → " + tc.getPgTypeName());
            case COMPATIBLE ->
                    diff.addDifference("类型不同(可兼容): " + sc.getPgTypeName() + " → " + tc.getPgTypeName());
            default -> {
                // IDENTICAL: 类型一致
            }
        }

        // 2. 可空性差异
        if (sc.isNullable() != tc.isNullable()) {
            diff.addDifference("可空性不同: 源(" + (sc.isNullable() ? "可空" : "NOT NULL")
                    + ") → 目标(" + (tc.isNullable() ? "可空" : "NOT NULL") + ")");
        }

        // 3. 默认值差异（序列默认 nextval(...) 视为一致，避免源/目标序列名不同造成误报）
        boolean scSeq = sc.getDefaultExpr() != null && sc.getDefaultExpr().startsWith("nextval(");
        boolean tcSeq = tc.getDefaultExpr() != null && tc.getDefaultExpr().startsWith("nextval(");
        boolean sizeChanged = sc.isHasDefault() != tc.isHasDefault();
        boolean exprChanged = !sizeChanged
                && sc.isHasDefault()
                && !Objects.equals(sc.getDefaultExpr(), tc.getDefaultExpr())
                && !(scSeq && tcSeq);
        if (sizeChanged || exprChanged) {
            diff.addDifference("默认值不同: 源(" + defaultDesc(sc) + ") → 目标(" + defaultDesc(tc) + ")");
        }

        // 4. 标识列 / 生成列差异
        if (sc.isIdentity() != tc.isIdentity()) {
            diff.addDifference("标识列(identity)属性不同");
        }
        if (sc.isGenerated() != tc.isGenerated()) {
            diff.addDifference("生成列(generated)属性不同");
        }
    }

    private String defaultDesc(ColumnMetadata col) {
        if (!col.isHasDefault() || col.getDefaultExpr() == null) {
            return "无";
        }
        return col.isIdentity() ? "IDENTITY" : col.getDefaultExpr();
    }
}