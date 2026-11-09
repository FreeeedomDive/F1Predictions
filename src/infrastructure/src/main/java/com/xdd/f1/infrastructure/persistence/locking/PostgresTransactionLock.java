package com.xdd.f1.infrastructure.persistence.locking;

import com.xdd.f1.application.locking.TransactionLock;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresTransactionLock implements TransactionLock {
    private final EntityManager entityManager;

    public PostgresTransactionLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String resource) {
        entityManager.createNativeQuery("select 1 from pg_advisory_xact_lock(hashtextextended(:resource, 0))", Integer.class)
                .setParameter("resource", resource)
                .getSingleResult();
    }
}
