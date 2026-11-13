package com.xdd.f1.bootstrap.rights;

import com.xdd.f1.application.rights.RightsService;
import com.xdd.f1.application.user.UserNotFoundException;
import com.xdd.f1.application.user.UserRepository;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.rights.Right;
import com.xdd.f1.domain.user.User;
import com.xdd.f1.domain.user.UserId;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import com.xdd.f1.infrastructure.persistence.user.UserJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RightsServiceIntegrationTest extends AbstractIntegrationTest {
    private final RightsService rights;
    private final UserRepository users;
    private final UserJpaRepository userJpaRepository;
    private final List<UserId> createdUsers = new ArrayList<>();

    @Autowired
    RightsServiceIntegrationTest(RightsService rights, UserRepository users, UserJpaRepository userJpaRepository) {
        this.rights = rights;
        this.users = users;
        this.userJpaRepository = userJpaRepository;
    }

    @AfterEach
    void cleanUp() {
        for (var userId : createdUsers) {
            userJpaRepository.deleteById(userId.value());
        }
    }

    @Test
    void storesIndependentGlobalRightsAndMakesRepeatedChangesIdempotent() {
        var userId = createUser();
        var otherUserId = createUser();
        assertThat(rights.findByUserId(userId)).isEmpty();

        rights.grant(userId, Right.EDIT_RIGHTS);
        rights.grant(userId, Right.EDIT_RIGHTS);
        assertThat(rights.findByUserId(userId)).containsExactly(Right.EDIT_RIGHTS);
        assertThat(rights.findByUserId(otherUserId)).isEmpty();

        rights.grant(userId, Right.EDIT_RACE_RESULTS);
        rights.grant(userId, Right.EDIT_PREDICTIONS);
        rights.grant(userId, Right.EDIT_RACE_LINEUPS);
        rights.grant(otherUserId, Right.EDIT_RACE_LINEUPS);
        assertThat(rights.findByUserId(userId)).containsExactlyInAnyOrder(Right.values());

        rights.revoke(userId, Right.EDIT_RACE_RESULTS);
        rights.revoke(userId, Right.EDIT_RACE_RESULTS);
        rights.revoke(otherUserId, Right.EDIT_RIGHTS);
        assertThat(rights.findByUserId(userId))
                .containsExactlyInAnyOrder(Right.EDIT_RIGHTS, Right.EDIT_PREDICTIONS, Right.EDIT_RACE_LINEUPS);
        assertThat(rights.findByUserId(otherUserId)).containsExactly(Right.EDIT_RACE_LINEUPS);
    }

    @Test
    void rejectsGrantAndRevokeForUnknownUser() {
        var userId = new UserId(UUID.randomUUID());

        assertThatThrownBy(() -> rights.grant(userId, Right.EDIT_RIGHTS))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> rights.revoke(userId, Right.EDIT_RIGHTS))
                .isInstanceOf(UserNotFoundException.class);
        assertThat(rights.findByUserId(userId)).isEmpty();
    }

    @Test
    void deletesAssignmentsWithTheirUserWithoutAffectingOtherUsers() {
        var userId = createUser();
        var otherUserId = createUser();
        rights.grant(userId, Right.EDIT_RIGHTS);
        rights.grant(otherUserId, Right.EDIT_RIGHTS);

        userJpaRepository.deleteById(userId.value());

        assertThat(rights.findByUserId(userId)).isEmpty();
        assertThat(rights.findByUserId(otherUserId)).containsExactly(Right.EDIT_RIGHTS);
    }

    private UserId createUser() {
        var userId = new UserId(UUID.randomUUID());
        users.create(new User(userId, Instant.parse("2026-11-13T12:00:00Z")),
                new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString()));
        createdUsers.add(userId);
        return userId;
    }
}
