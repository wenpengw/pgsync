package com.example.pgsync;

import com.example.pgsync.config.SyncProperties;
import com.example.pgsync.report.SyncReport;
import com.example.pgsync.service.SyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * 测试类触发（快速调试）
 * 运行方式：直接执行对应测试方法，或取消注释 analyzeAll 先只看结构
 */
@SpringBootTest
class SyncManualTest {

    @Autowired
    private SyncService syncService;

    @Autowired
    private SyncProperties props;

    /** 仅分析表结构（强烈建议先跑这个看字段差异） */
    @Test
    void analyzeOnly() {
        syncService.analyzeTables(props.getTables());
    }

    /** 根据 analyzeOnly 分析结果，自动同步目标表结构 */
    @Test
    void syncStructureFromAnalysis() {
        List<String> tables = props.getTables();
        int total = 0, modified = 0, created = 0;
        
        for (String tableName : tables) {
            total++;
            try {
                if (syncService.syncTableStructure(props.getSchema(), tableName)) {
                    modified++;
                } else {
                    created++;
                }
            } catch (Exception e) {
                System.err.println("❌ " + tableName + " 失败: " + e.getMessage());
            }
        }
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 结构同步统计");
        System.out.println("  总表数:   " + total);
        System.out.println("  修改表:   " + modified);
        System.out.println("  新建表:   " + created);
        System.out.println("=".repeat(60));
    }

    /** 同步配置文件中的全部表（只同步 sync-tables.yml 的 pgsync.tables 清单，不扫描全库） */
    @Test
    void syncConfiguredTables() {
        SyncReport report = syncService.syncTables(props.getTables());
        System.out.println("成功: " + report.successCount() + " 失败: " + report.failCount());
    }

    /**
     * 清空目标表原始数据（清单来自 clear-tables.yml 的 pgsync.clear.tables）。
     * 只删目标库：不读源库、不改表结构；DELETE / TRUNCATE 由 full-overwrite.clear-strategy 决定。
     * 注意：删除不可恢复，执行前请确认这些表后续会被重新同步。
     */
    @Test
    void clearTargetTables() {
        List<String> tables = props.getClear().getTables();
        System.out.println("待清空目标表数量: " + tables.size() + "（清单来自 clear-tables.yml）");
        if (tables.isEmpty()) {
            System.out.println("clear-tables.yml 的 pgsync.clear.tables 为空，未执行任何操作");
            return;
        }
        SyncReport report = syncService.clearTargetTables(tables);
        System.out.println("清空完成: 成功 " + report.successCount() + " 张 / 失败 " + report.failCount() + " 张");
    }
    

    
}
