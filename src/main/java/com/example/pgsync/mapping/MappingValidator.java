package com.example.pgsync.mapping;

import com.example.pgsync.exception.SyncValidationException;

/**
 * 字段映射结果校验
 */
public class MappingValidator {

    public static void validate(ColumnMapping mapping) {
        if (!mapping.getTargetOnlyBlocking().isEmpty()) {
            throw new SyncValidationException(
                    "目标表存在 NOT NULL 且无默认值的独有字段，源表无法提供值，无法同步: "
                            + String.join(", ", mapping.getTargetOnlyBlocking()));
        }
        if (mapping.getSyncColumns().isEmpty()) {
            throw new SyncValidationException("无共同可同步字段");
        }
    }
}
