package com.example.pgsync.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import javax.sql.DataSource;

/**
 * 双数据源手动配置（源库只读 + 目标库可写）
 * 排除 Spring Boot 自动单数据源配置，避免冲突
 */
@Configuration
@org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
public class DataSourceConfig {

    @Bean(destroyMethod = "close")
    public DataSource sourceDataSource(SyncProperties props) {
        return buildDataSource(props.getSource(), "pgsync-source");
    }

    @Bean(destroyMethod = "close")
    public DataSource targetDataSource(SyncProperties props) {
        return buildDataSource(props.getTarget(), "pgsync-target");
    }

    private DataSource buildDataSource(DbConfig cfg, String poolName) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName(poolName);
        ds.setDriverClassName(cfg.getDriverClassName());
        ds.setJdbcUrl(cfg.getJdbcUrl());
        ds.setUsername(cfg.getUsername());
        ds.setPassword(cfg.getPassword());
        cfg.getHikari().forEach((k, v) -> applyHikariProp(ds, k, v));
        return ds;
    }

    private void applyHikariProp(HikariDataSource ds, String key, String value) {
        switch (key.toLowerCase()) {
            case "maximum-pool-size" -> ds.setMaximumPoolSize(Integer.parseInt(value));
            case "minimum-idle" -> ds.setMinimumIdle(Integer.parseInt(value));
            case "connection-timeout" -> ds.setConnectionTimeout(Long.parseLong(value));
            case "idle-timeout" -> ds.setIdleTimeout(Long.parseLong(value));
            case "max-lifetime" -> ds.setMaxLifetime(Long.parseLong(value));
            case "read-only" -> ds.setReadOnly(Boolean.parseBoolean(value));
            case "auto-commit" -> ds.setAutoCommit(Boolean.parseBoolean(value));
            default -> { /* 忽略未知属性 */ }
        }
    }
}
