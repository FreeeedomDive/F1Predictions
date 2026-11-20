package com.xdd.f1.bootstrap.championship;

import com.xdd.f1.application.championship.ChampionshipPredictionService;
import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.application.race.lineup.RaceLineupRepository;
import com.xdd.f1.application.rights.RightsService;
import com.xdd.f1.application.session.SessionService;
import com.xdd.f1.bootstrap.auth.SessionCookie;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.league.League;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.league.LeagueSettings;
import com.xdd.f1.domain.league.LeagueSource;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.RaceLineup;
import com.xdd.f1.domain.race.lineup.Team;
import com.xdd.f1.domain.race.lineup.TeamBranding;
import com.xdd.f1.domain.rights.Right;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.xdd.f1.domain.championship.ChampionshipRulesVersion.V2027;
import static com.xdd.f1.domain.championship.ChampionshipStage.PRE_SEASON;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "internal-api.service-key=championship-test-key")
class ChampionshipApiIntegrationTest extends AbstractIntegrationTest {
    private static final int SEASON = 2029;
    private static final Instant START = Instant.parse("2029-03-01T12:00:00Z");
    private static final String BEARER = "Bearer championship-test-key";
    private static final String PARTICIPANT_HEADER = "F1P-ParticipantId";
    private static final String ADMIN = "/adminApi/v1/championship/" + SEASON;
    private static final String PRE_PREDICTION = "/predictions/PRE_SEASON";
    private static final String ORDER = "{\"driverStandings\":[\"Norris\",\"Verstappen\"]}";
    private static final String REVERSE_ORDER = "{\"driverStandings\":[\"Verstappen\",\"Norris\"]}";

    private final MockMvc mvc;
    private final LeagueRepository leagues;
    private final LeagueParticipantService participants;
    private final SessionService sessions;
    private final RightsService rights;
    private final RaceService races;
    private final RaceLineupRepository lineups;
    private final ChampionshipPredictionService predictions;
    private final TransactionTemplate transactions;
    private final JdbcClient jdbc;
    private final Clock clock;

    @Autowired
    ChampionshipApiIntegrationTest(MockMvc mvc, LeagueRepository leagues, LeagueParticipantService participants,
                                   SessionService sessions, RightsService rights, RaceService races,
                                   RaceLineupRepository lineups, ChampionshipPredictionService predictions,
                                    PlatformTransactionManager transactionManager, JdbcClient jdbc, Clock clock) {
        this.mvc = mvc;
        this.leagues = leagues;
        this.participants = participants;
        this.sessions = sessions;
        this.rights = rights;
        this.races = races;
        this.lineups = lineups;
        this.predictions = predictions;
        this.transactions = new TransactionTemplate(transactionManager);
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM championship_seasons WHERE season = :season").param("season", SEASON).update();
        jdbc.sql("DELETE FROM races WHERE season = :season AND name = 'Championship API race'").param("season", SEASON).update();
    }

