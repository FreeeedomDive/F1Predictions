package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.prediction.*;
import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.application.race.RaceNotFoundException;
import com.xdd.f1.application.race.lineup.RaceLineupRepository;
import com.xdd.f1.application.race.result.*;
import com.xdd.f1.application.standings.SeasonPodiumEntry;
import com.xdd.f1.application.standings.SeasonPodiumRepository;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class RaceResultServiceIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2027-03-01T12:00:00Z");
    private static final RulesVersion RULES_2027 = new RulesVersion("2027-v1");

    private final RaceResultService service;
    private final RaceResultRepository results;
    private final RaceService races;
    private final PredictionService predictionService;
    private final PredictionRepository predictions;
    private final PredictionConditionsRepository conditions;
    private final RaceLineupRepository lineups;
    private final LeagueRepository leagues;
    private final LeagueParticipantService participants;
    private final SeasonPodiumRepository podiums;
    private final RaceJpaRepository raceJpaRepository;
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    @MockitoBean
    private Clock clock;

    @MockitoSpyBean
    private PredictionPointsRepository points;

    private League firstLeague;
    private League secondLeague;
    private LeagueParticipant firstParticipant;
    private LeagueParticipant secondParticipant;

    @Autowired
    RaceResultServiceIntegrationTest(RaceResultService service, RaceResultRepository results, RaceService races,
                                     PredictionService predictionService, PredictionRepository predictions,
                                     PredictionConditionsRepository conditions, RaceLineupRepository lineups,
                                     LeagueRepository leagues, LeagueParticipantService participants,
                                     SeasonPodiumRepository podiums, RaceJpaRepository raceJpaRepository, JdbcClient jdbc,
                                     PlatformTransactionManager transactionManager) {
        this.service = service;
        this.results = results;
        this.races = races;
        this.predictionService = predictionService;
        this.predictions = predictions;
        this.conditions = conditions;
        this.lineups = lineups;
        this.leagues = leagues;
        this.participants = participants;
        this.podiums = podiums;
        this.raceJpaRepository = raceJpaRepository;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        firstLeague = createLeague();
        secondLeague = createLeague();
        var identity = new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
        firstParticipant = participants.ensureParticipant(firstLeague.id(), identity, "First league name");
        secondParticipant = participants.ensureParticipant(secondLeague.id(), identity, "Second league name");
    }

    @AfterEach
    void cleanUp() {
        raceJpaRepository.deleteAll();
    }

    @Test
    void savesPartialDraftDuringOpenPredictionsWithoutPublishingOrCalculating() {
        var race = openRace(2027, 1, RaceType.RACE);
        var savedPredictions = savePredictions(race);
        var partial = new RaceResultValues(List.of(new RaceClassificationEntry(new Driver("Norris"), RaceDriverStatus.FINISHED)), null, null);
        var saved = service.saveDraft(race.id(), partial);
        assertThat(service.saveDraft(race.id(), partial)).isEqualTo(saved);

        transactions.executeWithoutResult(status -> {
            assertThat(results.findByRaceId(race.id())).contains(new RaceResult(race.id(), RULES_2027, null, partial));
            assertThat(points.findAllByRaceId(race.id())).isEmpty();
            assertThat(predictions.findAllByRaceId(race.id())).containsExactlyInAnyOrderElementsOf(savedPredictions);
            assertThat(races.getById(race.id()).status()).isEqualTo(RaceStatus.OPEN);
        });
        assertThat(jdbc.sql("SELECT version FROM race_results WHERE race_id = :id")
                .param("id", race.id().value()).query(Long.class).single()).isZero();
    }

    @Test
    void readsOnlyPublishedResultAndOwnPointsAcrossParticipantsAndLeaguesWhileTheEditorKeepsTheDraft() {
        var race = openRace(2027, 1, RaceType.RACE);
        var saved = savePredictions(race);
        var bob = participants.ensureParticipant(firstLeague.id(),
                new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString()), "Bob");
        var bobPrediction = predictionService.save(bob.userId(), firstLeague.id(), race.id(), predictionValues("Leclerc"));
        var editor = service.getForEditing(race.id());
        assertThat(editor.race().id()).isEqualTo(race.id());
        assertThat(editor.race().status()).isEqualTo(RaceStatus.OPEN);
        assertThat(editor.teams()).hasSize(3);
        assertThat(editor.lineupPrepared()).isTrue();
        assertThat(editor.conditions().positionPredictionDriver()).isEqualTo(new Driver("Norris"));
        assertThat(editor.result()).isNull();
        assertThat(service.findOwnResult(firstParticipant.userId(), firstLeague.id(), race.id())).isEmpty();
        service.saveDraft(race.id(), resultValues());
        assertThat(service.getForEditing(race.id()).result().draft()).isEqualTo(resultValues());
        assertThat(service.findOwnResult(firstParticipant.userId(), firstLeague.id(), race.id())).isEmpty();

        races.closeRacePredictions(race.id());
        service.finalizeResult(race.id());
        var own = service.findOwnResult(firstParticipant.userId(), firstLeague.id(), race.id()).orElseThrow();
        assertThat(own.race().status()).isEqualTo(RaceStatus.FINISHED);
        assertThat(own.rulesVersion()).isEqualTo(RULES_2027);
        assertThat(own.result()).isEqualTo(resultValues());
        assertThat(own.points()).isEqualTo(new PredictionPoints(saved.getFirst().id(), RULES_2027, 28, 6, 2, 5, 5, 10, null));
        assertThat(service.findOwnResult(secondParticipant.userId(), secondLeague.id(), race.id()).orElseThrow().points().predictionId())
                .isEqualTo(saved.getLast().id());
        assertThat(service.findOwnResult(bob.userId(), firstLeague.id(), race.id()).orElseThrow().points())
                .isEqualTo(new PredictionPoints(bobPrediction.id(), RULES_2027, 24, 2, 2, 5, 5, 10, null));
        var observer = participants.ensureParticipant(firstLeague.id(),
                new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString()), "Observer");
        var observed = service.findOwnResult(observer.userId(), firstLeague.id(), race.id()).orElseThrow();
        assertThat(observed.result()).isEqualTo(resultValues());
        assertThat(observed.points()).isNull();
        assertThatThrownBy(() -> service.findOwnResult(bob.userId(), secondLeague.id(), race.id()))
                .isInstanceOf(LeagueParticipantNotFoundException.class);

        service.saveDraft(race.id(), correctedValues());
        assertThat(service.getForEditing(race.id()).result().draft()).isEqualTo(correctedValues());
        assertThat(service.findOwnResult(firstParticipant.userId(), firstLeague.id(), race.id())).contains(own);
        service.finalizeResult(race.id());
        var updated = service.findOwnResult(firstParticipant.userId(), firstLeague.id(), race.id()).orElseThrow();
        assertThat(updated.result()).isEqualTo(correctedValues());
        assertThat(updated.points()).isEqualTo(new PredictionPoints(saved.getFirst().id(), RULES_2027, 19, 2, 2, 5, 5, 5, null));
    }

    @Test
    void readsAnUnpreparedRaceEditorWithoutCreatingResultLineupOrConditions() {
        var race = races.create(2027, 1, RaceType.RACE, "Scheduled Grand Prix", NOW.plusSeconds(60), NOW.plusSeconds(3600));
        var editor = service.getForEditing(race.id());
        assertThat(editor.race()).isEqualTo(race);
        assertThat(editor.teams()).isEmpty();
        assertThat(editor.lineupPrepared()).isFalse();
        assertThat(editor.conditions()).isNull();
        assertThat(editor.result()).isNull();
        transactions.executeWithoutResult(status -> {
            assertThat(results.findByRaceId(race.id())).isEmpty();
            assertThat(lineups.findByRaceId(race.id())).isEmpty();
            assertThat(conditions.findByRaceId(race.id())).isEmpty();
        });
    }

    @Test
    void keepsOwnSavedSprintResultReadableAfterDisablingSprintsAndRejectsIneligibleReads() {
        var race = openRace(2027, 1, RaceType.SPRINT);
        var saved = savePredictions(race);
        publish(race);
        jdbc.sql("UPDATE league_settings SET sprints_enabled = false WHERE league_id = :id")
                .param("id", firstLeague.id().value()).update();
        var own = service.findOwnResult(firstParticipant.userId(), firstLeague.id(), race.id()).orElseThrow();
        assertThat(own.points().predictionId()).isEqualTo(saved.getFirst().id());
        assertThat(own.points().totalPoints()).isEqualTo(8);
        var observer = participants.ensureParticipant(firstLeague.id(),
                new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString()), "Observer");
        assertThatThrownBy(() -> service.findOwnResult(observer.userId(), firstLeague.id(), race.id()))
                .isInstanceOf(RaceNotFoundException.class);
        var earlier = races.create(2022, 1, RaceType.RACE, "Earlier Grand Prix", NOW.plusSeconds(60), NOW.plusSeconds(3600));
        assertThatThrownBy(() -> service.findOwnResult(firstParticipant.userId(), firstLeague.id(), earlier.id()))
                .isInstanceOf(RaceNotFoundException.class);
        assertThatThrownBy(() -> service.findOwnResult(firstParticipant.userId(), firstLeague.id(), new RaceId(UUID.randomUUID())))
                .isInstanceOf(RaceNotFoundException.class);
    }

    @Test
    void finalizesAllLeaguesAndKeepsRepeatedFinalizationAndUnchangedAutosaveIdempotent() {
        var race = openRace(2027, 1, RaceType.RACE);
        var saved = savePredictions(race);
        service.saveDraft(race.id(), resultValues());
        races.closeRacePredictions(race.id());
        var published = service.finalizeResult(race.id());

        assertThat(service.finalizeResult(race.id())).isEqualTo(published);
        assertThat(service.saveDraft(race.id(), resultValues())).isEqualTo(published);
        assertThat(service.finalizeResult(race.id())).isEqualTo(published);
        transactions.executeWithoutResult(status -> {
            assertThat(results.findByRaceId(race.id())).contains(new RaceResult(race.id(), RULES_2027, resultValues(), null));
            assertThat(points.findAllByRaceId(race.id())).containsExactlyInAnyOrder(
                    new PredictionPoints(saved.getFirst().id(), RULES_2027, 28, 6, 2, 5, 5, 10, null),
                    new PredictionPoints(saved.getLast().id(), RULES_2027, 23, 1, 2, 5, 5, 10, null));
            assertThat(races.getById(race.id()).status()).isEqualTo(RaceStatus.FINISHED);
            assertThat(predictions.findAllByRaceId(race.id())).containsExactlyInAnyOrderElementsOf(saved);
        });
        assertThat(jdbc.sql("SELECT version FROM race_results WHERE race_id = :id")
                .param("id", race.id().value()).query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("SELECT version FROM prediction_points WHERE prediction_id = :id")
                .param("id", saved.getFirst().id().value()).query(Long.class).single()).isZero();
    }

    @Test
    void preservesStartingGridThroughDraftFinalizationAndCorrection() {
        var race = openRace(2027, 1, RaceType.RACE);
        savePredictions(race);
        var grid = List.of("Norris", "Leclerc", "Piastri", "Hamilton", "Bearman", "Ocon").stream()
                .map(Driver::new).toList();
        var gridOnly = new RaceResult(race.id(), RULES_2027, null, null, grid);
        transactions.executeWithoutResult(status -> results.create(gridOnly));
        assertThat(service.findOwnResult(firstParticipant.userId(), firstLeague.id(), race.id())).isEmpty();

        races.closeRacePredictions(race.id());
        assertThatThrownBy(() -> service.finalizeResult(race.id())).isInstanceOf(RaceResultIncompleteException.class);
        assertThat(service.saveDraft(race.id(), resultValues()).startingGrid()).containsExactlyElementsOf(grid);
        var published = service.finalizeResult(race.id());
        assertThat(published.startingGrid()).containsExactlyElementsOf(grid);
        assertThat(service.finalizeResult(race.id())).isEqualTo(published);

        assertThat(service.saveDraft(race.id(), correctedValues()).startingGrid()).containsExactlyElementsOf(grid);
        service.finalizeResult(race.id());
        transactions.executeWithoutResult(status ->
                assertThat(results.findByRaceId(race.id()))
                        .contains(new RaceResult(race.id(), RULES_2027, correctedValues(), null, grid)));
    }

    @Test
    void savesPodiumsForEveryLeagueWhenLastRaceIsFinalized() {
        var firstRace = openRace(2027, 1, RaceType.RACE);
        var lastRace = openRace(2027, 2, RaceType.RACE);
        var bob = participants.ensureParticipant(firstLeague.id(),
                new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString()), "Bob");
        savePredictions(firstRace);
        predictionService.save(bob.userId(), firstLeague.id(), firstRace.id(), predictionValues("Piastri"));
        savePredictions(lastRace);
        predictionService.save(bob.userId(), firstLeague.id(), lastRace.id(), predictionValues("Piastri"));

        publish(firstRace);

        transactions.executeWithoutResult(status -> {
            assertThat(podiums.findAllByLeagueBeforeSeason(firstLeague.id(), 2028)).isEmpty();
            assertThat(podiums.findAllByLeagueBeforeSeason(secondLeague.id(), 2028)).isEmpty();
        });
        publish(lastRace);

        var expectedFirst = new SeasonPodiumEntry(firstLeague.id(), 2027, firstParticipant.id(), bob.id(), null);
        var expectedSecond = new SeasonPodiumEntry(secondLeague.id(), 2027, secondParticipant.id(), null, null);
        transactions.executeWithoutResult(status -> {
            assertThat(podiums.findAllByLeagueBeforeSeason(firstLeague.id(), 2028)).containsExactly(expectedFirst);
            assertThat(podiums.findAllByLeagueBeforeSeason(secondLeague.id(), 2028)).containsExactly(expectedSecond);
        });
        service.finalizeResult(lastRace.id());
        transactions.executeWithoutResult(status -> {
            assertThat(podiums.findAllByLeagueBeforeSeason(firstLeague.id(), 2028)).containsExactly(expectedFirst);
            assertThat(podiums.findAllByLeagueBeforeSeason(secondLeague.id(), 2028)).containsExactly(expectedSecond);
        });
    }

    @Test
    void updatesPodiumsWhenAnEarlierFinishedRaceIsCorrected() {
        var seasonRaces = List.of(
                openRace(2027, 1, RaceType.RACE),
                openRace(2027, 2, RaceType.RACE),
                openRace(2027, 3, RaceType.RACE),
                openRace(2027, 4, RaceType.RACE)
        );
        var bob = participants.ensureParticipant(firstLeague.id(),
                new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString()), "Bob");
        seasonRaces.forEach(race -> {
            savePredictions(race);
            predictionService.save(bob.userId(), firstLeague.id(), race.id(), predictionValues("Piastri"));
            publish(race);
        });
        var initial = new SeasonPodiumEntry(firstLeague.id(), 2027, firstParticipant.id(), bob.id(), null);
        transactions.executeWithoutResult(status ->
                assertThat(podiums.findAllByLeagueBeforeSeason(firstLeague.id(), 2028)).containsExactly(initial));

        seasonRaces.subList(0, 3).forEach(race -> {
            service.saveDraft(race.id(), correctedValues());
            service.finalizeResult(race.id());
        });

        var corrected = new SeasonPodiumEntry(firstLeague.id(), 2027, bob.id(), firstParticipant.id(), null);
        transactions.executeWithoutResult(status -> {
            assertThat(podiums.findAllByLeagueBeforeSeason(firstLeague.id(), 2028)).containsExactly(corrected);
            assertThat(podiums.findAllByLeagueBeforeSeason(secondLeague.id(), 2028))
                    .containsExactly(new SeasonPodiumEntry(secondLeague.id(), 2027, secondParticipant.id(), null, null));
        });
    }

    @Test
    void correctsAFinishedRaceAfterPenaltyAndPreservesPredictionsAndAnotherRace() {
        var race = openRace(2027, 1, RaceType.RACE);
        var otherRace = openRace(2027, 2, RaceType.RACE);
        var saved = savePredictions(race);
        var otherPredictions = savePredictions(otherRace);
        publish(race);
        publish(otherRace);
        var oldPoints = transactions.execute(status -> points.findAllByRaceId(race.id()));
        var otherPoints = transactions.execute(status -> points.findAllByRaceId(otherRace.id()));

        service.saveDraft(race.id(), correctedValues());
        transactions.executeWithoutResult(status -> {
            assertThat(results.findByRaceId(race.id()).orElseThrow().result()).isEqualTo(resultValues());
            assertThat(points.findAllByRaceId(race.id())).containsExactlyInAnyOrderElementsOf(oldPoints);
            assertThat(races.getById(race.id()).status()).isEqualTo(RaceStatus.FINISHED);
        });
        service.finalizeResult(race.id());

        transactions.executeWithoutResult(status -> {
            assertThat(results.findByRaceId(race.id())).contains(new RaceResult(race.id(), RULES_2027, correctedValues(), null));
            assertThat(points.findAllByRaceId(race.id())).containsExactlyInAnyOrder(
                    new PredictionPoints(saved.getFirst().id(), RULES_2027, 19, 2, 2, 5, 5, 5, null),
                    new PredictionPoints(saved.getLast().id(), RULES_2027, 21, 4, 2, 5, 5, 5, null));
            assertThat(predictions.findAllByRaceId(race.id())).containsExactlyInAnyOrderElementsOf(saved);
            assertThat(races.getById(race.id()).status()).isEqualTo(RaceStatus.FINISHED);
            assertThat(results.findByRaceId(otherRace.id()).orElseThrow().result()).isEqualTo(resultValues());
            assertThat(points.findAllByRaceId(otherRace.id())).containsExactlyInAnyOrderElementsOf(otherPoints);
            assertThat(predictions.findAllByRaceId(otherRace.id())).containsExactlyInAnyOrderElementsOf(otherPredictions);
        });
        assertThatThrownBy(() -> predictionService.save(firstParticipant.userId(), firstLeague.id(), race.id(), predictionValues("Norris")))
                .isInstanceOf(PredictionsClosedException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rollsBackResultPointsAndStatusWhenWritingTheSecondLeagueFails(boolean correction) {
        var race = openRace(2027, 1, RaceType.RACE);
        savePredictions(race);
        races.closeRacePredictions(race.id());
        if (correction) {
            service.saveDraft(race.id(), resultValues());
            service.finalizeResult(race.id());
            service.saveDraft(race.id(), correctedValues());
        } else {
            service.saveDraft(race.id(), resultValues());
        }
        var previousResult = transactions.execute(status -> results.findByRaceId(race.id()).orElseThrow());
        var previousPoints = transactions.execute(status -> points.findAllByRaceId(race.id()));
        var previousStatus = races.getById(race.id()).status();
        var writes = new AtomicInteger();
        Answer<Void> failAfterSecondWrite = invocation -> {
            invocation.callRealMethod();
            if (writes.incrementAndGet() == 2) {
                throw new RuntimeException("Abort second league points write");
            }
            return null;
        };
        doAnswer(failAfterSecondWrite).when(points).create(any());
        doAnswer(failAfterSecondWrite).when(points).update(any());

        assertThatThrownBy(() -> service.finalizeResult(race.id())).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("second league");
        transactions.executeWithoutResult(status -> {
            assertThat(results.findByRaceId(race.id())).contains(previousResult);
            assertThat(points.findAllByRaceId(race.id())).containsExactlyInAnyOrderElementsOf(previousPoints);
            assertThat(races.getById(race.id()).status()).isEqualTo(previousStatus);
        });
    }

    @Test
    void calculatesExistingSprintPredictionsEvenAfterSprintsAreDisabled() {
        var race = openRace(2027, 1, RaceType.SPRINT);
        var saved = savePredictions(race);
        jdbc.sql("UPDATE league_settings SET sprints_enabled = false WHERE league_id IN (:first, :second)")
                .param("first", firstLeague.id().value()).param("second", secondLeague.id().value()).update();
        publish(race);

        transactions.executeWithoutResult(status -> {
            assertThat(points.findAllByRaceId(race.id())).extracting(PredictionPoints::totalPoints).containsExactlyInAnyOrder(8, 6);
            assertThat(predictions.findAllByRaceId(race.id())).containsExactlyInAnyOrderElementsOf(saved);
            assertThat(races.getById(race.id()).status()).isEqualTo(RaceStatus.FINISHED);
        });
    }

    @Test
    void validatesStatusesDriversAndCompletenessBeforeFinalization() {
        var scheduled = races.create(2027, 1, RaceType.RACE, "Scheduled Grand Prix", NOW.plusSeconds(60), NOW.plusSeconds(3600));
        assertThatThrownBy(() -> service.saveDraft(scheduled.id(), resultValues())).isInstanceOf(RaceResultOperationNotAllowedException.class);
        assertThatThrownBy(() -> service.finalizeResult(scheduled.id())).isInstanceOf(RaceResultOperationNotAllowedException.class);
        var race = openRace(2027, 2, RaceType.RACE);
        savePredictions(race);
        service.saveDraft(race.id(), resultValues());
        assertThatThrownBy(() -> service.finalizeResult(race.id())).isInstanceOf(RaceResultOperationNotAllowedException.class);
        var foreign = new RaceResultValues(List.of(new RaceClassificationEntry(new Driver("Unknown"), RaceDriverStatus.FINISHED)), 0, BigDecimal.ZERO);
        assertThatThrownBy(() -> service.saveDraft(race.id(), foreign)).isInstanceOf(DomainValidationException.class);
        races.closeRacePredictions(race.id());
        var partial = new RaceResultValues(resultValues().classification().subList(0, 2), 1, new BigDecimal("1.25"));
        service.saveDraft(race.id(), partial);
        assertThatThrownBy(() -> service.finalizeResult(race.id())).isInstanceOf(RaceResultIncompleteException.class)
                .hasMessageContaining("every driver");
        service.saveDraft(race.id(), new RaceResultValues(resultValues().classification(), null, new BigDecimal("1.25")));
        assertThatThrownBy(() -> service.finalizeResult(race.id())).isInstanceOf(RaceResultIncompleteException.class)
                .hasMessageContaining("incident count");
        service.saveDraft(race.id(), new RaceResultValues(resultValues().classification(), 1, null));
        assertThatThrownBy(() -> service.finalizeResult(race.id())).isInstanceOf(RaceResultIncompleteException.class)
                .hasMessageContaining("winner gap");
        transactions.executeWithoutResult(status -> {
            assertThat(results.findByRaceId(race.id()).orElseThrow().result()).isNull();
            assertThat(points.findAllByRaceId(race.id())).isEmpty();
            assertThat(races.getById(race.id()).status()).isEqualTo(RaceStatus.CLOSED);
        });
    }

    @Test
    void preservesAbsentCategoriesAndDistinguishesThemFromActualZerosAndNobody() {
        var race = openRace(2027, 1, RaceType.RACE);
        var empty = predictionService.save(firstParticipant.userId(), firstLeague.id(), race.id(),
                new PredictionValues(null, null, null, null, null));
        var zero = predictionService.save(secondParticipant.userId(), secondLeague.id(), race.id(),
                new PredictionValues(null, new DnfPrediction(List.of()), SafetyCarsCount.ZERO, null, BigDecimal.ZERO));
        var classification = resultValues().classification().stream()
                .map(entry -> new RaceClassificationEntry(entry.driver(), RaceDriverStatus.FINISHED)).toList();
        races.closeRacePredictions(race.id());
        service.saveDraft(race.id(), new RaceResultValues(classification, 0, BigDecimal.ZERO));
        service.finalizeResult(race.id());
        transactions.executeWithoutResult(status -> assertThat(points.findAllByRaceId(race.id())).containsExactlyInAnyOrder(
                new PredictionPoints(empty.id(), RULES_2027, 0, null, null, null, null, null, null),
                new PredictionPoints(zero.id(), RULES_2027, 20, null, 10, 5, 5, null, null)));

        var other = openRace(2027, 2, RaceType.RACE);
        var absent = predictionService.save(firstParticipant.userId(), firstLeague.id(), other.id(), new PredictionValues(null, null, null, null, null));
        races.closeRacePredictions(other.id());
        service.saveDraft(other.id(), new RaceResultValues(classification, null, null));
        service.finalizeResult(other.id());
        transactions.executeWithoutResult(status -> {
            assertThat(results.findByRaceId(other.id()).orElseThrow().result().firstPlaceLead()).isNull();
            assertThat(results.findByRaceId(other.id()).orElseThrow().result().safetyCars()).isNull();
            assertThat(points.findByPredictionId(absent.id())).contains(new PredictionPoints(absent.id(), RULES_2027,
                    0, null, null, null, null, null, null));
        });
    }

    @ParameterizedTest
    @CsvSource({"2026,2026,28", "2027,2027-v1,23"})
    void usesThePinnedSeasonRulesInsteadOfApplyingTheNewScaleToHistory(int season, String version, int expectedTotal) {
        var race = openRace(season, 1, RaceType.RACE);
        var saved = predictionService.save(firstParticipant.userId(), firstLeague.id(), race.id(), predictionValues("Norris"));
        races.closeRacePredictions(race.id());
        service.saveDraft(race.id(), new RaceResultValues(resultValues().classification(), 1, new BigDecimal("100")));
        service.finalizeResult(race.id());
        transactions.executeWithoutResult(status -> {
            assertThat(results.findByRaceId(race.id()).orElseThrow().rulesVersion()).isEqualTo(new RulesVersion(version));
            assertThat(points.findByPredictionId(saved.id()).orElseThrow().rulesVersion()).isEqualTo(new RulesVersion(version));
            assertThat(points.findByPredictionId(saved.id()).orElseThrow().totalPoints()).isEqualTo(expectedTotal);
        });
    }

    @Test
    void rejectsUnsupportedAndMixedRulesWithoutChangingSavedHistory() {
        var race = openRace(2026, 1, RaceType.RACE);
        var saved = predictionService.save(firstParticipant.userId(), firstLeague.id(), race.id(), predictionValues("Norris"));
        races.closeRacePredictions(race.id());
        races.finishRace(race.id());
        var legacyRules = new RulesVersion("legacy-2026");
        var historical = new RaceResult(race.id(), legacyRules, resultValues(), null);
        var historicalPoints = new PredictionPoints(saved.id(), legacyRules, 17, 6, 2, 5, 0, 4, null);
        transactions.executeWithoutResult(status -> {
            results.create(historical);
            points.create(historicalPoints);
        });
        assertThat(service.getForEditing(race.id()).result()).isEqualTo(historical);
        assertThatThrownBy(() -> service.saveDraft(race.id(), correctedValues())).isInstanceOf(RaceResultRulesException.class);
        assertThatThrownBy(() -> service.finalizeResult(race.id())).isInstanceOf(RaceResultRulesException.class);
        var supported = new RaceResult(race.id(), new RulesVersion("2026"), resultValues(), null);
        transactions.executeWithoutResult(status -> results.update(supported));
        assertThatThrownBy(() -> service.finalizeResult(race.id())).isInstanceOf(RaceResultRulesException.class);
        transactions.executeWithoutResult(status -> {
            assertThat(results.findByRaceId(race.id())).contains(supported);
            assertThat(points.findByPredictionId(saved.id())).contains(historicalPoints);
            assertThat(races.getById(race.id()).status()).isEqualTo(RaceStatus.FINISHED);
        });
    }

    @Test
    void serializesConcurrentFinalizationWithoutDuplicatePoints() throws Exception {
        var race = openRace(2027, 1, RaceType.RACE);
        savePredictions(race);
        races.closeRacePredictions(race.id());
        service.saveDraft(race.id(), resultValues());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                return service.finalizeResult(race.id());
            });
            var second = executor.submit(() -> {
                start.await();
                return service.finalizeResult(race.id());
            });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
        }
        transactions.executeWithoutResult(status -> {
            assertThat(points.findAllByRaceId(race.id())).hasSize(2);
            assertThat(races.getById(race.id()).status()).isEqualTo(RaceStatus.FINISHED);
        });
    }

    private League createLeague() {
        var league = new League(new LeagueId(UUID.randomUUID()), "Result league", new LeagueSource.Manual(),
                new LeagueSettings(null, true, 2023), NOW);
        transactions.executeWithoutResult(status -> leagues.create(league));
        return league;
    }

    private Race openRace(int season, int round, RaceType type) {
        var race = races.create(season, round, type, "Grand Prix", NOW.plusSeconds(60), NOW.plusSeconds(3600L * round));
        transactions.executeWithoutResult(status -> lineups.createIfAbsent(new RaceLineup(race.id(), List.of(
                team("McLaren", "Norris", "Piastri"), team("Ferrari", "Leclerc", "Hamilton"), team("Haas", "Ocon", "Bearman")))));
        races.openRacePredictions(race.id());
        transactions.executeWithoutResult(status -> conditions.update(new PredictionConditions(race.id(), new Driver("Norris"))));
        return race;
    }

    private List<Prediction> savePredictions(Race race) {
        return List.of(predictionService.save(firstParticipant.userId(), firstLeague.id(), race.id(), predictionValues("Norris")),
                predictionService.save(secondParticipant.userId(), secondLeague.id(), race.id(), predictionValues("Piastri")));
    }

    private void publish(Race race) {
        races.closeRacePredictions(race.id());
        service.saveDraft(race.id(), resultValues());
        service.finalizeResult(race.id());
    }

    private Team team(String name, String first, String second) {
        return new Team(new TeamBranding(name, name.substring(0, 3), "#112233", "#FFFFFF"), List.of(new Driver(first), new Driver(second)));
    }

    private PredictionValues predictionValues(String tenth) {
        return new PredictionValues(new Driver(tenth), new DnfPrediction(List.of(new Driver("Norris"), new Driver("Piastri"),
                new Driver("Leclerc"), new Driver("Hamilton"), new Driver("Ocon"))), SafetyCarsCount.ONE, 4, new BigDecimal("1.25"));
    }

    private RaceResultValues resultValues() {
        return orderedResult(List.of("Piastri", "Leclerc", "Hamilton", "Norris", "Ocon", "Bearman"));
    }

    private RaceResultValues correctedValues() {
        return orderedResult(List.of("Leclerc", "Norris", "Piastri", "Hamilton", "Ocon", "Bearman"));
    }

    private RaceResultValues orderedResult(List<String> drivers) {
        return new RaceResultValues(drivers.stream().map(name -> new RaceClassificationEntry(new Driver(name), switch (name) {
            case "Ocon" -> RaceDriverStatus.DNF;
            case "Bearman" -> RaceDriverStatus.DNS;
            default -> RaceDriverStatus.FINISHED;
        })).toList(), 1, new BigDecimal("1.25"));
    }
}
