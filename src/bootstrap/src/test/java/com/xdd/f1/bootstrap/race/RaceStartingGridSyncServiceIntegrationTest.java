package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.application.race.RaceStartingGridSyncService;
import com.xdd.f1.application.race.StartingGridProvider;
import com.xdd.f1.application.race.lineup.RaceLineupRepository;
import com.xdd.f1.application.race.result.RaceResultService;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.contract.race.RaceStartingGridUpdatedEvent;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RecordApplicationEvents
class RaceStartingGridSyncServiceIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2027-03-01T12:00:00Z");
    private static final List<Driver> GRID = List.of(new Driver("Camara"), new Driver("Bearman"));

    private final RaceStartingGridSyncService sync;
    private final RaceService races;
    private final RaceResultService results;
    private final RaceLineupRepository lineups;
    private final RaceJpaRepository raceJpaRepository;
    private final TransactionTemplate transactions;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private StartingGridProvider provider;

    @Autowired
    RaceStartingGridSyncServiceIntegrationTest(RaceStartingGridSyncService sync, RaceService races, RaceResultService results,
                                              RaceLineupRepository lineups, RaceJpaRepository raceJpaRepository,
                                              PlatformTransactionManager transactionManager) {
        this.sync = sync;
        this.races = races;
        this.results = results;
        this.lineups = lineups;
        this.raceJpaRepository = raceJpaRepository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @AfterEach
    void cleanUp() {
        raceJpaRepository.deleteAll();
    }

    @Test
    void pollsOnlyCurrentOpenAndEarlyClosedRacesOutsideTransactionsAndKeepsRepeatsIdempotent(ApplicationEvents events) {
        var race = createRace(2027, 1, RaceType.RACE, NOW, NOW.plusSeconds(7200), RaceStatus.OPEN);
        var sprint = createRace(2027, 1, RaceType.SPRINT, NOW.minusSeconds(3600), NOW.plusSeconds(3600), RaceStatus.CLOSED);
        createRace(2027, 2, RaceType.RACE, NOW.minusSeconds(3600), NOW, RaceStatus.CLOSED);
        createRace(2027, 3, RaceType.RACE, NOW.minusSeconds(1), NOW.plusSeconds(3600), RaceStatus.SCHEDULED);
        createRace(2027, 4, RaceType.RACE, NOW.plusSeconds(1), NOW.plusSeconds(3600), RaceStatus.OPEN);
        createRace(2027, 5, RaceType.RACE, NOW.minusSeconds(3600), NOW.plusSeconds(3600), RaceStatus.FINISHED);
        createRace(2026, 20, RaceType.RACE, NOW.minusSeconds(86400), NOW.minusSeconds(3600), RaceStatus.CLOSED);
        when(provider.loadStartingGrid(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return GRID;
        });

        sync.syncStartingGrids();

        var requested = ArgumentCaptor.forClass(Race.class);
        verify(provider, times(2)).loadStartingGrid(requested.capture());
        assertThat(requested.getAllValues()).extracting(Race::id).containsExactly(sprint.id(), race.id());
        assertThat(results.getForEditing(race.id()).result().startingGrid()).containsExactlyElementsOf(GRID);
        assertThat(results.getForEditing(sprint.id()).result().startingGrid()).containsExactlyElementsOf(GRID);
        assertThat(events.stream(RaceStartingGridUpdatedEvent.class).count()).isEqualTo(2);

        sync.syncStartingGrids();

        assertThat(events.stream(RaceStartingGridUpdatedEvent.class).count()).isEqualTo(2);
    }

    @Test
    void keepsSavedGridsOnErrorsAndEmptyResponsesContinuesOtherRacesAndRetriesOnTheNextTick(ApplicationEvents events) {
        var sprint = createRace(2027, 1, RaceType.SPRINT, NOW.minusSeconds(3600), NOW.plusSeconds(3600), RaceStatus.CLOSED);
        var race = createRace(2027, 1, RaceType.RACE, NOW, NOW.plusSeconds(7200), RaceStatus.OPEN);
        when(provider.loadStartingGrid(sprint)).thenReturn(GRID)
                .thenThrow(new ResourceAccessException("OpenF1 timed out"))
                .thenReturn(List.of()).thenReturn(GRID.reversed());
        when(provider.loadStartingGrid(race)).thenReturn(GRID).thenReturn(GRID.reversed());
        sync.syncStartingGrids();

        sync.syncStartingGrids();

        assertThat(results.getForEditing(sprint.id()).result().startingGrid()).containsExactlyElementsOf(GRID);
        assertThat(results.getForEditing(race.id()).result().startingGrid()).containsExactlyElementsOf(GRID.reversed());
        assertThat(events.stream(RaceStartingGridUpdatedEvent.class).count()).isEqualTo(3);

        sync.syncStartingGrids();

        assertThat(results.getForEditing(sprint.id()).result().startingGrid()).containsExactlyElementsOf(GRID);
        assertThat(events.stream(RaceStartingGridUpdatedEvent.class).count()).isEqualTo(3);

        sync.syncStartingGrids();

        assertThat(results.getForEditing(sprint.id()).result().startingGrid()).containsExactlyElementsOf(GRID.reversed());
        assertThat(events.stream(RaceStartingGridUpdatedEvent.class).count()).isEqualTo(4);
    }

    @Test
    void manuallySyncsAFinishedPastRaceAndKeepsItsGridOnEmptyResponsesAndErrors(ApplicationEvents events) {
        var race = createRace(2026, 20, RaceType.RACE, NOW.minusSeconds(172800), NOW.minusSeconds(86400), RaceStatus.FINISHED);
        when(provider.loadStartingGrid(race)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return GRID;
        });

        var saved = sync.syncStartingGrid(race.id());

        assertThat(saved.startingGrid()).containsExactlyElementsOf(GRID);
        assertThat(races.getById(race.id()).status()).isEqualTo(RaceStatus.FINISHED);
        assertThat(sync.syncStartingGrid(race.id())).isEqualTo(saved);
        assertThat(events.stream(RaceStartingGridUpdatedEvent.class).count()).isEqualTo(1);

        when(provider.loadStartingGrid(race)).thenReturn(List.of());
        assertThatThrownBy(() -> sync.syncStartingGrid(race.id())).isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("no published starting grid");
        when(provider.loadStartingGrid(race)).thenReturn(List.of(new Driver("Unknown")));
        assertThatThrownBy(() -> sync.syncStartingGrid(race.id())).isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("missing from this race's lineup");
        when(provider.loadStartingGrid(race)).thenThrow(new ResourceAccessException("OpenF1 timed out"));
        assertThatThrownBy(() -> sync.syncStartingGrid(race.id())).isInstanceOf(ResourceAccessException.class);

        assertThat(results.getForEditing(race.id()).result()).isEqualTo(saved);
        assertThat(events.stream(RaceStartingGridUpdatedEvent.class).count()).isEqualTo(1);
    }

    @Test
    void doesNotApplyAGridWhenTheRaceFinishesDuringTheExternalRequest(ApplicationEvents events) {
        var race = createRace(2027, 1, RaceType.RACE, NOW, NOW.plusSeconds(3600), RaceStatus.OPEN);
        results.applyStartingGrid(race, GRID);
        when(provider.loadStartingGrid(race)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            races.closeRacePredictions(race.id());
            races.finishRace(race.id());
            return GRID.reversed();
        });

        sync.syncStartingGrids();

        assertThat(results.getForEditing(race.id()).result().startingGrid()).containsExactlyElementsOf(GRID);
        assertThat(events.stream(RaceStartingGridUpdatedEvent.class).count()).isEqualTo(1);
    }

    private Race createRace(int season, int round, RaceType type, Instant openAt, Instant closeAt, RaceStatus status) {
        var race = races.create(season, round, type, "Grand Prix", openAt, closeAt);
        transactions.executeWithoutResult(transaction -> lineups.createIfAbsent(new RaceLineup(race.id(), List.of(
                new Team(new TeamBranding("Haas", "HAS", "#FFFFFF", "#000000"), GRID)
        ))));
        if (status != RaceStatus.SCHEDULED) {
            races.openRacePredictions(race.id());
        }
        if (status == RaceStatus.CLOSED || status == RaceStatus.FINISHED) {
            races.closeRacePredictions(race.id());
        }
        if (status == RaceStatus.FINISHED) {
            races.finishRace(race.id());
        }
        return races.getById(race.id());
    }
}
