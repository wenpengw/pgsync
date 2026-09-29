package com.example.pgsync.trigger;

import com.example.pgsync.config.SyncProperties;
import com.example.pgsync.service.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main 方法触发：java -jar pgsync.jar --pgsync.run=all
 * java -jar pgsync.jar --pgsync.run=t_user,t_order
 * java -jar pgsync.jar --pgsync.run=analyze   (仅分析)
 */
@Component
@Order(1)
public class SyncCommandLineRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(SyncCommandLineRunner.class);

    private final SyncProperties props;
    private final SyncService syncService;

    public SyncCommandLineRunner(SyncProperties props, SyncService syncService) {
        this.props = props;
        this.syncService = syncService;
    }

    @Override
    public void run(String... args) {
        String run = null;
        for (String arg : args) {
            if (arg.startsWith("--pgsync.run=")) {
                run = arg.substring("--pgsync.run=".length());
            }
        }
        if (run == null || run.isBlank()) {
            log.info("未指定 --pgsync.run 参数，跳过自动同步（可经 HTTP / 测试类触发）");
            return;
        }
        if ("analyze".equalsIgnoreCase(run)) {
            log.info("执行结构分析（只读）...");
            syncService.analyzeAll();
            return;
        }
        List<String> tables;
        if ("all".equalsIgnoreCase(run)) {
            tables = props.getTables();
        } else {
            tables = Arrays.stream(run.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        }
        log.info("执行同步: {}", tables);
        syncService.syncTables(tables);
    }
}
