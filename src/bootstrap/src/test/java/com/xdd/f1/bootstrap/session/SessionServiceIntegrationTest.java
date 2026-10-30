package com.xdd.f1.bootstrap.session;

import com.xdd.f1.application.session.SessionRepository;
import com.xdd.f1.application.session.SessionService;
import com.xdd.f1.application.user.UserRepository;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.session.Session;
import com.xdd.f1.domain.user.User;
import com.xdd.f1.domain.user.UserId;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import com.xdd.f1.infrastructure.persistence.session.SessionEntity;
import com.xdd.f1.infrastructure.persistence.session.SessionJpaRepository;
import com.xdd.f1.infrastructure.persistence.user.UserJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class SessionServiceIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-02-03T04:05:06Z");

    private final SessionService service;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final SessionJpaRepository sessionJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final TransactionTemplate transactions;

    @MockitoBean
    private Clock clock;

    private UserId userId;

    @Autowired
    SessionServiceIntegrationTest(
            SessionService service,
            SessionRepository sessions,
            UserRepository users,
            SessionJpaRepository sessionJpaRepository,
            UserJpaRepository userJpaRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.service = service;
        this.sessions = sessions;
        this.users = users;
        this.sessionJpaRepository = sessionJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        userId = new UserId(UUID.randomUUID());
        var user = new User(userId, NOW);
        var identity = new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
        users.create(user, identity);
    }

    @AfterEach
    void cleanUp() {
        if (userId != null) {
            userJpaRepository.deleteById(userId.value());
        }
    }

    @Test
    void issuesUuidTokenAndPersistsOnlyItsHashWithSevenDayLifetime() {
        var token = service.create(userId);

        assertThat(UUID.fromString(token).version()).isEqualTo(4);
        var hashes = storedHashes();
        assertThat(hashes).hasSize(1);
        var hash = hashes.getFirst();
        assertThat(hash).isNotEqualTo(token);

        var stored = loadSession(hash).orElseThrow();
        assertThat(stored.userId()).isEqualTo(userId);
        assertThat(stored.createdAt()).isEqualTo(NOW);
        assertThat(stored.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(stored.lastUsedAt()).isNull();

        var authenticated = service.find(token);
        assertThat(authenticated).hasValueSatisfying(session -> {
            assertThat(session.hash()).isEqualTo(hash);
            assertThat(session.userId()).isEqualTo(userId);
            assertThat(session.expiresAt()).isEqualTo(stored.expiresAt());
        });
    }

    @Test
    void acceptsTokenBeforeExpiryAndUpdatesLastUsedWithoutExtendingLifetime() {
        var token = issueSessionAt(NOW.minus(Duration.ofDays(7)).plusSeconds(1));
        var hash = storedHashes().getFirst();
        var original = loadSession(hash).orElseThrow();
        when(clock.instant()).thenReturn(NOW);

        var authenticated = service.find(token);

        assertThat(authenticated).hasValueSatisfying(session -> {
            assertThat(session.userId()).isEqualTo(userId);
            assertThat(session.expiresAt()).isEqualTo(original.expiresAt());
        });
        var stored = loadSession(hash).orElseThrow();
        assertThat(stored.userId()).isEqualTo(userId);
        assertThat(stored.lastUsedAt()).isEqualTo(NOW);
        assertThat(stored.createdAt()).isEqualTo(original.createdAt());
        assertThat(stored.expiresAt()).isEqualTo(original.expiresAt());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "unknown-token"})
    void rejectsMissingBlankAndUnknownTokens(String token) {
        assertThat(service.find(token)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void rejectsTokenAtOrAfterExpiryWithoutUpdatingLastUsed(long secondsAfterExpiry) {
        var token = issueSessionAt(NOW.minus(Duration.ofDays(7)));
        var hash = storedHashes().getFirst();
        when(clock.instant()).thenReturn(NOW.minusSeconds(60));
        assertThat(service.find(token)).isPresent();
        var original = loadSession(hash).orElseThrow();
        when(clock.instant()).thenReturn(NOW.plusSeconds(secondsAfterExpiry));

        var authenticated = service.find(token);

        assertThat(authenticated).isEmpty();
        assertThat(loadSession(hash)).contains(original);
    }

    @Test
    void allowsMultipleIndependentSessionsForOneUser() {
        var firstToken = service.create(userId);
        var secondToken = service.create(userId);

        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThat(storedHashes()).hasSize(2);
        assertThat(service.find(firstToken)).hasValueSatisfying(
                session -> assertThat(session.userId()).isEqualTo(userId)
        );
        assertThat(service.find(secondToken)).hasValueSatisfying(
                session -> assertThat(session.userId()).isEqualTo(userId)
        );
    }

    @Test
    void rejectsTokenWithAnotherHashingKey() {
        var token = service.create(userId);
        var otherKey = new SecretKeySpec(
                "another-test-session-hash-key".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        var otherService = new SessionService(sessions, clock, otherKey);

        var authenticated = transactions.execute(status -> otherService.find(token));

        assertThat(authenticated).isEmpty();
        assertThat(service.find(token)).isPresent();
    }

    @Test
    void bulkDeletesExpiredSessionsIncludingBoundaryAndKeepsActiveSession() {
        var expiredToken = issueSessionAt(NOW.minus(Duration.ofDays(7)).minusSeconds(1));
        var boundaryToken = issueSessionAt(NOW.minus(Duration.ofDays(7)));
        var activeToken = issueSessionAt(NOW.minus(Duration.ofDays(7)).plusSeconds(1));
        assertThat(storedHashes()).hasSize(3);
        when(clock.instant()).thenReturn(NOW);

        int deleted = service.deleteExpired();

        assertThat(deleted).isEqualTo(2);
        assertThat(storedHashes()).hasSize(1);
        assertThat(service.find(expiredToken)).isEmpty();
        assertThat(service.find(boundaryToken)).isEmpty();
        assertThat(service.find(activeToken)).hasValueSatisfying(
                session -> assertThat(session.userId()).isEqualTo(userId)
        );
        assertThat(service.deleteExpired()).isZero();
    }

    private String issueSessionAt(Instant createdAt) {
        when(clock.instant()).thenReturn(createdAt);
        return service.create(userId);
    }

    private Optional<Session> loadSession(String hash) {
        return transactions.execute(status -> sessions.findByHash(hash));
    }

    private List<String> storedHashes() {
        return sessionJpaRepository.findByUser_Id(userId.value()).stream()
                .map(SessionEntity::getHash)
                .toList();
    }
}
