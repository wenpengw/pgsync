package com.example.pgsync.engine;

import com.example.pgsync.exception.SyncException;
import com.example.pgsync.mapping.ColumnMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 源库数据读取：流式游标 + fetchSize，避免大表 OOM
 */
public class DataReader {
    private static final Logger log = LoggerFactory.getLogger(DataReader.class);

    private final DataSource sourceDs;
    private final String schema;
    private final String table;
    private final ColumnMapping mapping;
    private final int fetchSize;

    public DataReader(DataSource sourceDs, String schema, String table,
                      ColumnMapping mapping, int fetchSize) {
        this.sourceDs = sourceDs;
        this.schema = schema;
        this.table = table;
        this.mapping = mapping;
        this.fetchSize = fetchSize;
    }

    /**
     * 全量流式读取并处理（按 batchSize 回调）
     */
    public void readInBatches(int batchSize, RowBatchHandler handler) {
        String sql = com.example.pgsync.sql.SelectSqlBuilder.build(schema, table, mapping, null);
        log.debug("源库读取 SQL: {}", sql);

        try (Connection conn = sourceDs.getConnection()) {
            // PG 流式游标必须 autoCommit=false
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql,
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                ps.setFetchSize(fetchSize);
                ps.setFetchDirection(ResultSet.FETCH_FORWARD);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Object[]> batch = new ArrayList<>(batchSize);
                    int total = 0;
                    while (rs.next()) {
                        Object[] row = new Object[mapping.getSyncColumns().size()];
                        for (int i = 0; i < mapping.getSyncColumns().size(); i++) {
                            row[i] = TypeConverter.read(rs, i + 1);
                        }
                        batch.add(row);
                        total++;
                        if (batch.size() >= batchSize) {
                            handler.handle(batch);
                            batch.clear();
                        }
                    }
                    if (!batch.isEmpty()) {
                        handler.handle(batch);
                    }
                    log.debug("源库 {} 读取完成，共 {} 行", table, total);
                }
            }
        } catch (SQLException e) {
            throw new SyncException("读取源表失败: " + schema + "." + table, e);
        }
    }

    /**
     * 源表行数（近似，用于统计）
     */
    public long estimateCount() {
        String sql = "SELECT COUNT(*) FROM " + com.example.pgsync.sql.SqlIdentifierUtils.qualify(schema, table);
        try (Connection conn = sourceDs.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            log.warn("估算行数失败 {}: {}", table, e.getMessage());
        }
        return -1;
    }

    @FunctionalInterface
    public interface RowBatchHandler {
        void handle(List<Object[]> batch);
    }
}
