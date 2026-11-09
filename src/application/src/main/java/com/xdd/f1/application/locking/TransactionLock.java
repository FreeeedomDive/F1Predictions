package com.xdd.f1.application.locking;

public interface TransactionLock {
    void acquire(String resource);
}
