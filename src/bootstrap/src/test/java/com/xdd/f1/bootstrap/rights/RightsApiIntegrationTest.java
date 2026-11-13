package com.xdd.f1.bootstrap.rights;

import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueService;
import com.xdd.f1.application.rights.RightsService;
import com.xdd.f1.application.session.SessionService;
import com.xdd.f1.application.user.UserRepository;
import com.xdd.f1.bootstrap.auth.SessionCookie;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.contract.common.ListResponseDto;
import com.xdd.f1.contract.rights.RightDto;
import com.xdd.f1.contract.rights.UserRightsDto;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.rights.Right;
import com.xdd.f1.domain.user.*;
import com.xdd.f1.infrastructure.persistence.league.LeagueJpaRepository;
import com.xdd.f1.infrastructure.persistence.user.UserJpaRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RightsApiIntegrationTest extends AbstractIntegrationTest {
    private final MockMvc mvc;
    private final RightsService rights;
    private final SessionService sessions;
    private final UserRepository users;
    private final UserJpaRepository userJpaRepository;
    private final LeagueService leagues;
    private final LeagueParticipantService participants;
    private final LeagueJpaRepository leagueJpaRepository;
    private final JsonMapper json;
    private final List<UserId> createdUsers = new ArrayList<>();
    private final List<LeagueId> createdLeagues = new ArrayList<>();

    @Autowired
    RightsApiIntegrationTest(MockMvc mvc, RightsService rights, SessionService sessions, UserRepository users,
                             UserJpaRepository userJpaRepository, LeagueService leagues,
                             LeagueParticipantService participants, LeagueJpaRepository leagueJpaRepository,
                             JsonMapper json) {
        this.mvc = mvc;
        this.rights = rights;
        this.sessions = sessions;
        this.users = users;
        this.userJpaRepository = userJpaRepository;
        this.leagues = leagues;
        this.participants = participants;
        this.leagueJpaRepository = leagueJpaRepository;
        this.json = json;
    }

    @AfterEach
    void cleanUp() {
        for (var user : createdUsers) userJpaRepository.deleteById(user.value());
        for (var league : createdLeagues) leagueJpaRepository.deleteById(league.value());
    }

    @Test
    void administrativeAccessUsesCurrentRightsOfTheSessionActor() throws Exception {
        var actor = createUser();
        var cookie = cookie(actor);
        var url = "/adminApi/v1/races?season=2027";
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).cookie(cookie)).andExpect(status().isForbidden());

        rights.grant(actor, Right.EDIT_RIGHTS);
        mvc.perform(get(url).cookie(cookie)).andExpect(status().isForbidden());
        rights.grant(actor, Right.EDIT_RACE_RESULTS);
        mvc.perform(get(url).cookie(cookie)).andExpect(status().isOk());
        rights.revoke(actor, Right.EDIT_RACE_RESULTS);
        mvc.perform(get(url).cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me").cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    void predictionAndLineupEndpointsRequireTheirOwnRights() throws Exception {
        var actor = createUser();
        var cookie = cookie(actor);
        var predictions = "/adminApi/v1/races/" + UUID.randomUUID() + "/predictions";
        rights.grant(actor, Right.EDIT_RACE_RESULTS);
        mvc.perform(get(predictions).cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(put(predictions + "/" + UUID.randomUUID()).cookie(cookie)
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/adminApi/v1/raceLineups/target?season=2027").cookie(cookie))
                .andExpect(status().isForbidden());

        rights.grant(actor, Right.EDIT_RACE_LINEUPS);
        mvc.perform(get("/adminApi/v1/raceLineups/target?season=2027").cookie(cookie))
                .andExpect(status().isNoContent());
    }

    @Test
    void managementUsesActorRightsAndChangesOneAssignmentIdempotently() throws Exception {
        var actor = createUser();
        var target = createUser();
        var cookie = cookie(actor);
        var url = "/adminApi/v1/rights/" + target.value() + "/EDIT_PREDICTIONS";
        rights.grant(target, Right.EDIT_RIGHTS);
        mvc.perform(get("/adminApi/v1/rights").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(put(url).cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(delete(url).cookie(cookie)).andExpect(status().isForbidden());

        rights.grant(actor, Right.EDIT_RIGHTS);
        mvc.perform(put(url).cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(put(url).cookie(cookie)).andExpect(status().isNoContent());
        assertThat(rights.findByUserId(target)).containsExactlyInAnyOrder(Right.EDIT_RIGHTS, Right.EDIT_PREDICTIONS);
        assertThat(rights.findByUserId(actor)).containsExactly(Right.EDIT_RIGHTS);
        mvc.perform(delete(url).cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(delete(url).cookie(cookie)).andExpect(status().isNoContent());
        assertThat(rights.findByUserId(target)).containsExactly(Right.EDIT_RIGHTS);

        mvc.perform(put("/adminApi/v1/rights/" + UUID.randomUUID() + "/EDIT_RIGHTS").cookie(cookie))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/adminApi/v1/rights/" + UUID.randomUUID() + "/EDIT_RIGHTS").cookie(cookie))
                .andExpect(status().isNotFound());
        mvc.perform(put("/adminApi/v1/rights/" + target.value() + "/UNKNOWN").cookie(cookie))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/adminApi/v1/rights/" + actor.value() + "/EDIT_RIGHTS").cookie(cookie))
                .andExpect(status().isNoContent());
        mvc.perform(get("/adminApi/v1/rights").cookie(cookie)).andExpect(status().isForbidden());
    }

    @Test
    void catalogueCombinesDistinctNamesAcrossLeaguesAndIncludesUsersWithoutAssignmentsOrNames() throws Exception {
        var actor = createUser();
        var namedUser = createUser();
        var namesake = createUser();
        var unnamed = createUser();
        var firstLeague = leagues.createManual("First rights league");
        var secondLeague = leagues.createManual("Second rights league");
        var thirdLeague = leagues.createManual("Third rights league");
        createdLeagues.addAll(List.of(firstLeague.id(), secondLeague.id(), thirdLeague.id()));
        participants.ensureParticipant(firstLeague.id(), identity(namedUser), "First name");
        participants.ensureParticipant(secondLeague.id(), identity(namedUser), "Second name");
        participants.ensureParticipant(thirdLeague.id(), identity(namedUser), "First name");
        participants.ensureParticipant(firstLeague.id(), identity(namesake), "First name");
        rights.grant(actor, Right.EDIT_RIGHTS);
        rights.grant(namedUser, Right.EDIT_RACE_RESULTS);
        rights.grant(namedUser, Right.EDIT_PREDICTIONS);

        var result = mvc.perform(get("/adminApi/v1/rights").cookie(cookie(actor)))
                .andExpect(status().isOk()).andReturn();
        var response = json.readValue(result.getResponse().getContentAsByteArray(),
                new TypeReference<ListResponseDto<UserRightsDto>>() {});
        assertThat(response.data()).filteredOn(user -> user.userId().equals(namedUser.value()))
                .containsExactly(new UserRightsDto(namedUser.value(), List.of("First name", "Second name"),
                        List.of(RightDto.EDIT_PREDICTIONS, RightDto.EDIT_RACE_RESULTS)));
        assertThat(response.data()).filteredOn(user -> user.userId().equals(namesake.value()))
                .containsExactly(new UserRightsDto(namesake.value(), List.of("First name"), List.of()));
        assertThat(response.data()).filteredOn(user -> user.userId().equals(unnamed.value()))
                .containsExactly(new UserRightsDto(unnamed.value(), List.of(), List.of()));
    }

    private UserId createUser() {
        var id = new UserId(UUID.randomUUID());
        users.create(new User(id, Instant.now()), identity(id));
        createdUsers.add(id);
        return id;
    }

    private UserIdentityId identity(UserId id) {
        return new UserIdentityId(UserIdentitySource.MANUAL, id.value().toString());
    }

    private Cookie cookie(UserId id) {
        return new Cookie(SessionCookie.NAME, sessions.create(id));
    }
}
