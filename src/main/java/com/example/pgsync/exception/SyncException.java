package com.example.pgsync.exception;

/**
 * 同步异常基类
 */
public class SyncException extends RuntimeException {
    public SyncException(String message) {
        super(message);
    }

    public SyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
