package com.example.pgsync.sql;

import com.example.pgsync.config.SafetyConfig;
import com.example.pgsync.exception.DdlForbiddenException;

import java.util.List;

/**
 * SQL 守卫：所有 SQL 提交前过白名单校验，禁止任何 DDL / 非白名单语句
 */
public class SqlGuard {
    private final boolean enabled;
    private final List<String> allowedStatements;

    private static final String[] FORBIDDEN_KEYWORDS = {
            "CREATE", "ALTER", "DROP", "TRUNCATE", "GRANT", "REVOKE",
            "COMMENT", "REINDEX", "VACUUM", "CLUSTER", "SET", "BEGIN",
            "COMMIT", "ROLLBACK", "SAVEPOINT", "ANALYZE"
    };

    public SqlGuard(SafetyConfig safety) {
        this.enabled = safety.isSqlGuardEnabled();
        this.allowedStatements = safety.getAllowedStatements();
    }

    public void check(String sql) {
        if (!enabled) return;
        String trimmed = sql.trim().replaceAll("\\s+", " ");
        String upper = trimmed.toUpperCase();

        // 按分号拆分，逐条校验首关键字（防止 "SELECT 1; DROP TABLE x" 这类夹带）
        for (String stmt : upper.split(";")) {
            String s = stmt.trim();
            if (s.isEmpty()) continue;
            String leading = s.split(" ", 2)[0];

            // 禁止语句检测：只看语句首关键字，避免误伤 upsert 的 "DO UPDATE SET" 等合法子句
            if (!"TRUNCATE".equals(leading) || !allowedStatements.contains("TRUNCATE")) {
                for (String kw : FORBIDDEN_KEYWORDS) {
                    if (leading.equals(kw)) {
                        throw new DdlForbiddenException("SQL 守卫拦截到禁止的操作 [" + kw + "]: " + trimmed);
                    }
                }
            }

            // 白名单前缀校验
            if (!allowedStatements.isEmpty()) {
                boolean matched = allowedStatements.stream()
                        .anyMatch(prefix -> s.startsWith(prefix));
                if (!matched) {
                    throw new DdlForbiddenException("SQL 不在允许语句白名单内 ["
                            + String.join(",", allowedStatements) + "]: " + trimmed);
                }
            }
        }
    }

    /**
     * 清空目标表专用校验：
     * - TRUNCATE：必须配置在白名单内
     * - DELETE：仅允许在全量覆盖（FULL_OVERWRITE）清表时使用，不允许出现在白名单中
     */
    public void checkClear(String sql, String strategy, boolean fullOverwrite) {
        if (!enabled) return;

        if ("DELETE".equalsIgnoreCase(strategy)) {
            if (!fullOverwrite) {
                throw new DdlForbiddenException(
                        "DELETE 仅允许在全量覆盖（FULL_OVERWRITE）模式下清空目标表时使用: " + sql);
            }
            return;   // 全量覆盖清表场景直接放行，DELETE 不进入通用白名单
        }
        // TRUNCATE 走通用白名单校验
        check(sql);
    }
}
