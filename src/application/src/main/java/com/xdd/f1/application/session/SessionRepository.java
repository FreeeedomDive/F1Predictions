package com.xdd.f1.application.session;

import com.xdd.f1.domain.session.Session;

import java.time.Instant;
import java.util.Optional;

public interface SessionRepository {
    void create(Session session);
    Optional<Session> findByHash(String hash);
    void updateLastUsedAt(String hash, Instant lastUsedAt);
    int deleteExpired(Instant now);
}