    @Test
    void requiresTheConfiguredAuthenticationAndALeagueParticipantOnBothUserRoutes() throws Exception {
        var member = createParticipant(createLeague(), "Member");
        var outsider = createParticipant(createLeague(), "Outsider");
        var internal = internalUrl(member.leagueId());
        var nativeUrl = nativeUrl(member.leagueId());

        mvc.perform(get(nativeUrl).header(PARTICIPANT_HEADER, member.id().value())).andExpect(status().isUnauthorized());
        mvc.perform(get(nativeUrl).cookie(cookie(outsider))).andExpect(status().isNotFound());
        mvc.perform(get(internal).header(PARTICIPANT_HEADER, member.id().value())).andExpect(status().isUnauthorized());
        mvc.perform(get(internal).header(HttpHeaders.AUTHORIZATION, "Bearer wrong")
                        .header(PARTICIPANT_HEADER, member.id().value()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(internal).header(HttpHeaders.AUTHORIZATION, BEARER)).andExpect(status().isBadRequest());
        mvc.perform(put(internal + PRE_PREDICTION).header(HttpHeaders.AUTHORIZATION, BEARER)
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isBadRequest());
        mvc.perform(get(internal).header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header(PARTICIPANT_HEADER, outsider.id().value()))
                .andExpect(status().isNotFound());
    }

    @Test
    void sharesTheSamePredictionAcrossApisWithoutExposingOtherOpenPredictions() throws Exception {
        var league = createLeague();
        var member = createParticipant(league, "Member");
        var other = createParticipant(league, "Other");
        var cookie = cookie(member);
        var nativeUrl = nativeUrl(league);
        var internal = internalUrl(league);

        mvc.perform(get(nativeUrl).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.driverStandings").isEmpty())
                .andExpect(jsonPath("$.preSeasonPrediction").value(nullValue()))
                .andExpect(jsonPath("$.rows").isEmpty());
        predictions.configure(SEASON, V2027);
        predictions.openStage(SEASON, PRE_SEASON, createLineup());
        mvc.perform(put(nativeUrl + PRE_PREDICTION).cookie(cookie)
                        .header(PARTICIPANT_HEADER, other.id().value()).param("participantId", other.id().value().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictionSource").value("USER"));

        mvc.perform(get(internal).header(HttpHeaders.AUTHORIZATION, BEARER).header(PARTICIPANT_HEADER, other.id().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preSeasonPrediction").value(nullValue()))
                .andExpect(jsonPath("$.rows").isEmpty());
        mvc.perform(get(internal).header(HttpHeaders.AUTHORIZATION, BEARER).header(PARTICIPANT_HEADER, member.id().value()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.rulesVersion").value("championship-v2027"))
                .andExpect(jsonPath("$.currentStage").value("PRE_SEASON"))
                .andExpect(jsonPath("$.predictionsOpen").value(true))
                .andExpect(jsonPath("$.preSeasonRoster", contains("Norris", "Verstappen")))
                .andExpect(jsonPath("$.preSeasonPrediction.driverStandings", contains("Norris", "Verstappen")))
                .andExpect(jsonPath("$.preSeasonPrediction.points").isEmpty());
        mvc.perform(put(internal + PRE_PREDICTION).header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header(PARTICIPANT_HEADER, member.id().value())
                        .contentType(MediaType.APPLICATION_JSON).content(REVERSE_ORDER))
                .andExpect(status().isOk());
        mvc.perform(get(nativeUrl).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preSeasonPrediction.driverStandings", contains("Verstappen", "Norris")))
                .andExpect(jsonPath("$.midSeasonPrediction").value(nullValue()));

        mvc.perform(put(nativeUrl + PRE_PREDICTION).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"driverStandings\":[\"Norris\",\"Norris\"]}"))
                .andExpect(status().isBadRequest());
        predictions.closeStage(SEASON, PRE_SEASON);
        mvc.perform(put(nativeUrl + PRE_PREDICTION).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAMPIONSHIP_PREDICTIONS_CLOSED"));
        mvc.perform(put(internal + PRE_PREDICTION).header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header(PARTICIPANT_HEADER, member.id().value()).contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAMPIONSHIP_PREDICTIONS_CLOSED"));
    }

    @Test
    void exposesAdministrationAndPredictionStatusesOnlyWithTheChampionshipRight() throws Exception {
        var league = createLeague();
        var copy = createParticipant(league, "Copy");
        var own = createParticipant(league, "Own");
        createParticipant(league, "Pending");
        var admin = createParticipant(createLeague(), "Administrator");
        var cookie = cookie(admin);
        var lineupRace = createLineup();
        var lineupBody = "{\"lineupRaceId\":\"" + lineupRace.value() + "\"}";
        var configBody = "{\"rulesVersion\":\"championship-v2027\"}";

        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        rights.grant(admin.userId(), Right.EDIT_RACE_RESULTS);
        mvc.perform(put(ADMIN + "/configuration").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(configBody))
                .andExpect(status().isForbidden());
        mvc.perform(put(ADMIN + "/standings").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isForbidden());
        mvc.perform(get(ADMIN + "/stage-roster-races").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN + "/prediction-statuses").cookie(cookie).param("leagueId", league.value().toString()))
                .andExpect(status().isForbidden());

        rights.grant(admin.userId(), Right.EDIT_CHAMPIONSHIP);
        races.create(SEASON, 2, RaceType.RACE, "Championship API race", START, START.plusSeconds(3600));
        mvc.perform(get(ADMIN + "/stage-roster-races").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", contains(lineupRace.value().toString())));
        mvc.perform(get(ADMIN).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false));
        mvc.perform(put(ADMIN + "/configuration").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rulesVersion\":\"unknown\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(ADMIN + "/configuration").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(configBody))
                .andExpect(status().isNoContent());
        mvc.perform(put(ADMIN + "/stages/PRE_SEASON/open").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(lineupBody))
                .andExpect(status().isNoContent());
        predictions.save(league, SEASON, copy.id(), PRE_SEASON, List.of("Norris", "Verstappen"));
        mvc.perform(put(ADMIN + "/standings").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(REVERSE_ORDER))
                .andExpect(status().isNoContent());
        mvc.perform(get(ADMIN).cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.driverStandings", contains("Verstappen", "Norris")))
                .andExpect(jsonPath("$.predictionsOpen").value(true));
        mvc.perform(put(ADMIN + "/stages/PRE_SEASON/close").cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(put(ADMIN + "/stages/MID_SEASON/open").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(lineupBody))
                .andExpect(status().isNoContent());
        mvc.perform(put(nativeUrl(league) + "/predictions/MID_SEASON").cookie(cookie(own))
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isOk());
        mvc.perform(get(ADMIN + "/prediction-statuses").cookie(cookie).param("leagueId", league.value().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].displayName").value("Copy"))
                .andExpect(jsonPath("$.data[0].hasPreSeasonPrediction").value(true))
                .andExpect(jsonPath("$.data[0].midSeasonPredictionSource").value(nullValue()))
                .andExpect(jsonPath("$.data[1].midSeasonPredictionSource").value("USER"))
                .andExpect(jsonPath("$.data[2].hasPreSeasonPrediction").value(false))
                .andExpect(jsonPath("$.data[2].midSeasonPredictionSource").value(nullValue()));
        mvc.perform(put(ADMIN + "/stages/MID_SEASON/close").cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(get(ADMIN + "/prediction-statuses").cookie(cookie).param("leagueId", league.value().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].midSeasonPredictionSource").value("PRE_SEASON_COPY"))
                .andExpect(jsonPath("$.data[1].midSeasonPredictionSource").value("USER"));
        mvc.perform(get(nativeUrl(league)).cookie(cookie(copy))).andExpect(status().isOk())
                .andExpect(jsonPath("$.midSeasonPrediction.predictionSource").value("PRE_SEASON_COPY"))
                .andExpect(jsonPath("$.rows[0].driverStandings").doesNotExist());
    }

    @Test
    void distinguishesCurrentAndFormerDriversWithoutUsingFutureLineups() throws Exception {
        var member = createParticipant(createLeague(), "Member");
        predictions.configure(SEASON, V2027);
        var now = clock.instant();
        createLineup(1, now.minus(14, ChronoUnit.DAYS), "OLD", List.of(new Driver("Norris"), new Driver("Verstappen")));
        createLineup(2, now.minus(7, ChronoUnit.DAYS), "NEW", List.of(new Driver("Norris"), new Driver("Piastri")));
        createLineup(3, now.plus(7, ChronoUnit.DAYS), "FUT", List.of(new Driver("Norris"), new Driver("Russell")));

        mvc.perform(get(nativeUrl(member.leagueId())).cookie(cookie(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentDrivers", contains("Norris", "Piastri")))
                .andExpect(jsonPath("$.teams", hasSize(2)))
                .andExpect(jsonPath("$.teams[0].badge").value("NEW"))
                .andExpect(jsonPath("$.teams[0].drivers", contains("Norris", "Piastri")))
                .andExpect(jsonPath("$.teams[1].badge").value("OLD"))
                .andExpect(jsonPath("$.teams[1].drivers", contains("Verstappen")));
    }

    @Test
    void usesTheEarliestPreparedLineupBeforeTheFirstRace() throws Exception {
        var member = createParticipant(createLeague(), "Member");
        predictions.configure(SEASON, V2027);
        var now = clock.instant();
        createLineup(1, now.plus(7, ChronoUnit.DAYS), "TEM", List.of(new Driver("Norris"), new Driver("Verstappen")));
        createLineup(2, now.plus(14, ChronoUnit.DAYS), "FUT", List.of(new Driver("Norris"), new Driver("Piastri")));

        mvc.perform(get(nativeUrl(member.leagueId())).cookie(cookie(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentDrivers", contains("Norris", "Verstappen")))
                .andExpect(jsonPath("$.teams", hasSize(1)))
                .andExpect(jsonPath("$.teams[0].badge").value("TEM"));
    }

    private LeagueId createLeague() {
        var league = new League(new LeagueId(UUID.randomUUID()), "Championship API league", new LeagueSource.Manual(),
                new LeagueSettings(null, false, SEASON), START);
        transactions.executeWithoutResult(status -> leagues.create(league));
        return league.id();
    }

    private LeagueParticipant createParticipant(LeagueId league, String name) {
        return participants.ensureParticipant(league, new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString()), name);
    }

    private RaceId createLineup() {
        return createLineup(1, START.plusSeconds(3600), "TEM", List.of(new Driver("Norris"), new Driver("Verstappen")));
    }

    private RaceId createLineup(int round, Instant closeAt, String badge, List<Driver> drivers) {
        var race = races.create(SEASON, round, RaceType.RACE, "Championship API race", closeAt.minusSeconds(3600), closeAt);
        var team = new Team(new TeamBranding("Team " + badge, badge, "#112233", "#FFFFFF"), drivers);
        transactions.executeWithoutResult(status -> lineups.createIfAbsent(new RaceLineup(race.id(), List.of(team))));
        return race.id();
    }

    private Cookie cookie(LeagueParticipant participant) {
        return new Cookie(SessionCookie.NAME, sessions.create(participant.userId()));
    }

    private String nativeUrl(LeagueId league) {
        return "/api/v1/leagues/" + league.value() + "/championship/" + SEASON;
    }

    private String internalUrl(LeagueId league) {
        return "/internal/v1/leagues/" + league.value() + "/championship/" + SEASON;
    }
}
