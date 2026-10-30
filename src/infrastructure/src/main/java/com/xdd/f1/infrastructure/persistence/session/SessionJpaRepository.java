package com.xdd.f1.infrastructure.persistence.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SessionJpaRepository extends JpaRepository<SessionEntity, String> {
    List<SessionEntity> findByUser_Id(UUID userId);

    @Modifying
    @Query("""
        delete from SessionEntity s
        where s.expiresAt <= :now
        """)
    int deleteExpired(@Param("now") Instant now);
}
