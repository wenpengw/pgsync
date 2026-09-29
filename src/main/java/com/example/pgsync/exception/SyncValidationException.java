package com.example.pgsync.exception;

/**
 * 配置/结构校验失败（如字段不兼容、无主键无备用键）
 * 属于可预期的、应跳过该表并继续的错误
 */
public class SyncValidationException extends SyncException {
    public SyncValidationException(String message) {
        super(message);
    }
}
