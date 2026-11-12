package com.xdd.f1.bootstrap.statistics;

import com.xdd.f1.application.league.*;
import com.xdd.f1.application.prediction.PredictionPointsRepository;
import com.xdd.f1.application.prediction.PredictionRepository;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.application.race.result.RaceResultRepository;
import com.xdd.f1.application.statistics.SeasonStats;
import com.xdd.f1.application.statistics.SeasonStatsService;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.league.*;
import com.xdd.f1.domain.prediction.*;
import com.xdd.f1.domain.race.*;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.result.RaceClassificationEntry;
import com.xdd.f1.domain.race.result.RaceDriverStatus;
import com.xdd.f1.domain.race.result.RaceResult;
import com.xdd.f1.domain.race.result.RaceResultValues;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import com.xdd.f1.infrastructure.persistence.race.RaceJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeasonStatsServiceIntegrationTest extends AbstractIntegrationTest {
    private final SeasonStatsService service;
    private final LeagueRepository leagues;
    private final LeagueParticipantService participants;
    private final RaceRepository races;
    private final PredictionRepository predictions;
    private final PredictionPointsRepository points;
    private final RaceResultRepository results;
    private final RaceJpaRepository raceJpaRepository;
    private final TransactionTemplate transactions;

    private League league;
    private League otherLeague;
    private LeagueParticipant alice;
    private LeagueParticipant bob;
    private LeagueParticipant carol;
    private LeagueParticipant dave;
    private LeagueParticipant other;

    @Autowired
    SeasonStatsServiceIntegrationTest(SeasonStatsService service, LeagueRepository leagues,
                                      LeagueParticipantService participants, RaceRepository races,
                                      PredictionRepository predictions, PredictionPointsRepository points,
                                      RaceResultRepository results, RaceJpaRepository raceJpaRepository,
                                      PlatformTransactionManager transactionManager) {
        this.service = service;
        this.leagues = leagues;
        this.participants = participants;
        this.races = races;
        this.predictions = predictions;
        this.points = points;
        this.results = results;
        this.raceJpaRepository = raceJpaRepository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        league = createLeague(true, 2023);
        otherLeague = createLeague(true, 2023);
        alice = participant(league, "Alice");
        bob = participant(league, "Bob");
        carol = participant(league, "Carol");
        dave = participant(league, "Dave");
        other = participant(otherLeague, "Other league participant");
    }

    @AfterEach
    void cleanUp() {
        raceJpaRepository.deleteAll();
    }

    @Test
    void calculatesAllOriginalMetricsFromSavedPointsPredictionsAndPublishedResults() {
        var first = race(2027, 1, RaceType.RACE, RaceStatus.FINISHED, "First Grand Prix");
        var second = race(2027, 2, RaceType.RACE, RaceStatus.FINISHED, "Second Grand Prix");
        publish(first, result("Alpha", "Gamma", 0, "5.000"));
        publish(second, result("Alpha", "Gamma", 1, "7.500"));
        savePrediction(first, alice, prediction("Alpha", List.of("Gamma", "Delta"), SafetyCarsCount.ZERO, "4.500"), 25);
        savePrediction(first, bob, prediction("Alpha", List.of("Gamma"), SafetyCarsCount.ONE, "5.100"), 18);
        savePrediction(first, carol, prediction("Gamma", List.of(), SafetyCarsCount.ZERO, "3.000"), 18);
        savePrediction(second, alice, prediction("Alpha", List.of("Delta"), SafetyCarsCount.ONE, "7.000"), 25);
        savePrediction(second, bob, prediction("Beta", List.of(), SafetyCarsCount.ZERO, null), 25);
        savePrediction(second, carol, prediction("Gamma", List.of("Gamma"), null, "8.000"), 0);
        savePrediction(second, dave, prediction("Zero", null, null, null), 0);
        savePrediction(first, other, prediction("OtherDriver", List.of("OtherDnf"), SafetyCarsCount.TWO, "1.000"), 100);

        var stats = get(league, alice, 2027);

        assertThat(stats.tenthPlacePointsRating()).containsExactly(
                driver("Alpha", 68), driver("Beta", 25), driver("Gamma", 18)
        );
        assertThat(stats.mostPickedForTenthPlace()).containsExactly(
                driver("Alpha", 3), driver("Gamma", 2), driver("Beta", 1), driver("Zero", 1)
        );
        assertThat(stats.tenthPickedButDnfed()).containsExactly(driver("Gamma", 2));
        assertThat(stats.driverOwnTenthPlacePoints()).containsExactly(
                driver("Alpha", 50), driver("P9", 36), driver("Gamma", 36), driver("P8", 30), driver("P7", 24)
        );
        assertThat(stats.tenthPlacePredictionEfficiency()).containsExactly(
                driver("Beta", 25), driver("Alpha", 22), driver("Gamma", 9)
        );
        assertThat(stats.mostDnfDrivers()).containsExactly(driver("Gamma", 2));
        assertThat(stats.mostPickedForDnf()).containsExactly(driver("Gamma", 3), driver("Delta", 2));
        assertThat(stats.tenthPlaceDnfAntiRating()).singleElement().satisfies(entry -> {
            assertThat(entry.participantId()).isEqualTo(carol.id());
            assertThat(entry.participantName()).isEqualTo("Carol");
            assertThat(entry.score()).isEqualTo(2);
        });
        assertThat(stats.closestLeadGapPredictions()).hasSize(5);
        assertThat(stats.closestLeadGapPredictions().getFirst()).satisfies(entry -> {
            assertThat(entry.participantId()).isEqualTo(bob.id());
            assertThat(entry.participantName()).isEqualTo("Bob");
            assertThat(entry.raceName()).isEqualTo("First Grand Prix");
            assertThat(entry.predictedGap()).isEqualByComparingTo("5.100");
            assertThat(entry.actualGap()).isEqualByComparingTo("5.000");
            assertThat(entry.difference()).isEqualByComparingTo("0.100");
        });
        assertThat(stats.closestLeadGapPredictions().getLast().difference()).isEqualByComparingTo("2.000");
        assertThat(stats.safetyCarPickCounts()).containsExactly(
                safetyCar(SafetyCarsCount.ZERO, 3), safetyCar(SafetyCarsCount.ONE, 2)
        );
        assertThat(stats.safetyCarActualCounts()).containsExactly(
                safetyCar(SafetyCarsCount.ZERO, 1), safetyCar(SafetyCarsCount.ONE, 1)
        );
        assertThat(stats.safetyCarCorrectGuesses()).containsExactly(
                safetyCar(SafetyCarsCount.ZERO, 2), safetyCar(SafetyCarsCount.ONE, 1)
        );
    }

    @Test
    void limitsDriverRatingsToTopFive() {
        var race = race(2027, 1, RaceType.RACE, RaceStatus.FINISHED, "Grand Prix");
        publish(race, new RaceResultValues(null, null, null));
        for (var index = 1; index <= 6; index++) {
            var participant = participant(league, "Participant " + index);
            savePrediction(race, participant, prediction("Driver" + index, null, null, null), index);
        }

        var stats = get(league, alice, 2027);

        assertThat(stats.tenthPlacePointsRating()).hasSize(5);
        assertThat(stats.tenthPlacePointsRating().getFirst()).isEqualTo(driver("Driver6", 6));
        assertThat(stats.mostPickedForTenthPlace()).hasSize(5);
        assertThat(stats.tenthPlacePredictionEfficiency()).hasSize(5);
    }

    @Test
    void excludesUnpublishedRacesAndIncludesHistoricalSprintsOnlyWithLeaguePredictions() {
        var historicalLeague = createLeague(false, 2023);
        var historicalAlice = participant(historicalLeague, "Historical Alice");
        var sprint = race(2027, 1, RaceType.SPRINT, RaceStatus.FINISHED, "Played Sprint");
        var regular = race(2027, 1, RaceType.RACE, RaceStatus.FINISHED, "Regular Grand Prix");
        var hiddenSprint = race(2027, 2, RaceType.SPRINT, RaceStatus.FINISHED, "Unplayed Sprint");
        var unpublished = race(2027, 3, RaceType.RACE, RaceStatus.FINISHED, "Unpublished Grand Prix");
        publish(sprint, result("Alpha", "Gamma", 1, "5.000"));
        publish(regular, result("Alpha", "Gamma", 0, "5.000"));
        publish(hiddenSprint, result("Beta", "Delta", 2, "5.000"));
        transactions.executeWithoutResult(status -> results.create(new RaceResult(
                unpublished.id(), new RulesVersion("legacy"), null, result("Hidden", "Gamma", 0, "5.000")
        )));
        savePrediction(sprint, historicalAlice, prediction("Alpha", null, SafetyCarsCount.ONE, "5.000"), 25);
        savePrediction(unpublished, historicalAlice, prediction("Hidden", null, SafetyCarsCount.ZERO, "5.000"), 100);

        var stats = get(historicalLeague, historicalAlice, 2027);

        assertThat(stats.mostPickedForTenthPlace()).containsExactly(driver("Alpha", 1));
        assertThat(stats.tenthPlacePointsRating()).containsExactly(driver("Alpha", 25));
        assertThat(stats.safetyCarActualCounts()).containsExactly(
                safetyCar(SafetyCarsCount.ONE, 1), safetyCar(SafetyCarsCount.ZERO, 1)
        );
        assertThat(stats.driverOwnTenthPlacePoints()).contains(driver("Alpha", 50));
        assertThat(stats.driverOwnTenthPlacePoints()).doesNotContain(driver("Beta", 25), driver("Hidden", 25));
    }

    @Test
    void returnsEmptyStatsBeforeFirstSeasonAndWithoutPublishedRacesAndChecksLeagueAccess() {
        var futureLeague = createLeague(true, 2024);
        var participant = participant(futureLeague, "Future participant");
        var scheduled = race(2027, 1, RaceType.RACE, RaceStatus.SCHEDULED, "Scheduled Grand Prix");
        savePrediction(scheduled, alice, prediction("Alpha", null, null, null), 25);

        assertThat(get(futureLeague, participant, 2023)).isEqualTo(SeasonStats.empty());
        assertThat(get(league, alice, 2026)).isEqualTo(SeasonStats.empty());
        assertThat(get(league, alice, 2027)).isEqualTo(SeasonStats.empty());
        assertThatThrownBy(() -> get(league, other, 2027)).isInstanceOf(LeagueParticipantNotFoundException.class);
    }

    private League createLeague(boolean sprintsEnabled, int firstSeason) {
        var created = new League(new LeagueId(UUID.randomUUID()), "Statistics league", new LeagueSource.Manual(),
                new LeagueSettings(null, sprintsEnabled, firstSeason), Instant.now());
        transactions.executeWithoutResult(status -> leagues.create(created));
        return created;
    }

    private LeagueParticipant participant(League league, String name) {
        return participants.ensureParticipant(league.id(),
                new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString()), name);
    }

    private Race race(int season, int round, RaceType type, RaceStatus status, String name) {
        var closeAt = Instant.parse(String.format("%04d-03-01T00:00:00Z", season))
                .plusSeconds(round * 86400L + (type == RaceType.RACE ? 3600 : 0));
        var race = new Race(new RaceId(UUID.randomUUID()), season, round, type, name, status,
                closeAt.minusSeconds(3600), closeAt);
        transactions.executeWithoutResult(state -> races.create(race));
        return race;
    }

    private void publish(Race race, RaceResultValues values) {
        transactions.executeWithoutResult(status -> results.create(new RaceResult(
                race.id(), new RulesVersion("legacy"), values, null
        )));
    }

    private RaceResultValues result(String tenth, String eleventh, int safetyCars, String firstPlaceLead) {
        var drivers = new ArrayList<>(List.of("P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8", "P9", tenth, eleventh));
        var classification = drivers.stream()
                .map(driver -> new RaceClassificationEntry(new Driver(driver),
                        driver.equals("Gamma") ? RaceDriverStatus.DNF : RaceDriverStatus.FINISHED))
                .toList();
        return new RaceResultValues(classification, safetyCars, new BigDecimal(firstPlaceLead));
    }

    private PredictionValues prediction(String tenth, List<String> dnfs, SafetyCarsCount safetyCars, String lead) {
        return new PredictionValues(
                tenth == null ? null : new Driver(tenth),
                dnfs == null ? null : new DnfPrediction(dnfs.stream().map(Driver::new).toList()),
                safetyCars,
                null,
                lead == null ? null : new BigDecimal(lead)
        );
    }

    private void savePrediction(Race race, LeagueParticipant participant, PredictionValues values, Integer tenthPlacePoints) {
        var prediction = new Prediction(new PredictionId(UUID.randomUUID()), participant.leagueId(), participant.id(), race.id(),
                values, null);
        var savedPoints = new PredictionPoints(prediction.id(), new RulesVersion("legacy"), tenthPlacePoints,
                tenthPlacePoints, null, null, null, null, null);
        transactions.executeWithoutResult(status -> {
            predictions.create(prediction);
            points.create(savedPoints);
        });
    }

    private com.xdd.f1.application.statistics.SeasonDriverStats driver(String name, int score) {
        return new com.xdd.f1.application.statistics.SeasonDriverStats(name, score);
    }

    private com.xdd.f1.application.statistics.SeasonSafetyCarStats safetyCar(SafetyCarsCount safetyCars, int score) {
        return new com.xdd.f1.application.statistics.SeasonSafetyCarStats(safetyCars, score);
    }

    private SeasonStats get(League league, LeagueParticipant participant, int season) {
        return service.getSeasonStats(participant.userId(), league.id(), season);
    }
}
