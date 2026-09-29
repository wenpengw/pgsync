package com.example.pgsync.engine;

import com.example.pgsync.metadata.model.ColumnMetadata;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * 类型转换：从 ResultSet 读取 + 写入 PreparedStatement（按目标列类型）
 */
public class TypeConverter {

    public static Object read(ResultSet rs, int idx) throws SQLException {
        Object val = rs.getObject(idx);
        if (rs.wasNull()) return null;
        return val;
    }

    public static void set(PreparedStatement ps, int idx, Object value, ColumnMetadata targetCol)
            throws SQLException {
        if (value == null) {
            ps.setNull(idx, targetCol.getJdbcType() == Types.OTHER ? Types.NULL : targetCol.getJdbcType());
            return;
        }
        String pgType = targetCol.getPgTypeName();
        if (pgType != null) {
            // 特殊类型走 PGobject
            if (pgType.equalsIgnoreCase("jsonb") || pgType.equalsIgnoreCase("json")) {
                PGobject pgo = new PGobject();
                pgo.setType(pgType.toLowerCase());
                pgo.setValue(value.toString());
                ps.setObject(idx, pgo);
                return;
            }
            if (pgType.equalsIgnoreCase("uuid") && !(value instanceof java.util.UUID)) {
                ps.setObject(idx, java.util.UUID.fromString(value.toString()));
                return;
            }
            if (pgType.startsWith("_")) { // 数组
                ps.setObject(idx, value, Types.ARRAY);
                return;
            }
            if (pgType.equalsIgnoreCase("bytea")) {
                ps.setBytes(idx, (byte[]) value);
                return;
            }
        }
        // 通用分支
        ps.setObject(idx, value, targetCol.getJdbcType());
    }
}
