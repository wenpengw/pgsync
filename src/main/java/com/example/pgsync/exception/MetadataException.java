package com.example.pgsync.exception;

/**
 * 元数据探测失败（如连接异常、查询异常）
 */
public class MetadataException extends SyncException {
    public MetadataException(String message) {
        super(message);
    }

    public MetadataException(String message, Throwable cause) {
        super(message, cause);
    }
}
