package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.application.race.lineup.RaceLineupNotEditableException;
import com.xdd.f1.application.race.lineup.RaceLineupNotPreparedException;
import com.xdd.f1.application.race.lineup.RaceLineupRepository;
import com.xdd.f1.application.race.lineup.RaceLineupService;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.RaceLineup;
import com.xdd.f1.domain.race.lineup.Team;
import com.xdd.f1.domain.race.lineup.TeamBranding;
import com.xdd.f1.infrastructure.persistence.race.RaceJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class RaceLineupServiceIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-02-03T04:05:06Z");

    private final RaceService service;
    private final RaceJpaRepository jpaRepository;
    private final RaceLineupService lineupService;
    private final TransactionTemplate transactions;

    @MockitoBean
    private Clock clock;

    @MockitoSpyBean
    private RaceLineupRepository lineups;

    @Autowired
    RaceLineupServiceIntegrationTest(
            RaceService service,
            RaceJpaRepository jpaRepository,
            RaceLineupService lineupService,
            PlatformTransactionManager transactionManager
    ) {
        this.service = service;
        this.jpaRepository = jpaRepository;
        this.lineupService = lineupService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @AfterEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    @Test
    void storesIndependentLineupSnapshotsAndDoesNotOverwriteAnExistingLineup() {
        var firstRace = createRace(2027, 1, "First Grand Prix", NOW, NOW.plusSeconds(3600));
        var nextRace = createRace(2027, 2, "Next Grand Prix", NOW.plusSeconds(7200), NOW.plusSeconds(10800));
        var original = new RaceLineup(firstRace.id(), List.of(
                new Team(
                        new TeamBranding("McLaren", "MCL", "#FF8000", "#000000"),
                        List.of(new Driver("Lando Norris"), new Driver("Oscar Piastri"))
                ),
                new Team(
                        new TeamBranding("Ferrari", "FER", "#E80020", "#FFFFFF"),
                        List.of(new Driver("Charles Leclerc"), new Driver("Lewis Hamilton"))
                )
        ));
        var copied = new RaceLineup(nextRace.id(), original.teams());
        transactions.executeWithoutResult(status -> {
            assertThat(lineups.createIfAbsent(original)).isTrue();
            assertThat(lineups.createIfAbsent(copied)).isTrue();
        });

        var loadedOriginal = transactions.execute(status -> lineups.findByRaceId(firstRace.id()));
        var loadedCopy = transactions.execute(status -> lineups.findByRaceId(nextRace.id()));
        assertThat(loadedOriginal).contains(original);
        assertThat(loadedCopy).contains(copied);

        var updated = new RaceLineup(nextRace.id(), List.of(
                original.teams().getLast(),
                new Team(
                        new TeamBranding("McLaren", "MCL", "#FFFFFF", "#FF8000"),
                        List.of(new Driver("Oscar Piastri"), new Driver("New Driver"))
                )
        ));
        transactions.executeWithoutResult(status -> lineups.update(updated));
        transactions.executeWithoutResult(status -> {
            assertThat(lineups.createIfAbsent(copied)).isFalse();
            assertThat(lineups.findByRaceId(nextRace.id())).contains(updated);
        });

        var originalAfterUpdate = transactions.execute(status -> lineups.findByRaceId(firstRace.id()));
        var updatedCopy = transactions.execute(status -> lineups.findByRaceId(nextRace.id()));
        assertThat(originalAfterUpdate).contains(original);
        assertThat(updatedCopy).contains(updated);
    }

    @Test
    void selectsEarliestOpenLineupTargetOtherwiseNextUpcomingRaceInRequestedSeason() {
        createRace(2027, 1, "Overdue scheduled race", NOW.minusSeconds(7200), NOW.minusSeconds(3600));
        var race = createRace(2027, 2, "Grand Prix", NOW.plusSeconds(60), NOW.plusSeconds(7200));
        var sprint = service.create(2027, 2, RaceType.SPRINT, "Grand Prix", NOW.plusSeconds(120), NOW.plusSeconds(3600));
        var otherSeason = createRace(2026, 1, "Other season", NOW, NOW.plusSeconds(1800));
        lineupService.save(lineup(otherSeason, "McLaren", "Lando Norris"));
        service.openRacePredictions(otherSeason.id());

        assertThat(lineupService.findTarget(2027).orElseThrow().race()).isEqualTo(sprint);
        assertThat(lineupService.findTarget(2028)).isEmpty();

        service.openRacePredictions(race.id());
        assertThat(lineupService.findTarget(2027).orElseThrow().race().id()).isEqualTo(race.id());

        service.openRacePredictions(sprint.id());
        assertThat(lineupService.findTarget(2027).orElseThrow().race().id()).isEqualTo(sprint.id());

        service.closeRacePredictions(sprint.id());
        assertThat(lineupService.findTarget(2027).orElseThrow().race().id()).isEqualTo(race.id());

        service.closeRacePredictions(race.id());
        assertThat(lineupService.findTarget(2027)).isEmpty();
    }

    @Test
    void createsFirstLineupAndEditsItBeforeAndDuringOpenPredictions() {
        var race = createRace(2027, 1, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var empty = lineupService.getForEditing(race.id());
        assertThat(empty.race()).isEqualTo(race);
        assertThat(empty.teams()).isEmpty();
        assertThat(empty.prepared()).isFalse();

        var initial = lineup(race, "McLaren", "Lando Norris");
        assertThat(lineupService.save(initial)).isEqualTo(initial);
        var prepared = lineupService.getForEditing(race.id());
        assertThat(prepared.prepared()).isTrue();
        assertThat(prepared.teams()).isEqualTo(initial.teams());

        var beforeOpening = new RaceLineup(race.id(), List.of(new Team(
                new TeamBranding("McLaren", "MCL", "#FF8000", "#000000"),
                List.of(new Driver("Lando Norris"), new Driver("Oscar Piastri"))
        )));
        lineupService.save(beforeOpening);
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(beforeOpening.teams());
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.SCHEDULED);

        service.openRacePredictions(race.id());
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(beforeOpening.teams());
        assertThat(lineupService.getForEditing(race.id()).prepared()).isTrue();
        var whileOpen = new RaceLineup(race.id(), List.of(new Team(
                new TeamBranding("McLaren", "MCL", "#FFFFFF", "#FF8000"),
                List.of(new Driver("Oscar Piastri"), new Driver("Lando Norris"))
        )));
        lineupService.save(whileOpen);

        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(whileOpen.teams());
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.OPEN);
    }

    @Test
    void previewsLatestEarlierLineupWithoutSavingAndKeepsPreparedAndHistoricalSnapshotsIndependent() {
        var older = createRace(2025, 1, "Older season", NOW.minusSeconds(14400), NOW.minusSeconds(10800));
        var previous = createRace(2026, 1, "Previous season", NOW.minusSeconds(7200), NOW.minusSeconds(3600));
        var sprint = service.create(2027, 1, RaceType.SPRINT, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var race = createRace(2027, 1, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var future = createRace(2027, 2, "Future Grand Prix", NOW.plusSeconds(10800), NOW.plusSeconds(14400));
        var historical = lineup(previous, "Mercedes", "Lewis Hamilton");
        var prepared = lineup(future, "McLaren", "Oscar Piastri");
        transactions.executeWithoutResult(status -> {
            lineups.createIfAbsent(lineup(older, "McLaren", "Lando Norris"));
            lineups.createIfAbsent(historical);
        });
        lineupService.save(prepared);

        var preview = lineupService.getForEditing(sprint.id());
        assertThat(preview.race()).isEqualTo(sprint);
        assertThat(preview.teams()).isEqualTo(historical.teams());
        assertThat(preview.prepared()).isFalse();
        var storedPreview = transactions.execute(status -> lineups.findByRaceId(sprint.id()));
        assertThat(storedPreview).isEmpty();

        lineupService.save(new RaceLineup(sprint.id(), preview.teams()));
        var moved = lineup(sprint, "Ferrari", "Lewis Hamilton");
        lineupService.save(moved);

        assertThat(lineupService.getForEditing(sprint.id()).teams()).isEqualTo(moved.teams());
        assertThat(lineupService.getForEditing(previous.id()).teams()).isEqualTo(historical.teams());
        var racePreview = lineupService.getForEditing(race.id());
        assertThat(racePreview.teams()).isEqualTo(moved.teams());
        assertThat(racePreview.prepared()).isFalse();
        assertThat(lineupService.getForEditing(future.id()).teams()).isEqualTo(prepared.teams());
        assertThat(lineupService.getForEditing(future.id()).prepared()).isTrue();
    }

    @ParameterizedTest(name = "finished={0}")
    @ValueSource(booleans = {false, true})
    void rejectsEditingClosedOrFinishedLineup(boolean finished) {
        var race = createRace(2027, 1, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var initial = lineup(race, "McLaren", "Lando Norris");
        lineupService.save(initial);
        service.openRacePredictions(race.id());
        service.closeRacePredictions(race.id());
        if (finished) {
            service.finishRace(race.id());
        }

        assertThatThrownBy(() -> lineupService.save(lineup(race, "Ferrari", "Lando Norris")))
                .isInstanceOf(RaceLineupNotEditableException.class);
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(initial.teams());
    }

    @Test
    void inheritsSprintLineupWhenOpeningRaceAndDoesNotRecopyOnRepeatedOpening() {
        var sprint = service.create(2027, 1, RaceType.SPRINT, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var race = createRace(2027, 1, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var original = lineup(sprint, "McLaren", "Lando Norris");
        lineupService.save(original);
        service.openRacePredictions(sprint.id());
        service.closeRacePredictions(sprint.id());

        service.openRacePredictions(race.id());

        var inherited = transactions.execute(status -> lineups.findByRaceId(race.id()));
        assertThat(inherited).contains(new RaceLineup(race.id(), original.teams()));
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.OPEN);

        var edited = lineup(race, "Ferrari", "Lando Norris");
        lineupService.save(edited);
        service.openRacePredictions(race.id());

        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(edited.teams());
        assertThat(lineupService.getForEditing(sprint.id()).teams()).isEqualTo(original.teams());
    }

    @Test
    void rejectsOpeningWithoutPreparedOrEarlierLineup() {
        var race = createRace(2027, 1, "Grand Prix", NOW, NOW.plusSeconds(3600));

        assertThatThrownBy(() -> service.openRacePredictions(race.id()))
                .isInstanceOf(RaceLineupNotPreparedException.class);

        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.SCHEDULED);
        assertThat(lineupService.getForEditing(race.id()).prepared()).isFalse();
    }

    @Test
    void rollsBackInheritedLineupTogetherWithOpening() {
        var previous = createRace(2027, 1, "Previous Grand Prix", NOW, NOW.plusSeconds(3600));
        var race = createRace(2027, 2, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        lineupService.save(lineup(previous, "McLaren", "Lando Norris"));

        transactions.executeWithoutResult(status -> {
            service.openRacePredictions(race.id());
            assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.OPEN);
            assertThat(lineupService.getForEditing(race.id()).prepared()).isTrue();
            status.setRollbackOnly();
        });

        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.SCHEDULED);
        assertThat(lineupService.getForEditing(race.id()).prepared()).isFalse();
    }

    @ParameterizedTest(name = "editorFirst={0}")
    @ValueSource(booleans = {false, true})
    void openingAndEditorCanConcurrentlyCreateTheSameLineup(boolean editorFirst) throws Exception {
        var previous = createRace(2027, 1, "Previous Grand Prix", NOW, NOW.plusSeconds(3600));
        var race = createRace(2027, 2, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var original = lineup(previous, "McLaren", "Lando Norris");
        var edited = lineup(race, "Ferrari", "Lewis Hamilton");
        lineupService.save(original);

        var firstInserted = new CountDownLatch(1);
        var competingInsert = new CountDownLatch(1);
        var attempts = new AtomicInteger();
        // Hold the first insert uncommitted until the other transaction also tries to create the lineup.
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                var created = invocation.callRealMethod();
                firstInserted.countDown();
                assertThat(competingInsert.await(10, TimeUnit.SECONDS)).isTrue();
                return created;
            }
            competingInsert.countDown();
            return invocation.callRealMethod();
        }).when(lineups).createIfAbsent(argThat(lineup -> lineup.raceId().equals(race.id())));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                if (editorFirst) {
                    lineupService.save(edited);
                } else {
                    service.openRacePredictions(race.id());
                }
            });
            assertThat(firstInserted.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                if (editorFirst) {
                    service.openRacePredictions(race.id());
                } else {
                    lineupService.save(edited);
                }
            });
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        var saved = transactions.execute(status -> lineups.findByRaceId(race.id()));
        assertThat(saved).contains(edited);
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.OPEN);
        assertThat(lineupService.getForEditing(previous.id()).teams()).isEqualTo(original.teams());
    }

    @ParameterizedTest(name = "predictionsOpen={0}")
    @ValueSource(booleans = {false, true})
    void replacesDriverOnlyInTargetRaceAndKeepsRepeatedReplacementIdempotent(boolean predictionsOpen) {
        var previous = createRace(2027, 1, "Previous Grand Prix", NOW, NOW.plusSeconds(3600));
        var race = service.create(2027, 2, RaceType.SPRINT, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var future = createRace(2027, 2, "Grand Prix", NOW.plusSeconds(7200), NOW.plusSeconds(10800));
        var previousDriver = new Driver("Lando Norris");
        var newDriver = new Driver("Max Verstappen");
        var original = new RaceLineup(previous.id(), List.of(
                new Team(
                        new TeamBranding("McLaren", "MCL", "#FF8000", "#000000"),
                        List.of(previousDriver, new Driver("Oscar Piastri"))
                ),
                new Team(
                        new TeamBranding("Ferrari", "FER", "#E80020", "#FFFFFF"),
                        List.of(new Driver("Charles Leclerc"), new Driver("Lewis Hamilton"))
                )
        ));
        lineupService.save(original);
        lineupService.save(new RaceLineup(future.id(), original.teams()));
        if (predictionsOpen) {
            service.openRacePredictions(race.id());
        }

        var replaced = lineupService.replaceDriver(race.id(), previousDriver, newDriver);

        var expected = new RaceLineup(race.id(), List.of(
                new Team(original.teams().getFirst().branding(), List.of(newDriver, new Driver("Oscar Piastri"))),
                original.teams().getLast()
        ));
        assertThat(replaced).isEqualTo(expected);
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(expected.teams());
        assertThat(lineupService.getForEditing(race.id()).prepared()).isTrue();

        assertThat(lineupService.replaceDriver(race.id(), previousDriver, newDriver)).isEqualTo(expected);
        assertThat(lineupService.replaceDriver(race.id(), newDriver, newDriver)).isEqualTo(expected);
        lineupService.save(expected);
        if (!predictionsOpen) {
            service.openRacePredictions(race.id());
        }

        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(expected.teams());
        assertThat(lineupService.getForEditing(previous.id()).teams()).isEqualTo(original.teams());
        assertThat(lineupService.getForEditing(future.id()).teams()).isEqualTo(original.teams());
    }

    @Test
    void rejectsInvalidOrClosedRaceReplacementWithoutChangingLineup() {
        var race = createRace(2027, 1, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var previousDriver = new Driver("Lando Norris");
        var teammate = new Driver("Oscar Piastri");
        var original = new RaceLineup(race.id(), List.of(new Team(
                new TeamBranding("McLaren", "MCL", "#FF8000", "#000000"),
                List.of(previousDriver, teammate)
        )));
        lineupService.save(original);

        assertThatThrownBy(() -> lineupService.replaceDriver(race.id(), previousDriver, teammate))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> lineupService.replaceDriver(
                race.id(), new Driver("Unknown driver"), new Driver("Max Verstappen")
        )).isInstanceOf(DomainValidationException.class);

        service.openRacePredictions(race.id());
        service.closeRacePredictions(race.id());
        assertThatThrownBy(() -> lineupService.replaceDriver(race.id(), previousDriver, new Driver("Max Verstappen")))
                .isInstanceOf(RaceLineupNotEditableException.class);

        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(original.teams());
    }

    @Test
    void rollsBackReplacementTogetherWithNewLineup() {
        var previous = createRace(2027, 1, "Previous Grand Prix", NOW, NOW.plusSeconds(3600));
        var race = createRace(2027, 2, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var original = lineup(previous, "McLaren", "Lando Norris");
        lineupService.save(original);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            lineupService.replaceDriver(race.id(), new Driver("Lando Norris"), new Driver("Max Verstappen"));
            assertThat(lineupService.getForEditing(race.id()).prepared()).isTrue();
            throw new IllegalStateException("Replacement rolled back");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Replacement rolled back");

        assertThat(lineupService.getForEditing(race.id()).prepared()).isFalse();
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(original.teams());
        assertThat(lineupService.getForEditing(previous.id()).teams()).isEqualTo(original.teams());
    }

    private Race createRace(int season, int round, String name, Instant openAt, Instant closeAt) {
        return service.create(season, round, RaceType.RACE, name, openAt, closeAt);
    }

    private RaceLineup lineup(Race race, String teamName, String driverName) {
        return new RaceLineup(race.id(), List.of(new Team(
                new TeamBranding(teamName, teamName, "#FF8000", "#000000"),
                List.of(new Driver(driverName))
        )));
    }
}
