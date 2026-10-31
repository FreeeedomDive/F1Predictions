package com.xdd.f1.infrastructure.persistence.session;

import com.xdd.f1.application.session.SessionNotFoundException;
import com.xdd.f1.application.session.SessionRepository;
import com.xdd.f1.domain.session.Session;
import com.xdd.f1.domain.user.UserId;
import com.xdd.f1.infrastructure.persistence.user.UserJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class SessionRepositoryAdapter implements SessionRepository {

    private final SessionJpaRepository sessionJpaRepository;
    private final UserJpaRepository userJpaRepository;

    public SessionRepositoryAdapter(
            SessionJpaRepository sessionJpaRepository,
            UserJpaRepository userJpaRepository
    ) {
        this.sessionJpaRepository = sessionJpaRepository;
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public void create(Session session) {
        var entity = toEntity(session);
        sessionJpaRepository.save(entity);
    }

    @Override
    public Optional<Session> findByHash(String hash) {
        return sessionJpaRepository.findById(hash).map(SessionRepositoryAdapter::toModel);
    }

    @Override
    public void updateLastUsedAt(String hash, Instant lastUsedAt) {
        var session = sessionJpaRepository.findById(hash).orElseThrow(SessionNotFoundException::new);
        session.lastUsedAt = lastUsedAt;
    }

    @Override
    public int deleteExpired(Instant now) {
        return sessionJpaRepository.deleteExpired(now);
    }

    private static Session toModel(SessionEntity entity) {
        return new Session(
                entity.hash,
                new UserId(entity.user.getId()),
                entity.createdAt,
                entity.expiresAt,
                entity.lastUsedAt
        );
    }

    private SessionEntity toEntity(Session session) {
        var entity = new SessionEntity();
        entity.hash = session.hash();
        entity.user = userJpaRepository.getReferenceById(session.userId().value());
        entity.createdAt = session.createdAt();
        entity.expiresAt = session.expiresAt();
        entity.lastUsedAt = session.lastUsedAt();
        return entity;
    }
}
