package com.xdd.f1.bootstrap.historyimport;

import com.xdd.f1.application.historyimport.HistoryImport;
import com.xdd.f1.application.historyimport.HistoryImportService;
import com.xdd.f1.application.historyimport.ImportConflictException;
import com.xdd.f1.application.league.*;
import com.xdd.f1.application.prediction.*;
import com.xdd.f1.application.race.RaceNotFoundException;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.application.race.lineup.RaceLineupRepository;
import com.xdd.f1.application.race.lineup.RaceLineupNotFoundException;
import com.xdd.f1.application.race.result.RaceResultRepository;
import com.xdd.f1.application.standings.SeasonPodiumRepository;
import com.xdd.f1.application.standings.SeasonStandingsService;
import com.xdd.f1.application.statistics.SeasonStatsService;
import com.xdd.f1.application.user.UserRepository;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.contract.historyimport.ImportRequestDto;
import com.xdd.f1.contract.prediction.SafetyCarsCountDto;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.*;
import com.xdd.f1.domain.prediction.*;
import com.xdd.f1.domain.race.*;
import com.xdd.f1.domain.race.lineup.*;
import com.xdd.f1.domain.race.result.*;
import com.xdd.f1.domain.user.*;
import com.xdd.f1.infrastructure.persistence.race.RaceJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.xdd.f1.application.historyimport.HistoryImport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

class HistoryImportServiceIntegrationTest extends AbstractIntegrationTest {
    private static final Instant CREATED = Instant.parse("2026-11-12T12:00:00Z");

    private final HistoryImportService service;
    private final LeagueRepository leagues;
    private final LeagueParticipantRepository participants;
    private final LeagueParticipantService participantService;
    private final UserRepository users;
    private final RaceRepository races;
    private final RaceLineupRepository lineups;
    private final PredictionConditionsRepository conditions;
    private final PredictionRepository predictions;
    private final RaceResultRepository results;
    private final SeasonPodiumRepository podiums;
    private final SeasonStandingsService standings;
    private final SeasonStatsService statistics;
    private final RaceJpaRepository raceJpa;
    private final JsonMapper json;
    private final TransactionTemplate transactions;

    @MockitoSpyBean
    private PredictionPointsRepository points;

    private League league;
    private UserEntry alice;

    @Autowired
    HistoryImportServiceIntegrationTest(HistoryImportService service, LeagueRepository leagues, LeagueParticipantRepository participants,
                                       LeagueParticipantService participantService, UserRepository users, RaceRepository races,
                                       RaceLineupRepository lineups, PredictionConditionsRepository conditions, PredictionRepository predictions,
                                       RaceResultRepository results, SeasonPodiumRepository podiums, SeasonStandingsService standings,
                                       SeasonStatsService statistics, RaceJpaRepository raceJpa, JsonMapper json,
                                       PlatformTransactionManager transactionManager) {
        this.service = service;
        this.leagues = leagues;
        this.participants = participants;
        this.participantService = participantService;
        this.users = users;
        this.races = races;
        this.lineups = lineups;
        this.conditions = conditions;
        this.predictions = predictions;
        this.results = results;
        this.podiums = podiums;
        this.standings = standings;
        this.statistics = statistics;
        this.raceJpa = raceJpa;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        league = new League(new LeagueId(UUID.randomUUID()), "Existing Discord league",
                new LeagueSource.Discord(positiveId()), new LeagueSettings("123456789012345678", false, 2026), CREATED);
        transactions.executeWithoutResult(status -> leagues.create(league));
        service.importLeagueSettings(league.id(), new LeagueSettings("123456789012345678", false, 2023));
        alice = user("Alice");
    }

    @AfterEach
    void cleanUp() {
        raceJpa.deleteAll();
    }

