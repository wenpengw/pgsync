package com.example.pgsync.config;

import com.example.pgsync.metadata.MetadataCache;
import com.example.pgsync.metadata.MetadataService;
import com.example.pgsync.metadata.PgMetadataService;
import com.example.pgsync.sql.SqlGuard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 业务 Bean 装配
 */
@Configuration
public class BeanConfig {

    @Bean
    public MetadataCache metadataCache() {
        return new MetadataCache();
    }

    @Bean
    public MetadataService metadataService(MetadataCache cache) {
        return new PgMetadataService(cache);
    }

    @Bean
    public SqlGuard sqlGuard(SyncProperties props) {
        SqlGuard guard = new SqlGuard(props.getSafety());
        if (!props.getSafety().isDdlForbidden()) {
            throw new IllegalStateException(
                    "安全配置 ddl-forbidden 必须为 true（本工具禁止任何 DDL 建表/改表操作）");
        }
        return guard;
    }
}
