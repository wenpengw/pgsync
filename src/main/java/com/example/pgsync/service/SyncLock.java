package com.example.pgsync.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 全局同步锁：防止 HTTP 重复点击导致同一张表并行写
 */
@Component
public class SyncLock {
    private final ReentrantLock lock = new ReentrantLock();

    public boolean tryLock() {
        return lock.tryLock();
    }

    public void unlock() {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
