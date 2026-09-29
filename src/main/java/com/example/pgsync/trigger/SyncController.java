package com.example.pgsync.trigger;

import com.example.pgsync.report.SyncReport;
import com.example.pgsync.service.SyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * HTTP 触发入口
 */
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/all")
    public SyncReport syncAll() {
        return syncService.syncAll();
    }

    @PostMapping("/tables")
    public SyncReport syncTables(@RequestBody List<String> tables) {
        return syncService.syncTables(tables);
    }

    @GetMapping("/analyze/all")
    public Map<String, String> analyzeAll() {
        syncService.analyzeAll();
        return Map.of("status", "ok", "message", "结构分析完成，详见日志");
    }

    @GetMapping("/analyze/tables")
    public Map<String, String> analyzeTables(@RequestParam List<String> tables) {
        syncService.analyzeTables(tables);
        return Map.of("status", "ok", "message", "结构分析完成，详见日志");
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return syncService.health();
    }
}
