package com.example.pgsync.config;

import java.util.List;

/**
 * 安全约束配置
 */
public class SafetyConfig {
    private boolean ddlForbidden = true;
    private boolean sqlGuardEnabled = true;
    private List<String> allowedStatements = List.of("SELECT", "INSERT", "TRUNCATE", "UPDATE");
    private boolean failOnTargetTableMissing = true;

    public boolean isDdlForbidden() {
        return ddlForbidden;
    }

    public void setDdlForbidden(boolean ddlForbidden) {
        this.ddlForbidden = ddlForbidden;
    }

    public boolean isSqlGuardEnabled() {
        return sqlGuardEnabled;
    }

    public void setSqlGuardEnabled(boolean sqlGuardEnabled) {
        this.sqlGuardEnabled = sqlGuardEnabled;
    }

    public List<String> getAllowedStatements() {
        return allowedStatements;
    }

    public void setAllowedStatements(List<String> allowedStatements) {
        this.allowedStatements = allowedStatements;
    }

    public boolean isFailOnTargetTableMissing() {
        return failOnTargetTableMissing;
    }

    public void setFailOnTargetTableMissing(boolean failOnTargetTableMissing) {
        this.failOnTargetTableMissing = failOnTargetTableMissing;
    }
}
