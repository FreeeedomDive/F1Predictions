package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueService;
import com.xdd.f1.application.prediction.PredictionPointsRepository;
import com.xdd.f1.application.prediction.PredictionRepository;
import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.application.race.result.RaceResultRepository;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.prediction.*;
import com.xdd.f1.domain.race.*;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.result.*;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import com.xdd.f1.infrastructure.persistence.race.RaceJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RaceResultRepositoryIntegrationTest extends AbstractIntegrationTest {
    private static final RulesVersion RULES_2027 = new RulesVersion("2027-v1");

    private final RaceResultRepository results;
    private final PredictionPointsRepository points;
    private final PredictionRepository predictions;
    private final LeagueService leagues;
    private final LeagueParticipantService participants;
    private final RaceService races;
    private final RaceJpaRepository raceJpaRepository;
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    @Autowired
    RaceResultRepositoryIntegrationTest(
            RaceResultRepository results,
            PredictionPointsRepository points,
            PredictionRepository predictions,
            LeagueService leagues,
            LeagueParticipantService participants,
            RaceService races,
            RaceJpaRepository raceJpaRepository,
            JdbcClient jdbc,
            PlatformTransactionManager transactionManager
    ) {
        this.results = results;
        this.points = points;
        this.predictions = predictions;
        this.leagues = leagues;
        this.participants = participants;
        this.races = races;
        this.raceJpaRepository = raceJpaRepository;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        raceJpaRepository.deleteAll();
    }

    @Test
    void storesOrderedDriverStatusesAndKeepsDraftChangesSeparateFromPublishedResultAndPoints() {
        var race = createRace(2027, 1);
        var prediction = createPrediction(race, identity());
        var draft = new RaceResult(race.id(), RULES_2027, null, values("1.234"));
        transactions.executeWithoutResult(status -> results.create(draft));

        transactions.executeWithoutResult(status -> {
            assertThat(results.findByRaceId(race.id())).contains(draft);
            assertThat(points.findByPredictionId(prediction.id())).isEmpty();
            assertThat(races.getById(race.id()).status()).isEqualTo(RaceStatus.SCHEDULED);
        });

        var published = new RaceResult(race.id(), RULES_2027, draft.draft(), null);
        var originalPoints = new PredictionPoints(prediction.id(), RULES_2027, 42, 25, 4, 5, 1, 7, null);
        transactions.executeWithoutResult(status -> {
            results.update(published);
            points.create(originalPoints);
        });
        transactions.executeWithoutResult(status -> assertThat(results.findByRaceId(race.id())).contains(published));
        assertThat(jdbc.sql("SELECT draft IS NULL FROM race_results WHERE race_id = :id")
                .param("id", race.id().value()).query(Boolean.class).single()).isTrue();

        var correction = new RaceResult(race.id(), RULES_2027, published.result(), values("999999.999"));
        transactions.executeWithoutResult(status -> results.update(correction));
        transactions.executeWithoutResult(status -> {
            assertThat(results.findByRaceId(race.id())).contains(correction);
            assertThat(points.findByPredictionId(prediction.id())).contains(originalPoints);
        });
        assertThat(jdbc.sql("SELECT version FROM race_results WHERE race_id = :id")
                .param("id", race.id().value()).query(Long.class).single()).isEqualTo(2L);
        assertThat(jdbc.sql("SELECT version FROM prediction_points WHERE prediction_id = :id")
                .param("id", prediction.id().value()).query(Long.class).single()).isZero();
    }

    @Test
    void preservesAbsentValuesAndActualZerosAndCanClearBothSnapshots() {
        var race = createRace(2026, 1);
        var rules = new RulesVersion("2026");
        var absent = new RaceResultValues(null, null, null);
        var draft = new RaceResultValues(List.of(new RaceClassificationEntry(new Driver("Norris"), RaceDriverStatus.FINISHED)), null, null);
        var initial = new RaceResult(race.id(), rules, absent, draft);
        transactions.executeWithoutResult(status -> results.create(initial));
        transactions.executeWithoutResult(status -> assertThat(results.findByRaceId(race.id())).contains(initial));

        var zeros = new RaceResultValues(List.of(new RaceClassificationEntry(new Driver("Norris"), RaceDriverStatus.FINISHED)),
                0, BigDecimal.ZERO);
        var updated = new RaceResult(race.id(), rules, zeros, absent);
        transactions.executeWithoutResult(status -> results.update(updated));
        transactions.executeWithoutResult(status -> assertThat(results.findByRaceId(race.id())).contains(updated));

        var classificationOnly = new RaceResultValues(List.of(new RaceClassificationEntry(new Driver("Norris"), RaceDriverStatus.DNF)), null, null);
        var partialResult = new RaceResult(race.id(), rules, classificationOnly, new RaceResultValues(List.of(), null, null));
        transactions.executeWithoutResult(status -> results.update(partialResult));
        transactions.executeWithoutResult(status -> assertThat(results.findByRaceId(race.id())).contains(partialResult));

        var cleared = new RaceResult(race.id(), rules, null, null);
        transactions.executeWithoutResult(status -> results.update(cleared));
        transactions.executeWithoutResult(status -> assertThat(results.findByRaceId(race.id())).contains(cleared));
        assertThat(jdbc.sql("SELECT result IS NULL AND draft IS NULL FROM race_results WHERE race_id = :id")
                .param("id", race.id().value()).query(Boolean.class).single()).isTrue();
    }

    @Test
    void storesOneCurrentPointsSetPerPredictionAcrossLeaguesAndPreservesHistoricalTotalsAndRules() {
        var race = createRace(2027, 1);
        var historicalRace = createRace(2025, 1);
        var identity = identity();
        var first = createPrediction(race, identity);
        var second = createPrediction(race, identity);
        var historical = createPrediction(historicalRace, identity);
        var firstPoints = new PredictionPoints(first.id(), RULES_2027, 42, 25, 4, 5, 1, 7, null);
        var secondPoints = new PredictionPoints(second.id(), RULES_2027, 0, null, 0, 0, 0, null, null);
        var historicalPoints = new PredictionPoints(historical.id(), new RulesVersion("2025"), 6, 15, 0, 5, 0, null, 0);
        transactions.executeWithoutResult(status -> {
            points.create(firstPoints);
            points.create(secondPoints);
            points.create(historicalPoints);
        });

        transactions.executeWithoutResult(status -> {
            assertThat(points.findAllByRaceId(race.id())).containsExactlyInAnyOrder(firstPoints, secondPoints);
            assertThat(points.findAllByRaceId(historicalRace.id())).containsExactly(historicalPoints);
            assertThat(points.findByPredictionId(first.id())).contains(firstPoints);
        });

        var recalculated = new PredictionPoints(first.id(), RULES_2027, 35, 18, 4, 5, 1, 7, null);
        transactions.executeWithoutResult(status -> points.update(recalculated));
        transactions.executeWithoutResult(status ->
                assertThat(points.findAllByRaceId(race.id())).containsExactlyInAnyOrder(recalculated, secondPoints));
        assertThat(jdbc.sql("SELECT version FROM prediction_points WHERE prediction_id = :id")
                .param("id", first.id().value()).query(Long.class).single()).isEqualTo(1L);

        var absentPoints = new PredictionPoints(first.id(), RULES_2027, 0, null, null, null, null, null, null);
        transactions.executeWithoutResult(status -> points.update(absentPoints));
        transactions.executeWithoutResult(status -> {
            assertThat(points.findAllByRaceId(race.id())).containsExactlyInAnyOrder(absentPoints, secondPoints);
            assertThat(points.findByPredictionId(historical.id())).contains(historicalPoints);
        });
    }

    @Test
    void rejectsDuplicatesAndMissingParentsAndRollsBackResultAndPointsTogether() {
        var race = createRace(2027, 1);
        var first = createPrediction(race, identity());
        var second = createPrediction(race, identity());
        var result = new RaceResult(race.id(), RULES_2027, values("1.234"), null);
        var firstPoints = new PredictionPoints(first.id(), RULES_2027, 42, 25, 4, 5, 1, 7, null);
        transactions.executeWithoutResult(status -> {
            results.create(result);
            points.create(firstPoints);
        });

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> results.create(result)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("race_results_pkey");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> points.create(firstPoints)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("prediction_points_pkey");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> results.create(
                new RaceResult(new RaceId(UUID.randomUUID()), RULES_2027, null, values("2")))))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("race_results_race_id_fkey");

        var secondPoints = new PredictionPoints(second.id(), RULES_2027, 10, null, 10, null, null, null, null);
        var invalidPoints = new PredictionPoints(new PredictionId(UUID.randomUUID()), RULES_2027,
                0, null, null, null, null, null, null);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            results.update(new RaceResult(race.id(), RULES_2027, values("5"), values("7")));
            points.update(new PredictionPoints(first.id(), RULES_2027, 0, null, null, null, null, null, null));
            points.create(secondPoints);
            points.create(invalidPoints);
        })).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("prediction_points_prediction_id_fkey");

        transactions.executeWithoutResult(status -> {
            assertThat(results.findByRaceId(race.id())).contains(result);
            assertThat(points.findAllByRaceId(race.id())).containsExactly(firstPoints);
        });
    }

    private Race createRace(int season, int round) {
        var closeAt = Instant.parse(season + "-03-07T12:00:00Z").plusSeconds(round * 3600L);
        return races.create(season, round, RaceType.RACE, "Grand Prix", closeAt.minusSeconds(86400), closeAt);
    }

    private UserIdentityId identity() {
        return new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
    }

    private Prediction createPrediction(Race race, UserIdentityId identity) {
        var league = leagues.createManual("League " + UUID.randomUUID());
        var participant = participants.ensureParticipant(league.id(), identity, "Participant");
        var prediction = new Prediction(new PredictionId(UUID.randomUUID()), league.id(), participant.id(), race.id(),
                new PredictionValues(null, null, null, null, null), null);
        transactions.executeWithoutResult(status -> predictions.create(prediction));
        return prediction;
    }

    private RaceResultValues values(String lead) {
        return new RaceResultValues(List.of(
                new RaceClassificationEntry(new Driver("Norris"), RaceDriverStatus.FINISHED),
                new RaceClassificationEntry(new Driver("Stroll"), RaceDriverStatus.DNF),
                new RaceClassificationEntry(new Driver("Bottas"), RaceDriverStatus.DNS),
                new RaceClassificationEntry(new Driver("Hamilton"), RaceDriverStatus.DQ)
        ), 2, new BigDecimal(lead));
    }
}
