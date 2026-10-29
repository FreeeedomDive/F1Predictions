package com.xdd.f1.bootstrap.user;

import com.xdd.f1.application.user.UserRepository;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.user.User;
import com.xdd.f1.domain.user.UserId;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryIntegrationTest extends AbstractIntegrationTest {

    private final UserRepository repository;
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    @Autowired
    UserRepositoryIntegrationTest(
            UserRepository repository,
            JdbcClient jdbc,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void returnsEmptyForUnknownIdentity() {
        var identity = new UserIdentityId(
                UserIdentitySource.MANUAL,
                UUID.randomUUID().toString()
        );

        var loaded = transactions.execute(status -> repository.findByIdentity(identity));

        assertThat(loaded).isEmpty();
    }

    @Test
    void savesUserAndIdentityTogetherAndLoadsUserInAnotherTransaction() {
        var user = newUser();
        var identity = new UserIdentityId(
                UserIdentitySource.DISCORD,
                Long.toString(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE)
        );

        transactions.executeWithoutResult(status -> repository.create(user, identity));

        var loaded = transactions.execute(status -> repository.findByIdentity(identity));

        assertThat(loaded).contains(user);
        assertThat(jdbc.sql("""
                        SELECT user_id FROM user_identities
                        WHERE source = :source AND source_id = :sourceId
                        """)
                .param("source", identity.source().name())
                .param("sourceId", identity.sourceId())
                .query(UUID.class)
                .single()).isEqualTo(user.id().value());
    }

    @Test
    void rejectsDuplicateIdentityAndRollsBackTheSecondUser() {
        var identity = new UserIdentityId(
                UserIdentitySource.MANUAL,
                UUID.randomUUID().toString()
        );
        var original = newUser();
        var duplicate = newUser();
        transactions.executeWithoutResult(status -> repository.create(original, identity));

        assertThatThrownBy(() -> transactions.executeWithoutResult(
                status -> repository.create(duplicate, identity)
        )).isInstanceOf(DataIntegrityViolationException.class);

        var loaded = transactions.execute(status -> repository.findByIdentity(identity));
        assertThat(loaded).contains(original);
        assertThat(jdbc.sql("SELECT count(*) FROM users WHERE id = :id")
                .param("id", duplicate.id().value())
                .query(Long.class)
                .single()).isZero();
    }

    private User newUser() {
        return new User(
                new UserId(UUID.randomUUID()),
                Instant.parse("2026-01-02T03:04:05.123456Z")
        );
    }
}
