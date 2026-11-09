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
import com.xdd.f1.domain.race.lineup.DriverReplacement;
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
        lineupService.addTeam(otherSeason.id(), lineup(otherSeason, "McLaren", "Lando Norris").teams().getFirst());
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
        assertThat(lineupService.addTeam(race.id(), initial.teams().getFirst())).isEqualTo(initial);
        var prepared = lineupService.getForEditing(race.id());
        assertThat(prepared.prepared()).isTrue();
        assertThat(prepared.teams()).isEqualTo(initial.teams());

        var beforeOpening = new RaceLineup(race.id(), List.of(new Team(
                new TeamBranding("McLaren", "MCL", "#FF8000", "#000000"),
                List.of(new Driver("Lando Norris"), new Driver("Oscar Piastri"))
        )));
        lineupService.updateTeam(race.id(), "McLaren", beforeOpening.teams().getFirst());
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(beforeOpening.teams());
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.SCHEDULED);

        service.openRacePredictions(race.id());
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(beforeOpening.teams());
        assertThat(lineupService.getForEditing(race.id()).prepared()).isTrue();
        var whileOpen = new RaceLineup(race.id(), List.of(new Team(
                new TeamBranding("McLaren", "MCL", "#FFFFFF", "#FF8000"),
                List.of(new Driver("Oscar Piastri"), new Driver("Lando Norris"))
        )));
        lineupService.updateTeam(race.id(), "McLaren", whileOpen.teams().getFirst());

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
        prepareLineup(prepared);

        var preview = lineupService.getForEditing(sprint.id());
        assertThat(preview.race()).isEqualTo(sprint);
        assertThat(preview.teams()).isEqualTo(historical.teams());
        assertThat(preview.prepared()).isFalse();
        var storedPreview = transactions.execute(status -> lineups.findByRaceId(sprint.id()));
        assertThat(storedPreview).isEmpty();

        lineupService.updateTeam(sprint.id(), "Mercedes", preview.teams().getFirst());
        var moved = lineup(sprint, "Ferrari", "Lewis Hamilton");
        lineupService.updateTeam(sprint.id(), "Mercedes", moved.teams().getFirst());

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
        prepareLineup(initial);
        service.openRacePredictions(race.id());
        service.closeRacePredictions(race.id());
        if (finished) {
            service.finishRace(race.id());
        }

        assertThatThrownBy(() -> lineupService.updateTeam(
                race.id(), "McLaren", lineup(race, "Ferrari", "Lando Norris").teams().getFirst()
        ))
                .isInstanceOf(RaceLineupNotEditableException.class);
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(initial.teams());
    }

    @Test
    void inheritsSprintLineupWhenOpeningRaceAndDoesNotRecopyOnRepeatedOpening() {
        var sprint = service.create(2027, 1, RaceType.SPRINT, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var race = createRace(2027, 1, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var original = lineup(sprint, "McLaren", "Lando Norris");
        prepareLineup(original);
        service.openRacePredictions(sprint.id());
        service.closeRacePredictions(sprint.id());

        service.openRacePredictions(race.id());

        var inherited = transactions.execute(status -> lineups.findByRaceId(race.id()));
        assertThat(inherited).contains(new RaceLineup(race.id(), original.teams()));
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.OPEN);

        var edited = lineup(race, "Ferrari", "Lando Norris");
        lineupService.updateTeam(race.id(), "McLaren", edited.teams().getFirst());
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
        prepareLineup(lineup(previous, "McLaren", "Lando Norris"));

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
    void serializesOpeningAndEditorWithoutOverwritingTeamChanges(boolean editorFirst) throws Exception {
        var previous = createRace(2027, 1, "Previous Grand Prix", NOW, NOW.plusSeconds(3600));
        var race = createRace(2027, 2, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var original = lineup(previous, "McLaren", "Lando Norris");
        var edited = lineup(race, "Ferrari", "Lewis Hamilton");
        prepareLineup(original);

        var firstInserted = new CountDownLatch(1);
        var competingOperationStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            var created = invocation.callRealMethod();
            firstInserted.countDown();
            assertThat(competingOperationStarted.await(10, TimeUnit.SECONDS)).isTrue();
            return created;
        }).when(lineups).createIfAbsent(argThat(lineup -> lineup.raceId().equals(race.id())));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                if (editorFirst) {
                    lineupService.updateTeam(race.id(), "McLaren", edited.teams().getFirst());
                } else {
                    service.openRacePredictions(race.id());
                }
            });
            assertThat(firstInserted.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                competingOperationStarted.countDown();
                if (editorFirst) {
                    service.openRacePredictions(race.id());
                } else {
                    lineupService.updateTeam(race.id(), "McLaren", edited.teams().getFirst());
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
    void savesTeamWithDriverReplacementOnlyInTargetRaceAndKeepsRepeatedSaveIdempotent(boolean predictionsOpen) {
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
        prepareLineup(original);
        prepareLineup(new RaceLineup(future.id(), original.teams()));
        if (predictionsOpen) {
            service.openRacePredictions(race.id());
        }

        var expected = new RaceLineup(race.id(), List.of(
                new Team(original.teams().getFirst().branding(), List.of(newDriver, new Driver("Oscar Piastri"))),
                original.teams().getLast()
        ));
        var replaced = lineupService.updateTeam(race.id(), "McLaren", expected.teams().getFirst());
        assertThat(replaced).isEqualTo(expected);
        assertThat(original.teams().getFirst().driverReplacementsTo(expected.teams().getFirst()))
                .containsExactly(new DriverReplacement(previousDriver, newDriver));
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(expected.teams());
        assertThat(lineupService.getForEditing(race.id()).prepared()).isTrue();

        assertThat(lineupService.updateTeam(race.id(), "McLaren", expected.teams().getFirst())).isEqualTo(expected);
        assertThat(expected.teams().getFirst().driverReplacementsTo(expected.teams().getFirst())).isEmpty();
        if (!predictionsOpen) {
            service.openRacePredictions(race.id());
        }

        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(expected.teams());
        assertThat(lineupService.getForEditing(previous.id()).teams()).isEqualTo(original.teams());
        assertThat(lineupService.getForEditing(future.id()).teams()).isEqualTo(original.teams());
    }

    @Test
    void rejectsInvalidOrClosedRaceTeamUpdateWithoutChangingLineup() {
        var race = createRace(2027, 1, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var previousDriver = new Driver("Lando Norris");
        var teammate = new Driver("Oscar Piastri");
        var original = new RaceLineup(race.id(), List.of(new Team(
                new TeamBranding("McLaren", "MCL", "#FF8000", "#000000"),
                List.of(previousDriver, teammate)
        )));
        prepareLineup(original);

        var duplicate = new Team(original.teams().getFirst().branding(), List.of(teammate, teammate));
        assertThatThrownBy(() -> lineupService.updateTeam(race.id(), "McLaren", duplicate))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> lineupService.updateTeam(
                race.id(), "Unknown team", lineup(race, "Ferrari", "Max Verstappen").teams().getFirst()
        )).isInstanceOf(DomainValidationException.class);
        var removed = new Team(original.teams().getFirst().branding(), List.of(teammate));
        assertThatThrownBy(() -> lineupService.updateTeam(race.id(), "McLaren", removed))
                .isInstanceOf(DomainValidationException.class);

        service.openRacePredictions(race.id());
        service.closeRacePredictions(race.id());
        assertThatThrownBy(() -> lineupService.updateTeam(race.id(), "McLaren", original.teams().getFirst()))
                .isInstanceOf(RaceLineupNotEditableException.class);

        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(original.teams());
    }

    @Test
    void rollsBackReplacementTogetherWithNewLineup() {
        var previous = createRace(2027, 1, "Previous Grand Prix", NOW, NOW.plusSeconds(3600));
        var race = createRace(2027, 2, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var original = lineup(previous, "McLaren", "Lando Norris");
        prepareLineup(original);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            lineupService.updateTeam(race.id(), "McLaren", lineup(race, "McLaren", "Max Verstappen").teams().getFirst());
            assertThat(lineupService.getForEditing(race.id()).prepared()).isTrue();
            throw new IllegalStateException("Replacement rolled back");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Replacement rolled back");

        assertThat(lineupService.getForEditing(race.id()).prepared()).isFalse();
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(original.teams());
        assertThat(lineupService.getForEditing(previous.id()).teams()).isEqualTo(original.teams());
    }

    @Test
    void addsTeamsAndSavesOnlyChangedRowIncludingRenameAndBranding() {
        var race = createRace(2027, 1, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var mclaren = new Team(
                new TeamBranding("McLaren", "MCL", "#FF8000", "#000000"),
                List.of(new Driver("Lando Norris"), new Driver("Oscar Piastri"))
        );
        var ferrari = new Team(
                new TeamBranding("Ferrari", "FER", "#E80020", "#FFFFFF"),
                List.of(new Driver("Charles Leclerc"), new Driver("Lewis Hamilton"))
        );
        lineupService.addTeam(race.id(), mclaren);
        lineupService.addTeam(race.id(), ferrari);
        assertThatThrownBy(() -> lineupService.addTeam(race.id(), ferrari))
                .isInstanceOf(DomainValidationException.class);

        var updatedFerrari = new Team(
                new TeamBranding("Ferrari", "SF", "#FF0000", "#000000"), ferrari.drivers()
        );
        lineupService.updateTeam(race.id(), "Ferrari", updatedFerrari);
        var renamedMcLaren = new Team(
                new TeamBranding("McLaren Racing", "MCL", "#FFFFFF", "#FF8000"),
                List.of(new Driver("Oscar Piastri"), new Driver("Lando Norris"))
        );
        lineupService.updateTeam(race.id(), "McLaren", renamedMcLaren);

        assertThat(lineupService.getForEditing(race.id()).teams()).containsExactly(renamedMcLaren, updatedFerrari);
        assertThat(mclaren.driverReplacementsTo(renamedMcLaren)).isEmpty();
        assertThat(lineupService.updateTeam(race.id(), "McLaren", renamedMcLaren).teams())
                .containsExactly(renamedMcLaren, updatedFerrari);
    }

    @Test
    void detectsBothReplacementsInRowAndIgnoresPositionOfRetainedDriver() {
        var race = createRace(2027, 1, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var norris = new Driver("Lando Norris");
        var piastri = new Driver("Oscar Piastri");
        var verstappen = new Driver("Max Verstappen");
        var lawson = new Driver("Liam Lawson");
        var original = new Team(
                new TeamBranding("McLaren", "MCL", "#FF8000", "#000000"), List.of(norris, piastri)
        );
        var shifted = new Team(original.branding(), List.of(piastri, verstappen));
        assertThat(original.driverReplacementsTo(shifted))
                .containsExactly(new DriverReplacement(norris, verstappen));

        var updated = new Team(original.branding(), List.of(verstappen, lawson));
        assertThat(original.driverReplacementsTo(updated)).containsExactly(
                new DriverReplacement(norris, verstappen), new DriverReplacement(piastri, lawson)
        );
        lineupService.addTeam(race.id(), original);
        lineupService.updateTeam(race.id(), "McLaren", updated);

        assertThat(lineupService.getForEditing(race.id()).teams()).containsExactly(updated);
        assertThat(updated.driverReplacementsTo(updated)).isEmpty();
    }

    @ParameterizedTest(name = "predictionsOpen={0}")
    @ValueSource(booleans = {false, true})
    void swapsExistingDriversOnlyInTargetRaceAndCanSwapThemBack(boolean predictionsOpen) {
        var previous = createRace(2027, 1, "Previous Grand Prix", NOW, NOW.plusSeconds(3600));
        var race = createRace(2027, 2, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var future = createRace(2027, 3, "Future Grand Prix", NOW.plusSeconds(7200), NOW.plusSeconds(10800));
        var original = redBullLineup(previous);
        prepareLineup(original);
        prepareLineup(new RaceLineup(future.id(), original.teams()));
        if (predictionsOpen) {
            service.openRacePredictions(race.id());
        }
        var lawson = new Driver("Lawson");
        var hadjar = new Driver("Hadjar");

        var swapped = lineupService.swapDrivers(race.id(), lawson, hadjar);

        var expected = new RaceLineup(race.id(), List.of(
                new Team(original.teams().getFirst().branding(), List.of(new Driver("Verstappen"), hadjar)),
                new Team(original.teams().getLast().branding(), List.of(new Driver("Tsunoda"), lawson))
        ));
        assertThat(swapped).isEqualTo(expected);
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(expected.teams());
        assertThat(lineupService.getForEditing(race.id()).prepared()).isTrue();
        assertThat(lineupService.swapDrivers(race.id(), hadjar, hadjar)).isEqualTo(expected);
        assertThat(lineupService.getForEditing(previous.id()).teams()).isEqualTo(original.teams());
        assertThat(lineupService.getForEditing(future.id()).teams()).isEqualTo(original.teams());

        var restored = lineupService.swapDrivers(race.id(), lawson, hadjar);
        assertThat(restored).isEqualTo(new RaceLineup(race.id(), original.teams()));
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(original.teams());
    }

    @Test
    void rejectsSwapWithUnknownDriverOrClosedRaceWithoutChangingLineup() {
        var race = createRace(2027, 1, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var original = redBullLineup(race);
        prepareLineup(original);

        assertThatThrownBy(() -> lineupService.swapDrivers(race.id(), new Driver("Lawson"), new Driver("Unknown driver")))
                .isInstanceOf(DomainValidationException.class);
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(original.teams());

        service.openRacePredictions(race.id());
        service.closeRacePredictions(race.id());
        assertThatThrownBy(() -> lineupService.swapDrivers(race.id(), new Driver("Lawson"), new Driver("Hadjar")))
                .isInstanceOf(RaceLineupNotEditableException.class);
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(original.teams());
    }

    @Test
    void rollsBackBothSidesOfSwapTogetherWithInheritedLineup() {
        var previous = createRace(2027, 1, "Previous Grand Prix", NOW, NOW.plusSeconds(3600));
        var race = createRace(2027, 2, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var original = redBullLineup(previous);
        prepareLineup(original);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            lineupService.swapDrivers(race.id(), new Driver("Lawson"), new Driver("Hadjar"));
            var current = lineupService.getForEditing(race.id());
            assertThat(current.prepared()).isTrue();
            assertThat(current.teams().getFirst().drivers()).containsExactly(new Driver("Verstappen"), new Driver("Hadjar"));
            assertThat(current.teams().getLast().drivers()).containsExactly(new Driver("Tsunoda"), new Driver("Lawson"));
            throw new IllegalStateException("Swap rolled back");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Swap rolled back");

        assertThat(lineupService.getForEditing(race.id()).prepared()).isFalse();
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(original.teams());
        assertThat(lineupService.getForEditing(previous.id()).teams()).isEqualTo(original.teams());
    }

    private RaceLineup redBullLineup(Race race) {
        return new RaceLineup(race.id(), List.of(
                new Team(
                        new TeamBranding("Red Bull", "RBR", "#1F00B8", "#FFFFFF"),
                        List.of(new Driver("Verstappen"), new Driver("Lawson"))
                ),
                new Team(
                        new TeamBranding("Racing Bulls", "RCB", "#6692FF", "#111111"),
                        List.of(new Driver("Tsunoda"), new Driver("Hadjar"))
                )
        ));
    }

    private void prepareLineup(RaceLineup lineup) {
        transactions.executeWithoutResult(status -> lineups.createIfAbsent(lineup));
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
