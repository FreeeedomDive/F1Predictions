package com.xdd.f1.bootstrap.league;

import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantRepository;
import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueService;
import com.xdd.f1.application.user.UserRepository;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.user.User;
import com.xdd.f1.domain.user.UserId;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeagueParticipantServiceIntegrationTest extends AbstractIntegrationTest {

    private final LeagueParticipantService service;
    private final LeagueService leagues;
    private final LeagueParticipantRepository participants;
    private final UserRepository users;
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    @Autowired
    LeagueParticipantServiceIntegrationTest(
            LeagueParticipantService service,
            LeagueService leagues,
            LeagueParticipantRepository participants,
            UserRepository users,
            JdbcClient jdbc,
            PlatformTransactionManager transactionManager
    ) {
        this.service = service;
        this.leagues = leagues;
        this.participants = participants;
        this.users = users;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void createsAndCommitsUserIdentityAndParticipant() {
        var league = leagues.createManual("Participant creation test");
        var identity = newIdentity();

        var created = service.ensureParticipant(league.id(), identity, "First participant");

        var loaded = loadParticipant(league.id(), created.userId());
        var user = loadUser(identity);
        assertThat(loaded.id()).isEqualTo(created.id());
        assertThat(loaded.leagueId()).isEqualTo(league.id());
        assertThat(loaded.userId()).isEqualTo(user.id());
        assertThat(loaded.displayName()).isEqualTo("First participant");
        assertThat(loaded.joinedAt()).isNotNull();
        assertThat(user.createdAt()).isNotNull();
        assertThat(jdbc.sql("""
                        SELECT count(*)
                        FROM users u
                        JOIN user_identities i ON i.user_id = u.id
                        JOIN league_participants p ON p.user_id = u.id
                        WHERE i.source = :source AND i.source_id = :sourceId
                          AND p.league_id = :leagueId AND p.id = :participantId
                        """)
                .param("source", identity.source().name())
                .param("sourceId", identity.sourceId())
                .param("leagueId", league.id().value())
                .param("participantId", created.id().value())
                .query(Long.class)
                .single()).isEqualTo(1L);
    }

    @Test
    void repeatedCallPreservesParticipantUserAndVersionWhenNameIsUnchanged() {
        var league = leagues.createManual("Repeated participation test");
        var identity = newIdentity();
        var created = service.ensureParticipant(league.id(), identity, "Same name");
        var before = loadParticipant(league.id(), created.userId());
        var userBefore = loadUser(identity);
        var versionBefore = version(before.id());

        var repeated = service.ensureParticipant(league.id(), identity, "Same name");

        assertThat(repeated).isEqualTo(before);
        assertThat(loadParticipant(league.id(), before.userId())).isEqualTo(before);
        assertThat(loadUser(identity)).isEqualTo(userBefore);
        assertThat(version(before.id())).isEqualTo(versionBefore);
        assertThat(participantCount(league.id(), before.userId())).isEqualTo(1L);
    }

    @Test
    void updatesNameWithoutReplacingParticipantOrChangingJoinTime() {
        var league = leagues.createManual("Participant rename test");
        var identity = newIdentity();
        var created = service.ensureParticipant(league.id(), identity, "Old name");
        var before = loadParticipant(league.id(), created.userId());
        var versionBefore = version(before.id());

        var updated = service.ensureParticipant(league.id(), identity, "New name");

        var loaded = loadParticipant(league.id(), before.userId());
        assertThat(loaded).isEqualTo(updated);
        assertThat(loaded.id()).isEqualTo(before.id());
        assertThat(loaded.userId()).isEqualTo(before.userId());
        assertThat(loaded.leagueId()).isEqualTo(before.leagueId());
        assertThat(loaded.joinedAt()).isEqualTo(before.joinedAt());
        assertThat(loaded.displayName()).isEqualTo("New name");
        assertThat(version(before.id())).isEqualTo(versionBefore + 1);
        assertThat(participantCount(league.id(), before.userId())).isEqualTo(1L);
    }

    @Test
    void reusesGlobalUserAcrossLeaguesAndKeepsNamesIndependent() {
        var firstLeague = leagues.createManual("First participation league");
        var secondLeague = leagues.createManual("Second participation league");
        var identity = newIdentity();

        var first = service.ensureParticipant(firstLeague.id(), identity, "First server name");
        var second = service.ensureParticipant(secondLeague.id(), identity, "Second server name");

        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(loadParticipant(firstLeague.id(), first.userId()).displayName())
                .isEqualTo("First server name");
        assertThat(loadParticipant(secondLeague.id(), second.userId()).displayName())
                .isEqualTo("Second server name");
        assertThat(loadUser(identity).id()).isEqualTo(first.userId());
    }

    @Test
    void doesNotMergeDifferentIdentitiesWithTheSameDisplayName() {
        var league = leagues.createManual("Same display name test");

        var first = service.ensureParticipant(league.id(), newIdentity(), "Same name");
        var second = service.ensureParticipant(league.id(), newIdentity(), "Same name");

        assertThat(second.userId()).isNotEqualTo(first.userId());
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(loadParticipant(league.id(), first.userId()).id()).isEqualTo(first.id());
        assertThat(loadParticipant(league.id(), second.userId()).id()).isEqualTo(second.id());
    }

    @Test
    void treatsTheSameExternalIdFromDifferentSourcesAsDifferentUsers() {
        var league = leagues.createManual("Identity source isolation test");
        var sourceId = Long.toString(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
        var discord = new UserIdentityId(UserIdentitySource.DISCORD, sourceId);
        var manual = new UserIdentityId(UserIdentitySource.MANUAL, sourceId);

        var first = service.ensureParticipant(league.id(), discord, "Discord user");
        var second = service.ensureParticipant(league.id(), manual, "Manual user");

        assertThat(second.userId()).isNotEqualTo(first.userId());
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(loadUser(discord).id()).isEqualTo(first.userId());
        assertThat(loadUser(manual).id()).isEqualTo(second.userId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rollsBackNewUserAndIdentityWhenDisplayNameIsInvalid(String displayName) {
        var league = leagues.createManual("Invalid initial name test");
        var identity = newIdentity();
        var before = counts();

        assertThatThrownBy(() -> service.ensureParticipant(league.id(), identity, displayName))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Display name must not be");

        assertThat(counts()).isEqualTo(before);
        var loaded = transactions.execute(status -> users.findByIdentity(identity));
        assertThat(loaded).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rollsBackInvalidRenameAndPreservesExistingParticipant(String displayName) {
        var league = leagues.createManual("Invalid rename test");
        var identity = newIdentity();
        var created = service.ensureParticipant(league.id(), identity, "Valid name");
        var before = loadParticipant(league.id(), created.userId());
        var versionBefore = version(before.id());

        assertThatThrownBy(() -> service.ensureParticipant(league.id(), identity, displayName))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Display name must not be");

        assertThat(loadParticipant(league.id(), before.userId())).isEqualTo(before);
        assertThat(version(before.id())).isEqualTo(versionBefore);
    }

    @Test
    void rollsBackUserAndIdentityWhenLeagueDoesNotExist() {
        var missingLeague = new LeagueId(UUID.randomUUID());
        var identity = newIdentity();
        var before = counts();

        assertThatThrownBy(() -> service.ensureParticipant(missingLeague, identity, "Participant"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(counts()).isEqualTo(before);
        var loaded = transactions.execute(status -> users.findByIdentity(identity));
        assertThat(loaded).isEmpty();
    }

    @Test
    void rejectsDuplicateParticipationForTheSameLeagueAndUser() {
        var league = leagues.createManual("Duplicate participation test");
        var created = service.ensureParticipant(league.id(), newIdentity(), "Original participant");
        var before = loadParticipant(league.id(), created.userId());
        var duplicate = new LeagueParticipant(
                new LeagueParticipantId(UUID.randomUUID()),
                league.id(),
                created.userId(),
                "Duplicate participant",
                before.joinedAt()
        );

        assertThatThrownBy(() -> transactions.executeWithoutResult(
                status -> participants.create(duplicate)
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(loadParticipant(league.id(), created.userId())).isEqualTo(before);
        assertThat(participantCount(league.id(), created.userId())).isEqualTo(1L);
    }

    @Test
    void returnsEmptyForUnknownParticipation() {
        var leagueId = new LeagueId(UUID.randomUUID());
        var userId = new UserId(UUID.randomUUID());

        var loaded = transactions.execute(
                status -> participants.findByLeagueAndUser(leagueId, userId)
        );
        assertThat(loaded).isEmpty();
    }

    @Test
    void throwsApplicationExceptionWhenRenamingUnknownParticipant() {
        var participantId = new LeagueParticipantId(UUID.randomUUID());

        assertThatThrownBy(() -> transactions.executeWithoutResult(
                status -> participants.updateDisplayName(participantId, "New name")
        )).isInstanceOf(LeagueParticipantNotFoundException.class);
    }

    private UserIdentityId newIdentity() {
        return new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
    }

    private User loadUser(UserIdentityId identity) {
        return transactions.execute(status -> users.findByIdentity(identity).orElseThrow());
    }

    private LeagueParticipant loadParticipant(LeagueId leagueId, UserId userId) {
        return transactions.execute(status -> participants.findByLeagueAndUser(leagueId, userId).orElseThrow());
    }

    private long version(LeagueParticipantId id) {
        return jdbc.sql("SELECT version FROM league_participants WHERE id = :id")
                .param("id", id.value())
                .query(Long.class)
                .single();
    }

    private long participantCount(LeagueId leagueId, UserId userId) {
        return jdbc.sql("""
                        SELECT count(*) FROM league_participants
                        WHERE league_id = :leagueId AND user_id = :userId
                        """)
                .param("leagueId", leagueId.value())
                .param("userId", userId.value())
                .query(Long.class)
                .single();
    }

    private DatabaseCounts counts() {
        return new DatabaseCounts(
                jdbc.sql("SELECT count(*) FROM users").query(Long.class).single(),
                jdbc.sql("SELECT count(*) FROM user_identities").query(Long.class).single(),
                jdbc.sql("SELECT count(*) FROM league_participants").query(Long.class).single()
        );
    }

    private record DatabaseCounts(long users, long identities, long participants) {
    }
}
