package com.xdd.f1.bootstrap.prediction;

import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueService;
import com.xdd.f1.application.prediction.PredictionConditionsRepository;
import com.xdd.f1.application.prediction.PredictionRepository;
import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.prediction.*;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.domain.race.lineup.Driver;
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

class PredictionRepositoryIntegrationTest extends AbstractIntegrationTest {
    private final PredictionRepository predictions;
    private final PredictionConditionsRepository conditions;
    private final LeagueService leagues;
    private final LeagueParticipantService participants;
    private final RaceService races;
    private final RaceJpaRepository raceJpaRepository;
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    @Autowired
    PredictionRepositoryIntegrationTest(
            PredictionRepository predictions,
            PredictionConditionsRepository conditions,
            LeagueService leagues,
            LeagueParticipantService participants,
            RaceService races,
            RaceJpaRepository raceJpaRepository,
            JdbcClient jdbc,
            PlatformTransactionManager transactionManager
    ) {
        this.predictions = predictions;
        this.conditions = conditions;
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
    void storesIndependentPredictionsWithExactDecimalsAndLegacyValuesAndDoesNotOverwriteConditions() {
        var race = createRace();
        var identity = new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
        var firstParticipant = createParticipant(identity);
        var secondParticipant = createParticipant(identity);
        var thirdParticipant = createParticipant(identity);
        var first = prediction(race, firstParticipant, List.of("Historical Driver B", "Historical Driver A"));
        var second = prediction(race, secondParticipant, null);
        var third = prediction(race, thirdParticipant, List.of());
        var originalConditions = new PredictionConditions(race.id(), new Driver("Lando Norris"));

        transactions.executeWithoutResult(status -> {
            assertThat(conditions.createIfAbsent(originalConditions)).isTrue();
            predictions.create(first);
            predictions.create(second);
            predictions.create(third);
        });

        transactions.executeWithoutResult(status -> {
            assertThat(predictions.findByLeagueAndRaceAndParticipant(first.leagueId(), race.id(), first.participantId()))
                    .contains(first);
            assertThat(predictions.findByLeagueAndRaceAndParticipant(second.leagueId(), race.id(), second.participantId()))
                    .contains(second);
            assertThat(predictions.findByLeagueAndRaceAndParticipant(third.leagueId(), race.id(), third.participantId()))
                    .contains(third);
            assertThat(predictions.findByLeagueAndRaceAndParticipant(second.leagueId(), race.id(), first.participantId()))
                    .isEmpty();
            assertThat(conditions.createIfAbsent(new PredictionConditions(race.id(), new Driver("Oscar Piastri"))))
                    .isFalse();
            assertThat(conditions.findByRaceId(race.id())).contains(originalConditions);
        });

        var updated = new Prediction(first.id(), first.leagueId(), first.participantId(), race.id(), new PredictionValues(
                new Driver("Oscar Piastri"),
                new DnfPrediction(List.of(
                        new Driver("Driver E"), new Driver("Driver A"), new Driver("Driver C"),
                        new Driver("Driver B"), new Driver("Driver D")
                )),
                SafetyCarsCount.THREE_PLUS,
                22,
                new BigDecimal("999999.999")
        ), first.teamsPickedDrivers());
        var updatedConditions = new PredictionConditions(race.id(), new Driver("Oscar Piastri"));
        transactions.executeWithoutResult(status -> {
            predictions.update(updated);
            conditions.update(updatedConditions);
        });

        transactions.executeWithoutResult(status -> {
            assertThat(predictions.findAllByRaceId(race.id())).containsExactlyInAnyOrder(updated, second, third);
            assertThat(conditions.findByRaceId(race.id())).contains(updatedConditions);
        });
        assertThat(jdbc.sql("SELECT version FROM predictions WHERE id = :id")
                .param("id", first.id().value()).query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("SELECT version FROM race_prediction_conditions WHERE race_id = :raceId")
                .param("raceId", race.id().value()).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void storesAbsentCategoriesAndConditionsAndDistinguishesThemFromZeroValues() {
        var historicalRace = races.create(2023, 1, RaceType.RACE, "Historical Grand Prix",
                Instant.parse("2023-03-04T12:00:00Z"), Instant.parse("2023-03-05T12:00:00Z"));
        var race = createRace();
        var identity = new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
        var firstParticipant = createParticipant(identity);
        var secondParticipant = createParticipant(identity);
        var historical = new Prediction(
                new PredictionId(UUID.randomUUID()), firstParticipant.leagueId(), firstParticipant.id(), historicalRace.id(),
                new PredictionValues(new Driver("Historical Driver"),
                        new DnfPrediction(List.of(new Driver("First DNF Driver"))), null, null, null),
                null
        );
        var absentValues = new PredictionValues(null, null, null, null, null);
        var absent = new Prediction(new PredictionId(UUID.randomUUID()), firstParticipant.leagueId(), firstParticipant.id(),
                race.id(), absentValues, null);
        var zeroValues = new PredictionValues(null, new DnfPrediction(List.of()), SafetyCarsCount.ZERO, null, BigDecimal.ZERO);
        var zero = new Prediction(new PredictionId(UUID.randomUUID()), secondParticipant.leagueId(), secondParticipant.id(),
                race.id(), zeroValues, null);
        var absentConditions = new PredictionConditions(race.id(), null);

        transactions.executeWithoutResult(status -> {
            predictions.create(historical);
            predictions.create(absent);
            predictions.create(zero);
            assertThat(conditions.createIfAbsent(absentConditions)).isTrue();
        });

        transactions.executeWithoutResult(status -> {
            assertThat(predictions.findAllByRaceId(historicalRace.id())).containsExactly(historical);
            assertThat(conditions.findByRaceId(historicalRace.id())).isEmpty();
            assertThat(predictions.findAllByRaceId(race.id())).containsExactlyInAnyOrder(absent, zero);
            assertThat(conditions.findByRaceId(race.id())).contains(absentConditions);
            assertThat(conditions.createIfAbsent(new PredictionConditions(race.id(), new Driver("New Driver"))))
                    .isFalse();
            assertThat(conditions.findByRaceId(race.id())).contains(absentConditions);
        });

        var enabled = new Prediction(absent.id(), absent.leagueId(), absent.participantId(), race.id(),
                prediction(race, firstParticipant, null).values(), List.of("Historical Team Driver"));
        transactions.executeWithoutResult(status -> {
            predictions.update(enabled);
            conditions.update(new PredictionConditions(race.id(), new Driver("New Driver")));
        });
        transactions.executeWithoutResult(status -> {
            assertThat(predictions.findAllByRaceId(race.id())).containsExactlyInAnyOrder(enabled, zero);
            assertThat(conditions.findByRaceId(race.id())).contains(new PredictionConditions(race.id(), new Driver("New Driver")));
            predictions.update(absent);
            conditions.update(absentConditions);
        });
        transactions.executeWithoutResult(status -> {
            assertThat(predictions.findAllByRaceId(race.id())).containsExactlyInAnyOrder(absent, zero);
            assertThat(conditions.findByRaceId(race.id())).contains(absentConditions);
        });
    }

    @Test
    void rejectsDuplicatePredictionsAndParticipantsFromAnotherLeagueWithoutPartialWrites() {
        var race = createRace();
        var identity = new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
        var first = prediction(race, createParticipant(identity), null);
        var second = prediction(race, createParticipant(identity), null);
        var thirdParticipant = createParticipant(identity);
        transactions.executeWithoutResult(status -> predictions.create(first));

        var duplicate = new Prediction(new PredictionId(UUID.randomUUID()), first.leagueId(), first.participantId(),
                race.id(), first.values(), null);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> predictions.create(duplicate)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_predictions_race_participant");

        var mismatched = new Prediction(new PredictionId(UUID.randomUUID()), first.leagueId(), thirdParticipant.id(),
                race.id(), second.values(), null);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            predictions.create(second);
            predictions.create(mismatched);
        })).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_predictions_league_participant");

        transactions.executeWithoutResult(status ->
                assertThat(predictions.findAllByRaceId(race.id())).containsExactly(first));
    }

    private Race createRace() {
        return races.create(2027, 1, RaceType.RACE, "Grand Prix",
                Instant.parse("2027-03-06T12:00:00Z"), Instant.parse("2027-03-07T12:00:00Z"));
    }

    private LeagueParticipant createParticipant(UserIdentityId identity) {
        var league = leagues.createManual("League " + UUID.randomUUID());
        return participants.ensureParticipant(league.id(), identity, "Participant");
    }

    private Prediction prediction(Race race, LeagueParticipant participant, List<String> teamsPickedDrivers) {
        return new Prediction(
                new PredictionId(UUID.randomUUID()), participant.leagueId(), participant.id(), race.id(),
                new PredictionValues(new Driver("Lando Norris"), new DnfPrediction(List.of()),
                        SafetyCarsCount.ZERO, 10, new BigDecimal("1.5")),
                teamsPickedDrivers
        );
    }
}
