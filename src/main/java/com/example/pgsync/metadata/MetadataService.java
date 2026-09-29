package com.example.pgsync.metadata;

import com.example.pgsync.metadata.model.TableMetadata;
import com.example.pgsync.exception.MetadataException;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 表结构探测服务
 */
public interface MetadataService {

    /**
     * 加载指定表的完整元数据（列、主键、唯一索引、外键）
     *
     * @param ds       数据源
     * @param schema   schema 名
     * @param tableName 表名
     * @return 表元数据（exists 可能为 false，表示表不存在）
     */
    TableMetadata load(DataSource ds, String schema, String tableName) throws MetadataException;

    /**
     * 仅判断表是否存在（轻量，用于连通性预检）
     */
    boolean tableExists(Connection conn, String schema, String tableName);
}
