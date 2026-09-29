package com.example.pgsync.metadata;

import com.example.pgsync.config.NoPrimaryKeyStrategy;
import com.example.pgsync.exception.SyncValidationException;
import com.example.pgsync.metadata.model.ConflictKey;
import com.example.pgsync.metadata.model.KeySource;
import com.example.pgsync.metadata.model.TableMetadata;
import com.example.pgsync.metadata.model.UniqueIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 冲突键（去重依据）五级降级探测
 */
public class KeyResolver {
    private static final Logger log = LoggerFactory.getLogger(KeyResolver.class);

    private final NoPrimaryKeyStrategy fallbackStrategy;

    public KeyResolver(NoPrimaryKeyStrategy fallbackStrategy) {
        this.fallbackStrategy = fallbackStrategy;
    }

    /**
     * @param sourceMeta 源表元数据
     * @param targetMeta 目标表元数据
     * @param syncColumns 实际同步字段（目标列名集合）
     * @param fallbackKeys 配置的备用唯一键（可为 null）
     * @param requestedMode 请求的同步模式（UPSERT_MERGE / FULL_OVERWRITE）
     */
    public ConflictKey resolve(TableMetadata sourceMeta, TableMetadata targetMeta,
                               List<String> syncColumns, List<String> fallbackKeys,
                               boolean fullOverwriteRequested) {
        List<String> syncLower = syncColumns.stream().map(String::toLowerCase).toList();

        // L1: 目标表主键
        if (targetMeta.hasPrimaryKey()) {
            List<String> pk = targetMeta.getPrimaryKeys();
            if (allInSync(pk, syncLower)) {
                return ConflictKey.upsertable(pk, KeySource.TARGET_PRIMARY_KEY,
                        "目标表主键 [" + String.join(",", pk) + "]");
            }
            log.warn("目标表主键 [{}] 部分列不在同步字段中，无法 upsert，尝试其他键",
                    String.join(",", pk));
        }

        // L2: 目标表唯一索引（非主键、非 partial/expression，且列全在 sync 且全 NOT NULL）
        for (UniqueIndex idx : targetMeta.getUniqueIndexes()) {
            if (idx.isPrimary()) continue;
            if (allInSync(idx.getColumns(), syncLower) && idx.allNotNull(targetMeta::getColumn)) {
                return ConflictKey.upsertable(idx.getColumns(), KeySource.TARGET_UNIQUE_INDEX,
                        "目标表唯一索引 [" + idx.getIndexName() + "] (" + String.join(",", idx.getColumns()) + ")");
            }
        }

        // L3: 源表主键，且在目标表存在唯一约束
        if (sourceMeta.hasPrimaryKey()) {
            List<String> spk = sourceMeta.getPrimaryKeys();
            if (allInSync(spk, syncLower) && hasUniqueConstraint(targetMeta, spk)) {
                return ConflictKey.upsertable(spk, KeySource.SOURCE_PK_MATCHED,
                        "源表主键 [" + String.join(",", spk) + "] 在目标表有唯一约束");
            }
        }

        // L4: 配置的备用键（且目标表有对应唯一约束）
        if (fallbackKeys != null && !fallbackKeys.isEmpty()) {
            if (allInSync(fallbackKeys, syncLower) && hasUniqueConstraint(targetMeta, fallbackKeys)) {
                return ConflictKey.upsertable(fallbackKeys, KeySource.CONFIGURED_FALLBACK,
                        "配置备用键 [" + String.join(",", fallbackKeys) + "]");
            } else {
                log.warn("配置的备用键 [{}] 在目标表无唯一约束或不在同步字段，不可用",
                        String.join(",", fallbackKeys));
            }
        }

        // L5: 无键降级
        return resolveNoKey(fullOverwriteRequested);
    }

    private ConflictKey resolveNoKey(boolean fullOverwriteRequested) {
        // FULL_OVERWRITE 模式不需要冲突键：先清空目标表再整体 INSERT，
        // 因此无主键表在该模式下不视为错误，不再受 no-primary-key-strategy 影响。
        if (fullOverwriteRequested) {
            return ConflictKey.none(KeySource.NONE_FULL_OVERWRITE,
                    "无主键/唯一键，FULL_OVERWRITE 模式：清空目标表后全量 INSERT（无需冲突键）");
        }
        // 以下仅适用于 UPSERT_MERGE：必须有冲突键才能去重
        return switch (fallbackStrategy) {
            case AUTO_FULL_OVERWRITE -> throw new SyncValidationException(
                    "表无主键且无可用唯一键，UPSERT_MERGE 模式无法进行 upsert。"
                            + "请将 mode 设为 FULL_OVERWRITE（清表后全量 INSERT）");
            case APPEND_ONLY -> ConflictKey.none(KeySource.NONE_APPEND_ONLY,
                    "无主键/唯一键，按纯 INSERT 追加（可能产生重复数据，APPEND_ONLY）");
            case FAIL -> throw new SyncValidationException(
                    "表无主键且无可用唯一键，且 no-primary-key-strategy=FAIL，本表跳过");
        };
    }

    private boolean allInSync(List<String> cols, List<String> syncLower) {
        return cols.stream().allMatch(c -> syncLower.contains(c.toLowerCase()));
    }

    private boolean hasUniqueConstraint(TableMetadata targetMeta, List<String> cols) {
        return targetMeta.getUniqueIndexes().stream()
                .filter(idx -> !idx.isPrimary() || true)
                .anyMatch(idx -> sameColumns(idx.getColumns(), cols));
    }

    private boolean sameColumns(List<String> a, List<String> b) {
        if (a.size() != b.size()) return false;
        List<String> la = new ArrayList<>(a.stream().map(String::toLowerCase).sorted().toList());
        List<String> lb = new ArrayList<>(b.stream().map(String::toLowerCase).sorted().toList());
        return la.equals(lb);
    }
}
