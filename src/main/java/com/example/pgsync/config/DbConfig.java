package com.example.pgsync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 单数据源配置
 */
@Component
@ConfigurationProperties(prefix = "pgsync.source")
public class DbConfig {
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName = "org.postgresql.Driver";
    private java.util.Map<String, String> hikari = new java.util.HashMap<>();

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public java.util.Map<String, String> getHikari() {
        return hikari;
    }

    public void setHikari(java.util.Map<String, String> hikari) {
        this.hikari = hikari;
    }
}