    @Test
    void imports2023HistoryWithOriginalIdsAndResolvesLoginWithoutCreatingAnotherUser() {
        var race = race(2023, 2, RaceType.RACE);
        var lineup = lineup(race);
        var prediction = prediction(race, alice, null, null, null, null);
        var result = result(race, List.of(new PointsEntry(alice.user().id(), 30, 25, 5, null, null, null, null)));

        assertThat(service.importUsers(league.id(), List.of(alice)).created()).isEqualTo(1);
        assertThat(service.importUsers(league.id(), List.of(alice)).existing()).isEqualTo(1);
        assertThat(service.importRaces(league.id(), List.of(race)).created()).isEqualTo(1);
        assertThat(service.importLineups(league.id(), List.of(new LineupEntry(lineup, null))).created()).isEqualTo(1);
        assertThat(service.importPredictions(league.id(), List.of(prediction)).created()).isEqualTo(1);
        assertThat(service.importResults(league.id(), List.of(result)).pointsDifferences()).isEmpty();

        var participant = participantService.ensureParticipant(league.id(), alice.identity(), "Alice renamed");
        assertThat(participant.userId()).isEqualTo(alice.user().id());
        assertThat(participant.joinedAt()).isEqualTo(alice.joinedAt());
        transactions.executeWithoutResult(status -> {
            var saved = predictions.findAllByRaceId(race.id()).getFirst();
            assertThat(users.findByIdentity(alice.identity()).orElseThrow()).isEqualTo(alice.user());
            assertThat(races.findById(race.id()).orElseThrow()).isEqualTo(race);
            assertThat(conditions.findByRaceId(race.id())).isEmpty();
            assertThat(saved.teamsPickedDrivers()).isNull();
            assertThat(saved.values().driverPositionPrediction()).isNull();
            assertThat(saved.values().dnfPrediction().dnfPickedDrivers()).containsExactly(new Driver("De Vries"));
            assertThat(points.findByPredictionId(saved.id()).orElseThrow().totalPoints()).isEqualTo(30);
            assertThat(results.findByRaceId(race.id()).orElseThrow()).isEqualTo(result.result());
        });
        var table = standings.getStandings(alice.user().id(), league.id(), 2023);
        assertThat(table.rows()).singleElement().satisfies(row -> assertThat(row.totalPoints()).isEqualTo(30));
        assertThat(table.events()).containsExactly(race);
        assertThat(table.charts().getFirst().points()).containsExactly(0, 30);
        assertThat(statistics.getSeasonStats(alice.user().id(), league.id(), 2023)).isNotNull();
        assertThat(service.importRaces(league.id(), List.of(race)).existing()).isEqualTo(1);
        assertThat(service.importLineups(league.id(), List.of(new LineupEntry(lineup, null))).existing()).isEqualTo(1);
        assertThat(service.importPredictions(league.id(), List.of(prediction)).existing()).isEqualTo(1);
        assertThat(service.importResults(league.id(), List.of(result)).existing()).isEqualTo(2);
        assertThat(service.importPodiums(league.id(), List.of(new PodiumEntry(2023, List.of(alice.user().id())))).created()).isEqualTo(1);
        assertThat(service.importPodiums(league.id(), List.of(new PodiumEntry(2023, List.of(alice.user().id())))).existing()).isEqualTo(1);
        transactions.executeWithoutResult(status -> assertThat(podiums.findAllByLeagueBeforeSeason(league.id(), 2024)).hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({"2024, SPRINT, 45", "2025, SPRINT, 13", "2026, RACE, 45"})
    void preservesHistoricalSprintsNullableCategoriesAndOriginalPositionConditions(int season, RaceType type, int total) {
        service.importUsers(league.id(), List.of(alice));
        var race = race(season, 2, type);
        var lineup = lineup(race);
        var picked = season < 2026 ? lineup.teams().stream().map(team -> team.drivers().getFirst().name()).toList() : List.<String>of();
        var raceConditions = season == 2026 ? new PredictionConditions(race.id(), new Driver("Driver 2")) : null;
        var value = ImportDtoMapper.prediction(new ImportRequestDto.Prediction(race.id().value(), alice.user().id().value(), "Driver 10",
                List.of(), SafetyCarsCountDto.ZERO, season == 2026 ? 2 : null, new BigDecimal("10.1313"), picked));
        service.importRaces(league.id(), List.of(race));
        service.importLineups(league.id(), List.of(new LineupEntry(lineup, raceConditions)));
        service.importPredictions(league.id(), List.of(value));
        var pointsEntry = new PointsEntry(alice.user().id(), total, 25, 0, 5, 5, season < 2026 ? 10 : null, season == 2026 ? 10 : null);
        var result = result(race, List.of(pointsEntry));
        assertThat(service.importResults(league.id(), List.of(result)).pointsDifferences()).isEmpty();

        transactions.executeWithoutResult(status -> {
            var saved = predictions.findAllByRaceId(race.id()).getFirst();
            assertThat(saved.values().dnfPrediction().dnfPickedDrivers()).isEmpty();
            assertThat(saved.values().firstPlaceLeadPrediction()).isEqualByComparingTo("10.131");
            assertThat(saved.teamsPickedDrivers()).isEqualTo(picked);
            assertThat(conditions.findByRaceId(race.id()).orElse(null)).isEqualTo(raceConditions);
            assertThat(points.findByPredictionId(saved.id()).orElseThrow().totalPoints()).isEqualTo(total);
        });
        assertThat(standings.getStandings(alice.user().id(), league.id(), season).rows().getFirst().totalPoints()).isEqualTo(total);
        assertThat(service.importLineups(league.id(), List.of(new LineupEntry(lineup, raceConditions))).created()).isZero();
    }

    @Test
    void reportsCalculationDifferencesButPreservesImportedPoints() {
        service.importUsers(league.id(), List.of(alice));
        var race = prepareRace(2023, 2);
        service.importPredictions(league.id(), List.of(prediction(race, alice, null, null, null, null)));
        var result = result(race, List.of(new PointsEntry(alice.user().id(), 25, 25, 0, null, null, null, null)));
        var report = service.importResults(league.id(), List.of(result));
        assertThat(report.pointsDifferences()).singleElement().satisfies(difference -> {
            assertThat(difference.imported().totalPoints()).isEqualTo(25);
            assertThat(difference.calculated().totalPoints()).isEqualTo(30);
        });
        transactions.executeWithoutResult(status -> {
            var prediction = predictions.findAllByRaceId(race.id()).getFirst();
            assertThat(points.findByPredictionId(prediction.id()).orElseThrow().totalPoints()).isEqualTo(25);
        });
    }

    @Test
    void rejectsConflictingIdentitiesAndDoesNotPartiallyImportUserBatches() {
        service.importUsers(league.id(), List.of(alice));
        var bob = user("Bob");
        var conflict = new UserEntry(new User(new UserId(UUID.randomUUID()), CREATED), alice.identity(), "Conflict", CREATED);
        assertThatThrownBy(() -> service.importUsers(league.id(), List.of(bob, conflict))).isInstanceOf(ImportConflictException.class);
        transactions.executeWithoutResult(status -> assertThat(users.findByIdentity(bob.identity())).isEmpty());
        var changed = new UserEntry(alice.user(), alice.identity(), "Different name", alice.joinedAt());
        assertThatThrownBy(() -> service.importUsers(league.id(), List.of(changed))).isInstanceOf(ImportConflictException.class);
        assertThatThrownBy(() -> service.importUsers(league.id(), List.of(bob, bob))).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsRaceKeyConflictsMissingReferencesAndChangedHistoricalData() {
        var race = race(2023, 2, RaceType.RACE);
        var missing = lineup(race);
        assertThatThrownBy(() -> service.importLineups(league.id(), List.of(new LineupEntry(missing, null))))
                .isInstanceOf(RaceNotFoundException.class);
        service.importRaces(league.id(), List.of(race));
        var otherId = new Race(new RaceId(UUID.randomUUID()), race.season(), race.round(), race.type(), race.name(), race.status(), race.openAt(), race.closeAt());
        assertThatThrownBy(() -> service.importRaces(league.id(), List.of(otherId))).isInstanceOf(ImportConflictException.class);
        assertThatThrownBy(() -> service.importRaces(league.id(), List.of(race.rename("Changed")))).isInstanceOf(ImportConflictException.class);
        assertThatThrownBy(() -> service.importPredictions(league.id(), List.of(prediction(race, alice, null, null, null, null))))
                .isInstanceOf(RaceLineupNotFoundException.class);
        service.importLineups(league.id(), List.of(new LineupEntry(missing, null)));
        service.importUsers(league.id(), List.of(alice));
        var invalid = new PredictionEntry(race.id(), alice.user().id(), new PredictionValues(new Driver("Not in lineup"), null, null, null, null), null);
        assertThatThrownBy(() -> service.importPredictions(league.id(), List.of(invalid))).isInstanceOf(DomainValidationException.class);
        transactions.executeWithoutResult(status -> assertThat(predictions.findAllByRaceId(race.id())).isEmpty());
    }

    @Test
    void rollsBackResultAndAllPointsWhenAWriteFails() {
        var bob = user("Bob");
        service.importUsers(league.id(), List.of(alice, bob));
        var race = prepareRace(2023, 2);
        service.importPredictions(league.id(), List.of(prediction(race, alice, null, null, null, null), prediction(race, bob, null, null, null, null)));
        var result = result(race, List.of(new PointsEntry(alice.user().id(), 30, 25, 5, null, null, null, null),
                new PointsEntry(bob.user().id(), 30, 25, 5, null, null, null, null)));
        var calls = new AtomicInteger();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (calls.incrementAndGet() == 2) {
                throw new IllegalStateException("Interrupted import");
            }
            return null;
        }).when(points).create(any());

        assertThatThrownBy(() -> service.importResults(league.id(), List.of(result)))
                .hasRootCauseInstanceOf(IllegalStateException.class).hasRootCauseMessage("Interrupted import");
        transactions.executeWithoutResult(status -> {
            assertThat(results.findByRaceId(race.id())).isEmpty();
            assertThat(points.findAllByRaceId(race.id())).isEmpty();
            assertThat(predictions.findAllByRaceId(race.id())).hasSize(2);
            assertThat(races.findById(race.id()).orElseThrow().status()).isEqualTo(RaceStatus.FINISHED);
        });
        assertThat(service.importResults(league.id(), List.of(result)).created()).isEqualTo(3);
    }

    @Test
    void rejectsPartialResultsAndAnUnfinishedSeasonPodium() {
        service.importUsers(league.id(), List.of(alice));
        var race = prepareRace(2026, 20);
        var prediction = new PredictionEntry(race.id(), alice.user().id(), new PredictionValues(null, null, null, null, null), List.of());
        service.importPredictions(league.id(), List.of(prediction));
        assertThatThrownBy(() -> service.importResults(league.id(), List.of(result(race, List.of()))))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("cover every");
        var result = result(race, List.of(new PointsEntry(alice.user().id(), 0, null, null, null, null, null, null)));
        service.importResults(league.id(), List.of(result));
        var next = race(2026, 21, RaceType.RACE);
        var scheduled = new Race(next.id(), next.season(), next.round(), next.type(), next.name(), RaceStatus.SCHEDULED, next.openAt(), next.closeAt());
        service.importRaces(league.id(), List.of(scheduled));
        assertThatThrownBy(() -> service.importPodiums(league.id(), List.of(new PodiumEntry(2026, List.of(alice.user().id())))))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("FINISHED");
        transactions.executeWithoutResult(status -> assertThat(podiums.findAllByLeagueBeforeSeason(league.id(), 2027)).isEmpty());
    }

    @Test
    void acceptsTypedJsonBatchesAndRequiresAnExplicitApiKey() {
        var request = json.readValue("""
                {"leagueId":"%s","data":[{"id":"%s","discordId":"123456789012345678",
                  "displayName":"Historical user","createdAt":"2026-11-12T12:00:00Z","joinedAt":"2026-11-12T12:00:00Z"}]}
                """.formatted(league.id().value(), UUID.randomUUID()), new TypeReference<ImportRequestDto<ImportRequestDto.User>>() {});
        assertThat(ImportDtoMapper.list(request.data(), "data", ImportDtoMapper::user)).hasSize(1);
        var interceptor = new ImportApiKeyInterceptor(new ImportProperties("test-import-key"));
        var http = new MockHttpServletRequest("POST", "/importApi/v1/users");
        assertThat(interceptor.preHandle(http, new MockHttpServletResponse(), new Object())).isFalse();
        http.addHeader("X-Import-Api-Key", "wrong-key");
        assertThat(interceptor.preHandle(http, new MockHttpServletResponse(), new Object())).isFalse();
        http.removeHeader("X-Import-Api-Key");
        http.addHeader("X-Import-Api-Key", "test-import-key");
        assertThat(interceptor.preHandle(http, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(new ImportApiKeyInterceptor(new ImportProperties("")).preHandle(http, new MockHttpServletResponse(), new Object())).isFalse();
        assertThatThrownBy(() -> ImportDtoMapper.prediction(new ImportRequestDto.Prediction(UUID.randomUUID(), UUID.randomUUID(), null,
                null, null, null, new BigDecimal("-0.0001"), null))).isInstanceOf(DomainValidationException.class);
    }

    private UserEntry user(String name) {
        return ImportDtoMapper.user(new ImportRequestDto.User(UUID.randomUUID(), String.valueOf(positiveId()), name, CREATED, CREATED));
    }

    private long positiveId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }

    private Race prepareRace(int season, int round) {
        var race = race(season, round, RaceType.RACE);
        service.importRaces(league.id(), List.of(race));
        service.importLineups(league.id(), List.of(new LineupEntry(lineup(race), null)));
        return race;
    }

    private Race race(int season, int round, RaceType type) {
        var close = Instant.parse(season + "-03-19T15:00:00Z").plusSeconds(round * 86400L);
        return new Race(new RaceId(UUID.randomUUID()), season, round, type, "Historical round " + round,
                RaceStatus.FINISHED, close.minusSeconds(86400), close);
    }

    private RaceLineup lineup(Race race) {
        var drivers = IntStream.rangeClosed(1, 20).mapToObj(index -> new Driver(index == 20 ? "De Vries" : "Driver " + index)).toList();
        var teams = IntStream.range(0, 10).mapToObj(index -> new Team(new TeamBranding("Historical team " + index, "T" + index,
                "#123456", "#FFFFFF"), drivers.subList(index * 2, index * 2 + 2))).toList();
        return new RaceLineup(race.id(), teams);
    }

    private PredictionEntry prediction(Race race, UserEntry user, SafetyCarsCount safety, Integer position, BigDecimal gap, List<String> teams) {
        return new PredictionEntry(race.id(), user.user().id(), new PredictionValues(new Driver("Driver 10"),
                new DnfPrediction(List.of(new Driver("De Vries"))), safety, position, gap), teams);
    }

    private ResultEntry result(Race race, List<PointsEntry> points) {
        var classification = IntStream.rangeClosed(1, 20).mapToObj(index -> new RaceClassificationEntry(
                new Driver(index == 20 ? "De Vries" : "Driver " + index), switch (index) {
                    case 18 -> RaceDriverStatus.DQ;
                    case 19 -> RaceDriverStatus.DNS;
                    case 20 -> RaceDriverStatus.DNF;
                    default -> RaceDriverStatus.FINISHED;
                })).toList();
        return new ResultEntry(new RaceResult(race.id(), new RulesVersion(String.valueOf(race.season())),
                new RaceResultValues(classification, race.season() == 2023 ? null : 0,
                        race.season() == 2023 ? null : new BigDecimal("10.131")), null), points);
    }
}
