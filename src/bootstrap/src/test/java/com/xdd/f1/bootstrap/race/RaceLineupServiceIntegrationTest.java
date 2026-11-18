package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.application.race.lineup.RaceLineupConflictException;
import com.xdd.f1.application.race.lineup.RaceLineupNotEditableException;
import com.xdd.f1.application.race.lineup.RaceLineupNotFoundException;
import com.xdd.f1.application.race.lineup.RaceLineupNotPreparedException;
import com.xdd.f1.application.race.lineup.RaceLineupRepository;
import com.xdd.f1.application.race.lineup.RaceLineupService;
import com.xdd.f1.application.race.result.RaceResultService;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.DriverReplacement;
import com.xdd.f1.domain.race.lineup.RaceLineup;
import com.xdd.f1.domain.race.lineup.RaceLineupChanges.TeamEdit;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final RaceRepository races;
    private final RaceJpaRepository jpaRepository;
    private final RaceLineupService lineupService;
    private final RaceResultService results;
    private final TransactionTemplate transactions;

    @MockitoBean
    private Clock clock;

    @MockitoSpyBean
    private RaceLineupRepository lineups;

    @Autowired
    RaceLineupServiceIntegrationTest(
            RaceService service,
            RaceRepository races,
            RaceJpaRepository jpaRepository,
            RaceLineupService lineupService,
            RaceResultService results,
            PlatformTransactionManager transactionManager
    ) {
        this.service = service;
        this.races = races;
        this.jpaRepository = jpaRepository;
        this.lineupService = lineupService;
        this.results = results;
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
        prepareLineup(lineup(otherSeason, "McLaren", "Lando Norris"));
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
        assertThat(lineupService.save(race.id(), empty.teams(),
                List.of(new TeamEdit(null, initial.teams().getFirst())), List.of()).teams()).isEqualTo(initial.teams());
        var prepared = lineupService.getForEditing(race.id());
        assertThat(prepared.prepared()).isTrue();
        assertThat(prepared.teams()).isEqualTo(initial.teams());

        var beforeOpening = new RaceLineup(race.id(), List.of(new Team(
                new TeamBranding("McLaren", "MCL", "#FF8000", "#000000"),
                List.of(new Driver("Lando Norris"), new Driver("Oscar Piastri"))
        )));
        editTeam(race, "McLaren", beforeOpening.teams().getFirst());
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(beforeOpening.teams());
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.SCHEDULED);

        service.openRacePredictions(race.id());
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(beforeOpening.teams());
        assertThat(lineupService.getForEditing(race.id()).prepared()).isTrue();
        var whileOpen = new RaceLineup(race.id(), List.of(new Team(
                new TeamBranding("McLaren", "MCL", "#FFFFFF", "#FF8000"),
                List.of(new Driver("Oscar Piastri"), new Driver("Lando Norris"))
        )));
        editTeam(race, "McLaren", whileOpen.teams().getFirst());

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

        editTeam(sprint, "Mercedes", preview.teams().getFirst());
        var moved = lineup(sprint, "Ferrari", "Lewis Hamilton");
        editTeam(sprint, "Mercedes", moved.teams().getFirst());

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
    void allowsOnlyTeamMetadataAndTeamOrderChangesForClosedOrFinishedRaces(boolean finished) {
        var race = createRace(2027, 1, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var initial = redBullLineup(race);
        prepareLineup(initial);
        service.openRacePredictions(race.id());
        var base = lineupService.getForEditing(race.id()).teams();
        service.closeRacePredictions(race.id());
        if (finished) {
            service.finishRace(race.id());
        }

        var redBull = base.getFirst();
        var racingBulls = base.getLast();
        var unchanged = new TeamEdit(racingBulls.branding().name(), racingBulls);
        var renamed = new Team(new TeamBranding("Oracle Red Bull Racing", "RB", "#001D54", "#E5E5E5"), redBull.drivers());
        var edit = new TeamEdit(redBull.branding().name(), renamed);
        var replacement = new Driver("New Driver");

        assertThatThrownBy(() -> lineupService.save(race.id(), base, List.of(unchanged, new TeamEdit(
                redBull.branding().name(), new Team(renamed.branding(), List.of(redBull.drivers().getFirst(), replacement))
        )), List.of())).isInstanceOf(RaceLineupNotEditableException.class);
        assertThatThrownBy(() -> lineupService.save(race.id(), base, List.of(unchanged, new TeamEdit(
                redBull.branding().name(), new Team(renamed.branding(), redBull.drivers().reversed())
        )), List.of())).isInstanceOf(RaceLineupNotEditableException.class);
        assertThatThrownBy(() -> saveByCurrentNames(race, base, swappedRedBullTeams(initial)))
                .isInstanceOf(RaceLineupNotEditableException.class);
        assertThatThrownBy(() -> lineupService.save(race.id(), base, List.of(unchanged), List.of()))
                .isInstanceOf(RaceLineupNotEditableException.class);
        assertThatThrownBy(() -> lineupService.save(race.id(), base, List.of(unchanged, edit, new TeamEdit(
                null, lineup(race, "Audi", "Bortoleto").teams().getFirst()
        )), List.of())).isInstanceOf(RaceLineupNotEditableException.class);
        assertThatThrownBy(() -> lineupService.save(race.id(), base, List.of(unchanged, edit),
                List.of(new DriverReplacement(redBull.drivers().getLast(), replacement))))
                .isInstanceOf(RaceLineupNotEditableException.class);
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(base);

        var saved = lineupService.save(race.id(), base, List.of(unchanged, edit), List.of());
        assertThat(saved.race().status()).isEqualTo(finished ? RaceStatus.FINISHED : RaceStatus.CLOSED);
        assertThat(saved.teams()).containsExactly(racingBulls, renamed);
        assertThat(lineupService.getForEditing(race.id())).isEqualTo(saved);
    }

    @Test
    void doesNotInheritOrCreateAMissingHistoricalLineup() {
        var previous = createRace(2026, 1, "Earlier Grand Prix", NOW, NOW.plusSeconds(1800));
        var historical = createRace(2026, 2, "Historical Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var previousLineup = redBullLineup(previous);
        prepareLineup(previousLineup);
        transactions.executeWithoutResult(status -> races.update(historical.open().close().finish()));

        var editor = lineupService.getForEditing(historical.id());
        assertThat(editor.prepared()).isFalse();
        assertThat(editor.teams()).isEmpty();
        assertThatThrownBy(() -> lineupService.save(historical.id(), List.of(),
                List.of(new TeamEdit(null, previousLineup.teams().getFirst())), List.of()))
                .isInstanceOf(RaceLineupNotFoundException.class);
        var stored = transactions.execute(status -> lineups.findByRaceId(historical.id()));
        assertThat(stored).isEmpty();
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
        editTeam(race, "McLaren", edited.teams().getFirst());
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
        var baseTeams = lineupService.getForEditing(race.id()).teams();
        var edits = List.of(new TeamEdit("McLaren", edited.teams().getFirst()));

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
                    lineupService.save(race.id(), baseTeams, edits, List.of());
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
                    lineupService.save(race.id(), baseTeams, edits, List.of());
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
    void savesDriverReplacementOnlyInTargetRaceAndKeepsUnchangedSaveIdempotent(boolean predictionsOpen) {
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
        editTeam(race, "McLaren", expected.teams().getFirst());
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(expected.teams());
        assertThat(lineupService.getForEditing(race.id()).prepared()).isTrue();

        editTeam(race, "McLaren", expected.teams().getFirst());
        if (!predictionsOpen) {
            service.openRacePredictions(race.id());
        }

        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(expected.teams());
        assertThat(lineupService.getForEditing(previous.id()).teams()).isEqualTo(original.teams());
        assertThat(lineupService.getForEditing(future.id()).teams()).isEqualTo(original.teams());
    }

    @Test
    void limitsBadgesToThreeCharactersWhenAddingOrUpdatingATeam() {
        var race = createRace(2027, 1, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var valid = lineup(race, "McLaren", "Lando Norris").teams().getFirst();
        var tooLong = new Team(new TeamBranding("McLaren", "LONG", "#FF8000", "#000000"), valid.drivers());

        assertThatThrownBy(() -> editTeam(race, null, tooLong))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("3 characters");
        assertThat(lineupService.getForEditing(race.id()).prepared()).isFalse();

        editTeam(race, null, valid);
        assertThatThrownBy(() -> editTeam(race, "McLaren", tooLong))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("3 characters");
        assertThat(lineupService.getForEditing(race.id()).teams()).containsExactly(valid);
    }

    @Test
    void rejectsInvalidTeamUpdateWithoutChangingLineup() {
        var race = createRace(2027, 1, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var previousDriver = new Driver("Lando Norris");
        var teammate = new Driver("Oscar Piastri");
        var original = new RaceLineup(race.id(), List.of(new Team(
                new TeamBranding("McLaren", "MCL", "#FF8000", "#000000"),
                List.of(previousDriver, teammate)
        )));
        prepareLineup(original);

        var duplicate = new Team(original.teams().getFirst().branding(), List.of(teammate, teammate));
        assertThatThrownBy(() -> editTeam(race, "McLaren", duplicate))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> editTeam(
                race, "Unknown team", lineup(race, "Ferrari", "Max Verstappen").teams().getFirst()
        )).isInstanceOf(DomainValidationException.class);
        var removed = new Team(original.teams().getFirst().branding(), List.of(teammate));
        assertThatThrownBy(() -> editTeam(race, "McLaren", removed))
                .isInstanceOf(DomainValidationException.class);

        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(original.teams());
    }

    @Test
    void rollsBackReplacementTogetherWithNewLineup() {
        var previous = createRace(2027, 1, "Previous Grand Prix", NOW, NOW.plusSeconds(3600));
        var race = createRace(2027, 2, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var original = lineup(previous, "McLaren", "Lando Norris");
        prepareLineup(original);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            editTeam(race, "McLaren", lineup(race, "McLaren", "Max Verstappen").teams().getFirst());
            assertThat(lineupService.getForEditing(race.id()).prepared()).isTrue();
            throw new IllegalStateException("Replacement rolled back");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Replacement rolled back");

        assertThat(lineupService.getForEditing(race.id()).prepared()).isFalse();
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(original.teams());
        assertThat(lineupService.getForEditing(previous.id()).teams()).isEqualTo(original.teams());
    }

    @Test
    void savesMultipleTeamEditsAndRejectsAnOutdatedDraft() {
        var race = createRace(2027, 1, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var mclaren = new Team(
                new TeamBranding("McLaren", "MCL", "#FF8000", "#000000"),
                List.of(new Driver("Lando Norris"), new Driver("Oscar Piastri"))
        );
        var ferrari = new Team(
                new TeamBranding("Ferrari", "FER", "#E80020", "#FFFFFF"),
                List.of(new Driver("Charles Leclerc"), new Driver("Lewis Hamilton"))
        );
        var base = List.of(mclaren, ferrari);
        lineupService.save(race.id(), List.of(), List.of(new TeamEdit(null, mclaren), new TeamEdit(null, ferrari)), List.of());
        assertThatThrownBy(() -> editTeam(race, null, ferrari))
                .isInstanceOf(DomainValidationException.class);

        var updatedFerrari = new Team(
                new TeamBranding("Ferrari", "SF", "#FF0000", "#000000"), ferrari.drivers()
        );
        var renamedMcLaren = new Team(
                new TeamBranding("McLaren Racing", "MCL", "#FFFFFF", "#FF8000"),
                List.of(new Driver("Oscar Piastri"), new Driver("Lando Norris"))
        );
        lineupService.save(race.id(), base,
                List.of(new TeamEdit("Ferrari", updatedFerrari), new TeamEdit("McLaren", renamedMcLaren)), List.of());
        assertThatThrownBy(() -> lineupService.save(race.id(), base,
                List.of(new TeamEdit("McLaren", mclaren), new TeamEdit("Ferrari", ferrari)), List.of()))
                .isInstanceOf(RaceLineupConflictException.class);
        assertThat(lineupService.getForEditing(race.id()).teams()).containsExactly(updatedFerrari, renamedMcLaren);
    }

    @Test
    void requiresExplicitPairsForAmbiguousReplacementsAndRejectsInvalidPairs() {
        var race = createRace(2027, 1, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var norris = new Driver("Lando Norris");
        var piastri = new Driver("Oscar Piastri");
        var verstappen = new Driver("Max Verstappen");
        var lawson = new Driver("Liam Lawson");
        var original = new Team(
                new TeamBranding("McLaren", "MCL", "#FF8000", "#000000"), List.of(norris, piastri)
        );
        var updated = new Team(original.branding(), List.of(verstappen, lawson));
        editTeam(race, null, original);
        var base = List.of(original);
        var edited = List.of(new TeamEdit("McLaren", updated));
        assertThatThrownBy(() -> lineupService.save(race.id(), base, edited, List.of()))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("Choose a replacement");
        assertThatThrownBy(() -> lineupService.save(race.id(), base, edited, List.of(
                new DriverReplacement(norris, verstappen), new DriverReplacement(piastri, verstappen)
        ))).isInstanceOf(DomainValidationException.class).hasMessageContaining("distinct");
        assertThat(lineupService.getForEditing(race.id()).teams()).containsExactly(original);
        lineupService.save(race.id(), base, edited, List.of(
                new DriverReplacement(norris, verstappen), new DriverReplacement(piastri, lawson)
        ));

        assertThat(lineupService.getForEditing(race.id()).teams()).containsExactly(updated);
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
        var expected = swappedRedBullTeams(original);
        saveByCurrentNames(race, original.teams(), expected);
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(expected);
        assertThat(lineupService.getForEditing(race.id()).prepared()).isTrue();
        assertThat(lineupService.getForEditing(previous.id()).teams()).isEqualTo(original.teams());
        assertThat(lineupService.getForEditing(future.id()).teams()).isEqualTo(original.teams());

        saveByCurrentNames(race, expected, original.teams());
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(original.teams());
    }

    @Test
    void removesTeamFromScheduledRaceAndInheritsTheUpdatedLineupWithoutChangingSavedSnapshots() {
        var previous = createRace(2026, 1, "Previous season", NOW, NOW.plusSeconds(1800));
        var race = createRace(2027, 1, "Season opener", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var next = createRace(2027, 2, "Next Grand Prix", NOW.plusSeconds(7200), NOW.plusSeconds(10800));
        var prepared = createRace(2027, 3, "Prepared Grand Prix", NOW.plusSeconds(10800), NOW.plusSeconds(14400));
        var base = redBullLineup(previous).teams();
        prepareLineup(new RaceLineup(previous.id(), base));
        prepareLineup(new RaceLineup(prepared.id(), base));
        var kept = base.getFirst();

        saveByCurrentNames(race, base, List.of(kept));

        assertThat(lineupService.getForEditing(race.id()).teams()).containsExactly(kept);
        assertThat(lineupService.getForEditing(next.id()).teams()).containsExactly(kept);
        service.openRacePredictions(next.id());
        assertThat(lineupService.getForEditing(next.id()).teams()).containsExactly(kept);
        assertThat(lineupService.getForEditing(previous.id()).teams()).isEqualTo(base);
        assertThat(lineupService.getForEditing(prepared.id()).teams()).isEqualTo(base);
    }

    @Test
    void keepsReassignedDriversAndRejectsRemovingTheLastTeam() {
        var race = createRace(2027, 1, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var prior = redBullLineup(race);
        var kept = prior.teams().getFirst();
        var departing = prior.teams().getLast();
        var base = List.of(new Team(kept.branding(), List.of(kept.drivers().getFirst())), departing);
        prepareLineup(new RaceLineup(race.id(), base));
        var updated = new Team(kept.branding(), List.of(kept.drivers().getFirst(), departing.drivers().getLast()));

        saveByCurrentNames(race, base, List.of(updated));
        assertThat(lineupService.getForEditing(race.id()).teams()).containsExactly(updated);
        assertThatThrownBy(() -> lineupService.save(race.id(), List.of(updated), List.of(), List.of()))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("at least one team");
    }

    @Test
    void rejectsTeamRemovalWhenItWouldLeaveAnInvalidGridOrPredictionsHaveOpened() {
        var race = createRace(2027, 1, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var original = redBullLineup(race);
        var base = original.teams();
        var remaining = List.of(base.getFirst());
        prepareLineup(original);
        var grid = List.of(base.getFirst().drivers().getFirst(), base.getLast().drivers().getFirst());
        results.saveStartingGridForAdmin(race.id(), grid);

        assertThatThrownBy(() -> saveByCurrentNames(race, base, remaining))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("starting grid");
        assertThat(results.getForEditing(race.id()).result().startingGrid()).isEqualTo(grid);
        results.saveStartingGridForAdmin(race.id(), List.of(grid.getFirst()));
        service.openRacePredictions(race.id());
        assertThatThrownBy(() -> saveByCurrentNames(race, base, remaining))
                .isInstanceOf(RaceLineupNotEditableException.class).hasMessageContaining("before predictions open");
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(base);
    }

    @Test
    void rejectsPersistingAThirdDriverInATeam() {
        var race = createRace(2027, 1, "Grand Prix", NOW, NOW.plusSeconds(3600));
        var original = redBullLineup(race);
        prepareLineup(original);

        var first = original.teams().getFirst();
        assertThatThrownBy(() -> editTeam(race, first.branding().name(), new Team(first.branding(),
                List.of(first.drivers().getFirst(), first.drivers().getLast(), new Driver("New Driver")))))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("two drivers");
        assertThat(lineupService.getForEditing(race.id()).teams()).isEqualTo(original.teams());
    }

    @Test
    void rollsBackBothSidesOfSwapTogetherWithInheritedLineup() {
        var previous = createRace(2027, 1, "Previous Grand Prix", NOW, NOW.plusSeconds(3600));
        var race = createRace(2027, 2, "Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        var original = redBullLineup(previous);
        prepareLineup(original);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            saveByCurrentNames(race, original.teams(), swappedRedBullTeams(original));
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

    private List<Team> swappedRedBullTeams(RaceLineup original) {
        return List.of(
                new Team(original.teams().getFirst().branding(), List.of(new Driver("Verstappen"), new Driver("Hadjar"))),
                new Team(original.teams().getLast().branding(), List.of(new Driver("Tsunoda"), new Driver("Lawson")))
        );
    }

    private void saveByCurrentNames(Race race, List<Team> base, List<Team> updated) {
        lineupService.save(race.id(), base, updated.stream()
                .map(team -> new TeamEdit(team.branding().name(), team)).toList(), List.of());
    }

    private void editTeam(Race race, String previousName, Team team) {
        var base = lineupService.getForEditing(race.id()).teams();
        var edits = new ArrayList<>(base.stream().map(existing -> {
            var name = existing.branding().name();
            return new TeamEdit(name, name.equals(previousName) ? team : existing);
        }).toList());
        if (previousName == null || base.stream().noneMatch(existing -> existing.branding().name().equals(previousName))) {
            edits.add(new TeamEdit(previousName, team));
        }
        lineupService.save(race.id(), base, edits, List.of());
    }

    private void prepareLineup(RaceLineup lineup) {
        transactions.executeWithoutResult(status -> lineups.createIfAbsent(lineup));
    }

    private Race createRace(int season, int round, String name, Instant openAt, Instant closeAt) {
        return service.create(season, round, RaceType.RACE, name, openAt, closeAt);
    }

    private RaceLineup lineup(Race race, String teamName, String driverName) {
        var badge = teamName.substring(0, Math.min(3, teamName.length())).toUpperCase(Locale.ROOT);
        return new RaceLineup(race.id(), List.of(new Team(
                new TeamBranding(teamName, badge, "#FF8000", "#000000"),
                List.of(new Driver(driverName))
        )));
    }
}
