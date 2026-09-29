package com.example.pgsync.exception;

/**
 * 目标表不存在（本工具不创建表）
 */
public class TargetTableNotFoundException extends SyncValidationException {
    public TargetTableNotFoundException(String message) {
        super(message);
    }
}
