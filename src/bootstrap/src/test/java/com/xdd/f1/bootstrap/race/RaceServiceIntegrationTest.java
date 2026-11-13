package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.race.RaceCalendarEntry;
import com.xdd.f1.application.race.RaceCalendarProvider;
import com.xdd.f1.application.race.RaceCalendarSyncService;
import com.xdd.f1.application.race.RaceNotFoundException;
import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.application.race.lineup.RaceLineupService;
import com.xdd.f1.application.session.SessionService;
import com.xdd.f1.application.rights.RightsService;
import com.xdd.f1.domain.rights.Right;
import com.xdd.f1.application.user.UserRepository;
import com.xdd.f1.bootstrap.auth.SessionCookie;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.League;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueSettings;
import com.xdd.f1.domain.league.LeagueSource;
import com.xdd.f1.domain.race.InvalidRaceStatusException;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.Team;
import com.xdd.f1.domain.race.lineup.TeamBranding;
import com.xdd.f1.domain.user.User;
import com.xdd.f1.domain.user.UserId;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import com.xdd.f1.infrastructure.persistence.league.LeagueJpaRepository;
import com.xdd.f1.infrastructure.persistence.race.RaceJpaRepository;
import com.xdd.f1.infrastructure.persistence.user.UserJpaRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RaceServiceIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-02-03T04:05:06Z");

    private final RaceService service;
    private final RaceJpaRepository jpaRepository;
    private final RaceCalendarSyncService calendarSync;
    private final LeagueRepository leagues;
    private final LeagueParticipantService participants;
    private final LeagueJpaRepository leagueJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final MockMvc mvc;
    private final SessionService sessions;
    private final UserRepository users;
    private final RightsService rights;
    private final RaceLineupService lineupService;

    private LeagueId createdLeagueId;
    private UserId createdUserId;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private RaceCalendarProvider calendarProvider;

    @Autowired
    RaceServiceIntegrationTest(
            RaceService service,
            RaceJpaRepository jpaRepository,
            RaceCalendarSyncService calendarSync,
            LeagueRepository leagues,
            LeagueParticipantService participants,
            LeagueJpaRepository leagueJpaRepository,
            UserJpaRepository userJpaRepository,
            MockMvc mvc,
            SessionService sessions,
            UserRepository users,
            RightsService rights,
            RaceLineupService lineupService
    ) {
        this.service = service;
        this.jpaRepository = jpaRepository;
        this.calendarSync = calendarSync;
        this.leagues = leagues;
        this.participants = participants;
        this.leagueJpaRepository = leagueJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.mvc = mvc;
        this.sessions = sessions;
        this.users = users;
        this.rights = rights;
        this.lineupService = lineupService;
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @AfterEach
    void cleanUp() {
        jpaRepository.deleteAll();
        if (createdUserId != null) {
            userJpaRepository.deleteById(createdUserId.value());
        }
        if (createdLeagueId != null) {
            leagueJpaRepository.deleteById(createdLeagueId.value());
        }
    }

    @Test
    void createsScheduledRaceAndLoadsItById() {
        var created = createRace(2027, 1, "Bahrain Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));

        var loaded = service.getById(created.id());

        assertThat(loaded).isEqualTo(created);
        assertThat(loaded.status()).isEqualTo(RaceStatus.SCHEDULED);
    }

    @Test
    void returnsSeasonCalendarSortedByOpenTime() {
        var later = createRace(2027, 2, "Saudi Arabian Grand Prix", NOW.plusSeconds(7200), NOW.plusSeconds(10800));
        var earlier = createRace(2027, 1, "Bahrain Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(5400));
        createRace(2026, 1, "Previous Season Grand Prix", NOW, NOW.plusSeconds(3600));

        var races = service.findAllBySeason(2027);

        assertThat(races).containsExactly(earlier, later);
    }

    @Test
    void opensClosesAndFinishesRace() {
        var race = createRace(2027, 1, "Bahrain Grand Prix", NOW, NOW.plusSeconds(3600));
        prepareLineup(race);

        service.openRacePredictions(race.id());
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.OPEN);

        service.closeRacePredictions(race.id());
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.CLOSED);

        service.finishRace(race.id());
        var finished = service.getById(race.id());
        assertThat(finished.status()).isEqualTo(RaceStatus.FINISHED);
        assertThat(service.findFinishedBySeason(2027)).containsExactly(finished);
    }

    @Test
    void rejectsInvalidStatusTransition() {
        var race = createRace(2027, 1, "Bahrain Grand Prix", NOW, NOW.plusSeconds(3600));

        assertThatThrownBy(() -> service.closeRacePredictions(race.id()))
                .isInstanceOf(InvalidRaceStatusException.class)
                .hasMessageContaining(RaceStatus.OPEN.name());

        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.SCHEDULED);
    }

    @Test
    void processesOnlyDueTransitionsAndCanRunRepeatedly() {
        var toOpen = createRace(2027, 1, "Opening Grand Prix", NOW, NOW.plusSeconds(3600));
        var toClose = createRace(2027, 2, "Closing Grand Prix", NOW.minusSeconds(7200), NOW);
        var future = createRace(2027, 3, "Future Grand Prix", NOW.plusSeconds(1), NOW.plusSeconds(3600));
        prepareLineup(toClose);
        service.openRacePredictions(toClose.id());

        service.processScheduledTransitions();
        service.processScheduledTransitions();

        assertThat(service.getById(toOpen.id()).status()).isEqualTo(RaceStatus.OPEN);
        assertThat(service.getById(toClose.id()).status()).isEqualTo(RaceStatus.CLOSED);
        assertThat(service.getById(future.id()).status()).isEqualTo(RaceStatus.SCHEDULED);
    }

    @Test
    void closesDueRaceWhenAnEarlierScheduledRaceHasNoLineup() {
        var missed = createRace(2027, 1, "Earlier race without lineup", NOW.minusSeconds(7200), NOW.minusSeconds(3600));
        var brazil = createRace(2027, 2, "Brazilian Grand Prix", NOW, NOW.plusSeconds(3600));
        prepareLineup(brazil);
        service.openRacePredictions(brazil.id());
        when(clock.instant()).thenReturn(brazil.closeAt());

        var result = service.processScheduledTransitions();

        assertThat(service.getById(brazil.id()).status()).isEqualTo(RaceStatus.CLOSED);
        assertThat(service.getById(missed.id()).status()).isEqualTo(RaceStatus.SCHEDULED);
        assertThat(result).containsEntry(brazil.id(), RaceStatus.CLOSED).doesNotContainKey(missed.id());
    }

    @Test
    void commitsOtherTransitionsWhenOneOpeningFailsAndRollsBackOnlyThatRace() {
        var due = createRace(2027, 1, "Race to close", NOW.minusSeconds(7200), NOW);
        prepareLineup(due);
        service.openRacePredictions(due.id());
        var before = createRace(2027, 2, "First valid opening", NOW.minusSeconds(30), NOW.plusSeconds(3600));
        var invalid = createRace(2028, 1, "Opening without season rules", NOW.minusSeconds(20), NOW.plusSeconds(5400));
        var after = createRace(2027, 3, "Next valid opening", NOW.minusSeconds(10), NOW.plusSeconds(7200));

        var result = service.processScheduledTransitions();

        assertThat(result).containsEntry(due.id(), RaceStatus.CLOSED)
                .containsEntry(before.id(), RaceStatus.OPEN)
                .containsEntry(after.id(), RaceStatus.OPEN)
                .doesNotContainKey(invalid.id());
        assertThat(service.getById(due.id()).status()).isEqualTo(RaceStatus.CLOSED);
        assertThat(service.getById(before.id()).status()).isEqualTo(RaceStatus.OPEN);
        assertThat(service.getById(after.id()).status()).isEqualTo(RaceStatus.OPEN);
        assertThat(service.getById(invalid.id()).status()).isEqualTo(RaceStatus.SCHEDULED);
        assertThat(lineupService.getForEditing(invalid.id()).prepared()).isFalse();
    }

    @Test
    void throwsWhenRaceDoesNotExist() throws Exception {
        var id = new RaceId(UUID.randomUUID());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(RaceNotFoundException.class);

        mvc.perform(patch("/adminApi/v1/races/{raceId}/open", id.value()).cookie(createSessionWithoutLeague()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    void doesNothingIfRaceIsAlreadyOpened() {
        var toOpen = createRace(2027, 1, "Bahrain Grand Prix", NOW, NOW.plusSeconds(3600));
        prepareLineup(toOpen);
        service.openRacePredictions(toOpen.id());

        service.openRacePredictions(toOpen.id());

        assertThat(service.getById(toOpen.id()).status()).isEqualTo(RaceStatus.OPEN);
    }

    @Test
    void doesNothingIfRaceIsAlreadyClosed() {
        var toClose = createRace(2027, 1, "Bahrain Grand Prix", NOW, NOW.plusSeconds(3600));
        prepareLineup(toClose);
        service.openRacePredictions(toClose.id());
        service.closeRacePredictions(toClose.id());

        service.closeRacePredictions(toClose.id());

        assertThat(service.getById(toClose.id()).status()).isEqualTo(RaceStatus.CLOSED);
    }

    @Test
    void syncCalendarCreatesRaceAndSprint() {
        var race = calendarEntry(RaceType.RACE, "Bahrain Grand Prix", NOW.plusSeconds(7200), NOW.plusSeconds(10800));
        var sprint = calendarEntry(RaceType.SPRINT, "Bahrain Grand Prix", NOW, NOW.plusSeconds(3600));
        when(calendarProvider.loadSeason(2027)).thenReturn(List.of(race, sprint));

        calendarSync.syncCalendar(2027);

        var races = service.findAllBySeason(2027);
        assertThat(races).hasSize(2);
        assertThat(races).anySatisfy(actual -> {
            assertThat(actual.type()).isEqualTo(RaceType.RACE);
            assertThat(actual.name()).isEqualTo(race.name());
            assertThat(actual.openAt()).isEqualTo(race.openAt());
            assertThat(actual.closeAt()).isEqualTo(race.closeAt());
            assertThat(actual.status()).isEqualTo(RaceStatus.SCHEDULED);
        });
        assertThat(races).anySatisfy(actual -> {
            assertThat(actual.type()).isEqualTo(RaceType.SPRINT);
            assertThat(actual.name()).isEqualTo(sprint.name());
            assertThat(actual.openAt()).isEqualTo(sprint.openAt());
            assertThat(actual.closeAt()).isEqualTo(sprint.closeAt());
            assertThat(actual.status()).isEqualTo(RaceStatus.SCHEDULED);
        });
    }

    @Test
    void syncCalendarUpdatesExistingRaceAndDoesNotCreateDuplicates() {
        var initial = calendarEntry(RaceType.RACE, "Old Grand Prix", NOW.plusSeconds(7200), NOW.plusSeconds(10800));
        when(calendarProvider.loadSeason(2027)).thenReturn(List.of(initial));
        calendarSync.syncCalendar(2027);
        var created = service.findAllBySeason(2027).getFirst();
        prepareLineup(created);
        service.openRacePredictions(created.id());
        var updated = calendarEntry(RaceType.RACE, "Updated Grand Prix", NOW.plusSeconds(14400), NOW.plusSeconds(18000));
        when(calendarProvider.loadSeason(2027)).thenReturn(List.of(updated));

        calendarSync.syncCalendar(2027);

        var races = service.findAllBySeason(2027);
        assertThat(races).hasSize(1);
        var actual = races.getFirst();
        assertThat(actual.id()).isEqualTo(created.id());
        assertThat(actual.status()).isEqualTo(RaceStatus.OPEN);
        assertThat(actual.name()).isEqualTo(updated.name());
        assertThat(actual.openAt()).isEqualTo(updated.openAt());
        assertThat(actual.closeAt()).isEqualTo(updated.closeAt());

        calendarSync.syncCalendar(2027);

        assertThat(service.findAllBySeason(2027)).hasSize(1);
    }

    @Test
    void rollsBackAllCalendarChangesWhenAnEntryIsInvalid() {
        var valid = calendarEntry(RaceType.RACE, "Valid race", NOW, NOW.plusSeconds(3600));
        var invalid = calendarEntry(RaceType.SPRINT, "Invalid race", NOW.plusSeconds(3600), NOW);
        when(calendarProvider.loadSeason(2027)).thenReturn(List.of(valid, invalid));

        assertThatThrownBy(() -> calendarSync.syncCalendar(2027))
                .isInstanceOf(DomainValidationException.class);

        assertThat(service.findAllBySeason(2027)).isEmpty();
    }

    @ParameterizedTest(name = "sprintsEnabled={0}")
    @ValueSource(booleans = {false, true})
    void includesSprintsOnlyWhenEnabledForLeague(boolean sprintsEnabled) throws Exception {
        createdLeagueId = new LeagueId(UUID.randomUUID());
        leagues.create(new League(
                createdLeagueId,
                "Sprint settings test league",
                new LeagueSource.Manual(),
                new LeagueSettings(null, sprintsEnabled, 2026),
                NOW
        ));
        var identity = new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
        var participant = participants.ensureParticipant(createdLeagueId, identity, "Test participant");
        createdUserId = participant.userId();
        var race = createRace(2027, 1, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var sprint = service.create(2027, 1, RaceType.SPRINT, "Grand Prix", NOW, NOW.plusSeconds(1800));

        var calendar = mvc.perform(get("/api/v1/leagues/{leagueId}/races", createdLeagueId.value())
                        .param("season", "2027")
                        .cookie(new Cookie(SessionCookie.NAME, sessions.create(createdUserId))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data").isArray());

        if (sprintsEnabled) {
            calendar.andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(sprint.id().value().toString()))
                    .andExpect(jsonPath("$.data[1].id").value(race.id().value().toString()));
        } else {
            calendar.andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(race.id().value().toString()));
        }
    }

    @Test
    void adminCalendarIncludesSprintsWithoutLeagueMembership() throws Exception {
        var race = createRace(2027, 1, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var sprint = service.create(2027, 1, RaceType.SPRINT, "Grand Prix", NOW, NOW.plusSeconds(1800));
        createRace(2026, 1, "Previous season", NOW, NOW.plusSeconds(3600));
        var cookie = createSessionWithoutLeague();

        mvc.perform(get("/adminApi/v1/races").param("season", "2027"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/adminApi/v1/races").param("season", "2027").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(sprint.id().value().toString()))
                .andExpect(jsonPath("$.data[0].type").value("SPRINT"))
                .andExpect(jsonPath("$.data[1].id").value(race.id().value().toString()))
                .andExpect(jsonPath("$.data[1].type").value("RACE"));

        mvc.perform(get("/adminApi/v1/races").param("season", "2028").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void adminCanOnlyOpenScheduledRaceAndCloseOpenRace() throws Exception {
        var race = createRace(2027, 1, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var cookie = createSessionWithoutLeague();

        mvc.perform(patch("/adminApi/v1/races/{raceId}/open", race.id().value()))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/adminApi/v1/races/{raceId}/close", race.id().value()).cookie(cookie))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value(containsString("current status: SCHEDULED")));
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.SCHEDULED);

        mvc.perform(patch("/adminApi/v1/races/{raceId}/open", race.id().value()).cookie(cookie))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("Prepare a lineup")));
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.SCHEDULED);

        prepareLineup(race);
        mvc.perform(patch("/adminApi/v1/races/{raceId}/open", race.id().value()).cookie(cookie))
                .andExpect(status().isOk());
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.OPEN);

        mvc.perform(patch("/adminApi/v1/races/{raceId}/close", race.id().value()))
                .andExpect(status().isUnauthorized());
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.OPEN);

        mvc.perform(patch("/adminApi/v1/races/{raceId}/close", race.id().value()).cookie(cookie))
                .andExpect(status().isOk());
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.CLOSED);

        mvc.perform(patch("/adminApi/v1/races/{raceId}/close", race.id().value()).cookie(cookie))
                .andExpect(status().isOk());

        mvc.perform(patch("/adminApi/v1/races/{raceId}/open", race.id().value()).cookie(cookie))
                .andExpect(status().isConflict());
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.CLOSED);
    }

    private Cookie createSessionWithoutLeague() {
        createdUserId = new UserId(UUID.randomUUID());
        var identity = new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
        users.create(new User(createdUserId, NOW), identity);
        rights.grant(createdUserId, Right.EDIT_RACE_RESULTS);
        return new Cookie(SessionCookie.NAME, sessions.create(createdUserId));
    }

    private Race createRace(int season, int round, String name, Instant openAt, Instant closeAt) {
        return service.create(season, round, RaceType.RACE, name, openAt, closeAt);
    }

    private void prepareLineup(Race race) {
        lineupService.addTeam(race.id(), new Team(
                new TeamBranding("McLaren", "MCL", "#FF8000", "#000000"),
                List.of(new Driver("Lando Norris"))
        ));
    }

    private RaceCalendarEntry calendarEntry(RaceType type, String name, Instant openAt, Instant closeAt) {
        return new RaceCalendarEntry(2027, 1, type, name, openAt, closeAt);
    }
}
