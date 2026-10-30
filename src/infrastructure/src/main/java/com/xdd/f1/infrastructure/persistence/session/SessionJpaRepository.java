package com.xdd.f1.infrastructure.persistence.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SessionJpaRepository extends JpaRepository<SessionEntity, String> {
    @Modifying
    @Query("""
        delete from SessionEntity s
        where s.expiresAt <= :now
        """)
    int deleteExpired(@Param("now") Instant now);
}
