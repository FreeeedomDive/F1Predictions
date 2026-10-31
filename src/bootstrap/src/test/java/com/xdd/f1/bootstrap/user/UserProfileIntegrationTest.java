package com.xdd.f1.bootstrap.user;

import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueService;
import com.xdd.f1.application.session.SessionService;
import com.xdd.f1.application.user.UserRepository;
import com.xdd.f1.bootstrap.auth.SessionCookie;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.contract.user.MeResponseDto;
import com.xdd.f1.contract.user.UserLeagueDto;
import com.xdd.f1.domain.league.League;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.user.User;
import com.xdd.f1.domain.user.UserId;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import com.xdd.f1.infrastructure.persistence.league.LeagueJpaRepository;
import com.xdd.f1.infrastructure.persistence.user.UserJpaRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UserProfileIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-02-03T04:05:06Z");

    private final MockMvc mvc;
    private final SessionService sessions;
    private final UserRepository users;
    private final UserJpaRepository userJpaRepository;
    private final LeagueService leagues;
    private final LeagueParticipantService participants;
    private final LeagueJpaRepository leagueJpaRepository;
    private final JsonMapper jsonMapper;
    private final List<LeagueId> createdLeagues = new ArrayList<>();
    private final List<UserId> additionalUsers = new ArrayList<>();

    @MockitoBean
    private Clock clock;

    private UserId userId;
    private UserIdentityId identityId;

    @Autowired
    UserProfileIntegrationTest(
            MockMvc mvc,
            SessionService sessions,
            UserRepository users,
            UserJpaRepository userJpaRepository,
            LeagueService leagues,
            LeagueParticipantService participants,
            LeagueJpaRepository leagueJpaRepository,
            JsonMapper jsonMapper
    ) {
        this.mvc = mvc;
        this.sessions = sessions;
        this.users = users;
        this.userJpaRepository = userJpaRepository;
        this.leagues = leagues;
        this.participants = participants;
        this.leagueJpaRepository = leagueJpaRepository;
        this.jsonMapper = jsonMapper;
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        userId = new UserId(UUID.randomUUID());
        identityId = new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
        users.create(new User(userId, NOW), identityId);
    }

    @AfterEach
    void cleanUp() {
        if (userId != null) {
            userJpaRepository.deleteById(userId.value());
        }
        for (var additionalUser : additionalUsers) {
            userJpaRepository.deleteById(additionalUser.value());
        }
        for (var league : createdLeagues) {
            leagueJpaRepository.deleteById(league.value());
        }
    }

    @Test
    void returnsAllOwnLeaguesWithMatchingParticipantData() throws Exception {
        var firstLeague = createLeague("First profile league");
        var secondLeague = createLeague("Second profile league");
        var firstParticipant = participants.ensureParticipant(firstLeague.id(), identityId, "First server name");
        var secondParticipant = participants.ensureParticipant(secondLeague.id(), identityId, "Second server name");
        var token = sessions.create(userId);

        var result = mvc.perform(get("/api/v1/me").cookie(new Cookie(SessionCookie.NAME, token)))
                .andExpect(status().isOk())
                .andReturn();

        var response = jsonMapper.readValue(result.getResponse().getContentAsByteArray(), MeResponseDto.class);
        assertThat(response.userId()).isEqualTo(userId.value());
        assertThat(response.leagues()).containsExactlyInAnyOrder(
                new UserLeagueDto(
                        firstLeague.id().value(),
                        firstParticipant.id().value(),
                        firstLeague.name(),
                        "First server name"
                ),
                new UserLeagueDto(
                        secondLeague.id().value(),
                        secondParticipant.id().value(),
                        secondLeague.name(),
                        "Second server name"
                )
        );
    }

    @Test
    void excludesOtherUsersParticipationsEvenWhenTheirUserIdIsProvided() throws Exception {
        var sharedLeague = createLeague("Shared profile league");
        var otherLeague = createLeague("Other user's profile league");
        var ownParticipant = participants.ensureParticipant(sharedLeague.id(), identityId, "Own participant name");
        var otherIdentity = new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
        var otherParticipant = participants.ensureParticipant(sharedLeague.id(), otherIdentity, "Another participant");
        additionalUsers.add(otherParticipant.userId());
        participants.ensureParticipant(otherLeague.id(), otherIdentity, "Other league participant");
        var token = sessions.create(userId);

        var result = mvc.perform(get("/api/v1/me")
                        .cookie(new Cookie(SessionCookie.NAME, token))
                        .param("userId", otherParticipant.userId().value().toString()))
                .andExpect(status().isOk())
                .andReturn();

        var response = jsonMapper.readValue(result.getResponse().getContentAsByteArray(), MeResponseDto.class);
        assertThat(response.userId()).isEqualTo(userId.value());
        assertThat(response.leagues()).containsExactly(new UserLeagueDto(
                sharedLeague.id().value(),
                ownParticipant.id().value(),
                sharedLeague.name(),
                "Own participant name"
        ));
    }

    @Test
    void returnsEmptyLeaguesArrayForUserWithoutParticipations() throws Exception {
        var token = sessions.create(userId);

        mvc.perform(get("/api/v1/me").cookie(new Cookie(SessionCookie.NAME, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.value().toString()))
                .andExpect(jsonPath("$.leagues").isArray())
                .andExpect(jsonPath("$.leagues").isEmpty());
    }

    private League createLeague(String name) {
        var league = leagues.createManual(name);
        createdLeagues.add(league.id());
        return league;
    }
}
