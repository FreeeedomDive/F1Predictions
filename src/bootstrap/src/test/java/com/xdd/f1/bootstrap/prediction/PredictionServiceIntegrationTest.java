package com.xdd.f1.bootstrap.prediction;

import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.prediction.*;
import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.application.race.lineup.RaceLineupService;
import com.xdd.f1.application.race.lineup.RaceLineupRepository;
import com.xdd.f1.application.race.result.RaceResultService;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.contract.race.RaceStartingGridUpdatedEvent;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.*;
import com.xdd.f1.domain.prediction.*;
import com.xdd.f1.domain.race.*;
import com.xdd.f1.domain.race.lineup.*;
import com.xdd.f1.domain.user.*;
import com.xdd.f1.infrastructure.persistence.race.RaceJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@RecordApplicationEvents
class PredictionServiceIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2027-03-01T12:00:00Z");

    private final PredictionService predictions;
    private final PredictionConditionsRepository conditions;
    private final RaceService races;
    private final RaceLineupService lineups;
    private final RaceLineupRepository lineupRepository;
    private final RaceResultService results;
    private final LeagueRepository leagues;
    private final LeagueParticipantService participants;
    private final RaceJpaRepository raceJpaRepository;
    private final TransactionTemplate transactions;

    @MockitoBean
    private Clock clock;

    private UserIdentityId identity;
    private League league;
    private LeagueParticipant participant;

    @Autowired
    PredictionServiceIntegrationTest(PredictionService predictions, PredictionConditionsRepository conditions,
                                     RaceService races, RaceLineupService lineups, RaceLineupRepository lineupRepository, LeagueRepository leagues,
                                     LeagueParticipantService participants, RaceJpaRepository raceJpaRepository,
                                     PlatformTransactionManager transactionManager, RaceResultService results) {
        this.predictions = predictions;
        this.conditions = conditions;
        this.races = races;
        this.lineups = lineups;
        this.lineupRepository = lineupRepository;
        this.results = results;
        this.leagues = leagues;
        this.participants = participants;
        this.raceJpaRepository = raceJpaRepository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        identity = new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
        league = createLeague(false, false);
        participant = participants.ensureParticipant(league.id(), identity, "Alice");
    }

    @AfterEach
    void cleanUp() {
        raceJpaRepository.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void readsRaceAndSavesOwnPredictionAfterParticipationLoadedTheLeagueProxy(boolean discord) {
        var selectedLeague = createLeague(discord, false);
        var member = participants.ensureParticipant(selectedLeague.id(), identity, "League-specific name");
        var race = createRace(1, RaceType.RACE, 3600);
        races.openRacePredictions(race.id());

        var details = predictions.getDetails(member.userId(), selectedLeague.id(), race.id());
        assertThat(details.predictionsOpen()).isTrue();
        assertThat(details.teams()).hasSize(3);
        assertThat(predictions.findOwn(member.userId(), selectedLeague.id(), race.id())).isEmpty();
        var saved = predictions.save(member.userId(), selectedLeague.id(), race.id(), values("Lando Norris"));
        var unchanged = predictions.save(member.userId(), selectedLeague.id(), race.id(), values("Lando Norris"));
        var updated = predictions.save(member.userId(), selectedLeague.id(), race.id(), values("Oscar Piastri"));

        assertThat(unchanged).isEqualTo(saved);
        assertThat(updated.id()).isEqualTo(saved.id());
        assertThat(updated.participantId()).isEqualTo(member.id());
        assertThat(predictions.findOwn(member.userId(), selectedLeague.id(), race.id())).contains(updated);
    }

    @Test
    void keepsOtherParticipantsValuesPrivateAndRejectsForeignLeagues() {
        var race = createRace(1, RaceType.RACE, 3600);
        races.openRacePredictions(race.id());
        var bobIdentity = new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
        var bob = participants.ensureParticipant(league.id(), bobIdentity, "Bob");
        var bobPrediction = predictions.save(bob.userId(), league.id(), race.id(), values("Oscar Piastri"));
        assertThat(predictions.findOwn(participant.userId(), league.id(), race.id())).isEmpty();
        predictions.save(participant.userId(), league.id(), race.id(), values("Lando Norris"));
        assertThat(predictions.findOwn(bob.userId(), league.id(), race.id())).contains(bobPrediction);

        var foreignLeague = createLeague(false, false);
        participants.ensureParticipant(foreignLeague.id(), bobIdentity, "Bob elsewhere");
        assertThatThrownBy(() -> predictions.getDetails(participant.userId(), foreignLeague.id(), race.id()))
                .isInstanceOf(LeagueParticipantNotFoundException.class);
        assertThatThrownBy(() -> predictions.save(participant.userId(), foreignLeague.id(), race.id(), values("Lando Norris")))
                .isInstanceOf(LeagueParticipantNotFoundException.class);

        races.closeRacePredictions(race.id());
        assertThat(predictions.findOwn(participant.userId(), league.id(), race.id()).orElseThrow().values().tenthPlacePickedDriver())
                .isEqualTo(new Driver("Lando Norris"));
    }

    @Test
    void enforcesTheServerDeadlineAndManualClosureWhileKeepingOwnPredictionReadable() {
        var deadlineRace = createRace(1, RaceType.RACE, 3600);
        var manualRace = createRace(2, RaceType.RACE, 7200);
        races.openRacePredictions(deadlineRace.id());
        races.openRacePredictions(manualRace.id());
        var saved = predictions.save(participant.userId(), league.id(), deadlineRace.id(), values("Lando Norris"));

        when(clock.instant()).thenReturn(deadlineRace.closeAt());
        assertThatThrownBy(() -> predictions.save(participant.userId(), league.id(), deadlineRace.id(), values("Oscar Piastri")))
                .isInstanceOf(PredictionsClosedException.class);
        assertThat(predictions.getDetails(participant.userId(), league.id(), deadlineRace.id()).predictionsOpen()).isFalse();
        assertThat(predictions.findOwn(participant.userId(), league.id(), deadlineRace.id())).contains(saved);

        when(clock.instant()).thenReturn(NOW);
        races.closeRacePredictions(manualRace.id());
        assertThatThrownBy(() -> predictions.save(participant.userId(), league.id(), manualRace.id(), values("Lando Norris")))
                .isInstanceOf(PredictionsClosedException.class);
    }

    @Test
    void selectsTheCurrentEligibleRaceOtherwiseUpcomingRaceAndSkipsExpiredOpenRaces() {
        var sprint = createRace(1, RaceType.SPRINT, 1800);
        var main = createRace(1, RaceType.RACE, 3600);
        var upcoming = createRace(2, RaceType.RACE, 7200);
        races.openRacePredictions(sprint.id());
        races.openRacePredictions(main.id());
        var sprintLeague = createLeague(false, true);
        participants.ensureParticipant(sprintLeague.id(), identity, "Alice in sprint league");

        assertThat(predictions.findCurrentDetails(participant.userId(), league.id(), Optional.of(2027)).orElseThrow().race().id())
                .isEqualTo(main.id());
        assertThat(predictions.findCurrentDetails(participant.userId(), sprintLeague.id(), Optional.of(2027)).orElseThrow().race().id())
                .isEqualTo(sprint.id());
        assertThat(predictions.findCurrentDetails(participant.userId(), league.id(), Optional.of(2022))).isEmpty();

        when(clock.instant()).thenReturn(main.closeAt());
        assertThat(predictions.findCurrentDetails(participant.userId(), league.id(), Optional.of(2027)).orElseThrow().race().id())
                .isEqualTo(upcoming.id());
        when(clock.instant()).thenReturn(upcoming.closeAt());
        assertThat(predictions.findCurrentDetails(participant.userId(), league.id(), Optional.of(2027))).isEmpty();
    }

    @Test
    void createsConditionsAtFirstOpeningAndPreservesThemOnRepeatedOpening() {
        var preparedRace = createRace(1, RaceType.RACE, 3600);
        assertThat(predictions.getDetails(participant.userId(), league.id(), preparedRace.id()).conditions()).isNull();

        races.openRacePredictions(preparedRace.id());
        var selected = predictions.getDetails(participant.userId(), league.id(), preparedRace.id()).conditions();
        assertThat(selected.positionPredictionDriver()).isNotNull();
        races.openRacePredictions(preparedRace.id());
        assertThat(predictions.getDetails(participant.userId(), league.id(), preparedRace.id()).conditions()).isEqualTo(selected);

        transactions.executeWithoutResult(status -> conditions.update(new PredictionConditions(preparedRace.id(), null)));
        races.openRacePredictions(preparedRace.id());
        assertThat(predictions.getDetails(participant.userId(), league.id(), preparedRace.id()).conditions())
                .isEqualTo(new PredictionConditions(preparedRace.id(), null));
        assertThat(races.getById(preparedRace.id()).status()).isEqualTo(RaceStatus.OPEN);
    }

    @Test
    void preservesConditionsAndMovesSelectionsForReplacementButNotSwapOrAnotherRace(ApplicationEvents events) {
        var race = createRace(1, RaceType.RACE, 3600);
        var otherRace = createRace(2, RaceType.RACE, 7200);
        races.openRacePredictions(race.id());
        races.openRacePredictions(otherRace.id());
        var chosen = transactions.execute(status -> conditions.findByRaceId(race.id()).orElseThrow());
        races.openRacePredictions(race.id());
        assertThat(predictions.getDetails(participant.userId(), league.id(), race.id()).conditions()).isEqualTo(chosen);
        transactions.executeWithoutResult(status -> conditions.update(new PredictionConditions(race.id(), new Driver("Lando Norris"))));
        var secondLeague = createLeague(false, false);
        participants.ensureParticipant(secondLeague.id(), identity, "Alice elsewhere");
        var otherPrediction = predictions.save(participant.userId(), league.id(), otherRace.id(), values("Lando Norris"));
        predictions.save(participant.userId(), league.id(), race.id(), values("Lando Norris"));
        predictions.save(participant.userId(), secondLeague.id(), race.id(), values("Lando Norris"));
        var grid = List.of(new Driver("Oscar Piastri"), new Driver("Lando Norris"), new Driver("Charles Leclerc"));
        assertThat(results.applyStartingGrid(race, grid)).isTrue();
        assertThat(results.applyStartingGrid(otherRace, grid)).isTrue();

        lineups.updateTeam(race.id(), "McLaren", team("McLaren", "Replacement Driver", "Oscar Piastri"));
        var moved = predictions.findOwn(participant.userId(), league.id(), race.id()).orElseThrow();
        assertThat(moved.values().tenthPlacePickedDriver()).isEqualTo(new Driver("Replacement Driver"));
        assertThat(moved.values().dnfPrediction().dnfPickedDrivers()).contains(new Driver("Replacement Driver"))
                .doesNotContain(new Driver("Lando Norris"));
        assertThat(moved.values().driverPositionPrediction()).isEqualTo(4);
        assertThat(moved.values().firstPlaceLeadPrediction()).isEqualByComparingTo("1.25");
        assertThat(predictions.findOwn(participant.userId(), secondLeague.id(), race.id()).orElseThrow().values())
                .isEqualTo(moved.values());
        assertThat(predictions.getDetails(participant.userId(), league.id(), race.id()).conditions().positionPredictionDriver())
                .isEqualTo(new Driver("Replacement Driver"));
        assertThat(predictions.findOwn(participant.userId(), league.id(), otherRace.id())).contains(otherPrediction);
        var replacedGrid = List.of(new Driver("Oscar Piastri"), new Driver("Replacement Driver"), new Driver("Charles Leclerc"));
        assertThat(results.getForEditing(race.id()).result().startingGrid()).containsExactlyElementsOf(replacedGrid);
        assertThat(results.getForEditing(otherRace.id()).result().startingGrid()).containsExactlyElementsOf(grid);
        assertThat(results.applyStartingGrid(race, grid)).isFalse();

        lineups.swapDrivers(race.id(), new Driver("Replacement Driver"), new Driver("Charles Leclerc"));
        assertThat(predictions.findOwn(participant.userId(), league.id(), race.id())).contains(moved);
        assertThat(predictions.getDetails(participant.userId(), league.id(), race.id()).conditions().positionPredictionDriver())
                .isEqualTo(new Driver("Replacement Driver"));
        assertThat(results.getForEditing(race.id()).result().startingGrid()).containsExactlyElementsOf(replacedGrid);
        assertThat(events.stream(RaceStartingGridUpdatedEvent.class).toList()).hasSize(3).last().isEqualTo(
                new RaceStartingGridUpdatedEvent(race.id().value(), race.name(), "RACE", 2027,
                        List.of("Oscar Piastri", "Replacement Driver", "Charles Leclerc"), false)
        );
    }

    @Test
    void rollsBackLineupConditionsAndPredictionsTogether() {
        var race = createRace(1, RaceType.RACE, 3600);
        races.openRacePredictions(race.id());
        transactions.executeWithoutResult(status -> conditions.update(new PredictionConditions(race.id(), new Driver("Lando Norris"))));
        var saved = predictions.save(participant.userId(), league.id(), race.id(), values("Lando Norris"));
        var grid = List.of(new Driver("Lando Norris"), new Driver("Oscar Piastri"));
        assertThat(results.applyStartingGrid(race, grid)).isTrue();
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            lineups.updateTeam(race.id(), "McLaren", team("McLaren", "Replacement Driver", "Oscar Piastri"));
            throw new IllegalStateException("Abort replacement");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(predictions.findOwn(participant.userId(), league.id(), race.id())).contains(saved);
        assertThat(predictions.getDetails(participant.userId(), league.id(), race.id()).conditions().positionPredictionDriver())
                .isEqualTo(new Driver("Lando Norris"));
        assertThat(lineups.getForEditing(race.id()).teams().getFirst().drivers()).contains(new Driver("Lando Norris"));
        assertThat(results.getForEditing(race.id()).result().startingGrid()).containsExactlyElementsOf(grid);
    }

    @Test
    void serializesConcurrentFirstSavesAndRejectsDriversOutsideTheLineup() throws Exception {
        var race = createRace(1, RaceType.RACE, 3600);
        races.openRacePredictions(race.id());
        assertThatThrownBy(() -> predictions.save(participant.userId(), league.id(), race.id(), values("Unknown Driver")))
                .isInstanceOf(DomainValidationException.class);
        assertThat(predictions.findOwn(participant.userId(), league.id(), race.id())).isEmpty();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                return predictions.save(participant.userId(), league.id(), race.id(), values("Lando Norris"));
            });
            var second = executor.submit(() -> {
                start.await();
                return predictions.save(participant.userId(), league.id(), race.id(), values("Oscar Piastri"));
            });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).id()).isEqualTo(second.get(10, TimeUnit.SECONDS).id());
        }
    }

    private League createLeague(boolean discord, boolean sprintsEnabled) {
        LeagueSource source = discord ? new LeagueSource.Discord(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE)
                : new LeagueSource.Manual();
        var created = new League(new LeagueId(UUID.randomUUID()), "Prediction league", source,
                new LeagueSettings(null, sprintsEnabled, 2023), NOW);
        transactions.executeWithoutResult(status -> leagues.create(created));
        return created;
    }

    private Race createRace(int round, RaceType type, long closesInSeconds) {
        var race = races.create(2027, round, type, "Grand Prix", NOW.plusSeconds(60), NOW.plusSeconds(closesInSeconds));
        transactions.executeWithoutResult(status -> lineupRepository.createIfAbsent(new RaceLineup(race.id(), List.of(
                team("McLaren", "Lando Norris", "Oscar Piastri"),
                team("Ferrari", "Charles Leclerc", "Lewis Hamilton"),
                team("Haas", "Esteban Ocon", "Oliver Bearman")
        ))));
        return race;
    }

    private Team team(String name, String first, String second) {
        var badge = name.substring(0, Math.min(3, name.length())).toUpperCase(Locale.ROOT);
        return new Team(new TeamBranding(name, badge, "#112233", "#FFFFFF"), List.of(new Driver(first), new Driver(second)));
    }

    private PredictionValues values(String tenthPlace) {
        return new PredictionValues(new Driver(tenthPlace), new DnfPrediction(List.of(
                new Driver("Lando Norris"), new Driver("Oscar Piastri"), new Driver("Charles Leclerc"),
                new Driver("Lewis Hamilton"), new Driver("Esteban Ocon"))), SafetyCarsCount.ONE, 4, new BigDecimal("1.25"));
    }
}
