package com.example.pgsync.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 目标表清空清单（对应 clear-tables.yml 中的 pgsync.clear.tables）
 * 仅用于手工触发"清空目标表数据"，不参与数据同步流程
 */
public class ClearConfig {

    private List<String> tables = new ArrayList<>();

    public List<String> getTables() {
        return tables;
    }

    public void setTables(List<String> tables) {
        this.tables = tables;
    }
}
