package com.example.pgsync.exception;

/**
 * SQL 守卫拦截到 DDL / 非白名单语句时抛出
 */
public class DdlForbiddenException extends SyncException {
    public DdlForbiddenException(String message) {
        super(message);
    }
}
