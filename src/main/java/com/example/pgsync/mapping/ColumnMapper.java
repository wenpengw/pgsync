package com.example.pgsync.mapping;

import com.example.pgsync.exception.SyncValidationException;
import com.example.pgsync.metadata.model.ColumnMetadata;
import com.example.pgsync.metadata.model.TableMetadata;

import java.util.List;

/**
 * 字段映射：计算源表/目标表字段交集，并校验目标表独有字段兼容性
 */
public class ColumnMapper {
    private final TypeCompatibilityChecker typeChecker = new TypeCompatibilityChecker();

    /**
     * @param sourceMeta 源表元数据
     * @param targetMeta 目标表元数据
     * @return 字段映射结果
     */
    public ColumnMapping map(TableMetadata sourceMeta, TableMetadata targetMeta) {
        ColumnMapping mapping = new ColumnMapping();

        if (!targetMeta.isExists()) {
            throw new SyncValidationException(
                    "目标表 " + targetMeta.getSchema() + "." + targetMeta.getTableName() + " 不存在");
        }

        // 目标表可写列（剔除生成列），按列序遍历，保证 INSERT 列序正确
        List<ColumnMetadata> targetWritable = targetMeta.writableColumns();

        // 跳过两边都是生成列的字段
        for (ColumnMetadata tc : targetWritable) {
            ColumnMetadata sc = sourceMeta.getColumn(tc.getName());
            if (sc != null && sc.isGenerated()) {
                // 目标普通列、源是生成列 → 源无法提供值，但目标可自动填充，按目标独有处理
            }
        }

        for (ColumnMetadata tc : targetWritable) {
            ColumnMetadata sc = sourceMeta.getColumn(tc.getName());
            if (sc == null) {
                // 目标独有字段
                handleTargetOnly(tc, mapping);
            } else if (!sc.isWritable()) {
                // 源是生成列，目标普通列 → 目标按独有处理（自动填充）
                handleTargetOnly(tc, mapping);
            } else {
                // 两边都存在且可写 → 同步字段
                SyncColumn scCol = new SyncColumn(sc, tc);
                mapping.getSyncColumns().add(scCol);
                CompatibilityLevel level = typeChecker.check(sc, tc);
                if (level == CompatibilityLevel.INCOMPATIBLE) {
                    throw new SyncValidationException(String.format(
                            "字段类型不兼容: %s 源[%s] -> 目标[%s]，无法同步",
                            tc.getName(), sc, tc));
                } else if (level == CompatibilityLevel.RISKY) {
                    mapping.getTypeWarnings().add(String.format(
                            "%s 源[%s] -> 目标[%s] 可能截断/溢出",
                            tc.getName(), sc.getPgTypeName(), tc.getPgTypeName()));
                }
            }
        }

        // 收集源表有而目标表无的字段（忽略）
        for (ColumnMetadata sc : sourceMeta.writableColumns()) {
            if (!targetMeta.hasColumn(sc.getName())) {
                mapping.getIgnoredSourceColumns().add(sc.getName());
            }
        }

        if (mapping.getSyncColumns().isEmpty()) {
            throw new SyncValidationException(
                    "源表与目标表无共同可同步字段: " + sourceMeta.getTableName());
        }
        return mapping;
    }

    private void handleTargetOnly(ColumnMetadata tc, ColumnMapping mapping) {
        if (tc.isGenerated()) {
            // 目标生成列，跳过（INSERT 不写）
            mapping.getSkippedGeneratedColumns().add(tc.getName());
            return;
        }
        if (tc.isNullable() || tc.isHasDefault() || tc.isIdentity()) {
            mapping.getTargetOnlyFillable().add(tc.getName());
        } else {
            mapping.getTargetOnlyBlocking().add(tc.getName());
        }
    }
}
