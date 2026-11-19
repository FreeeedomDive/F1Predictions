package com.xdd.f1.bootstrap.championship;

import com.xdd.f1.application.championship.ChampionshipPredictionService;
import com.xdd.f1.application.championship.ChampionshipSeasonRepository;
import com.xdd.f1.application.championship.ChampionshipStandingsProvider;
import com.xdd.f1.application.championship.ChampionshipStandingsProvider.Snapshot;
import com.xdd.f1.application.championship.ChampionshipStandingsService;
import com.xdd.f1.application.championship.ChampionshipStandingsSyncService;
import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.championship.ChampionshipDriverStandings;
import com.xdd.f1.domain.championship.ChampionshipSeason;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.RaceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.xdd.f1.domain.championship.ChampionshipRulesVersion.V2027;
import static com.xdd.f1.domain.championship.ChampionshipStage.MID_SEASON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChampionshipStandingsSyncServiceIntegrationTest extends AbstractIntegrationTest {
    private static final int SEASON = 2032;
    private static final Instant NOW = Instant.parse("2032-01-01T00:00:00Z");
    private static final List<String> ORDER = List.of("Norris", "Verstappen");

    private final ChampionshipSeasonRepository seasons;
    private final ChampionshipPredictionService predictions;
    private final ChampionshipStandingsService standings;
    private final RaceRepository races;
    private final TransactionLock lock;
    private final PlatformTransactionManager transactionManager;
    private final TransactionTemplate transactions;
    private final JdbcClient jdbc;
    private final ChampionshipStandingsProvider provider = mock(ChampionshipStandingsProvider.class);

    @Autowired
    ChampionshipStandingsSyncServiceIntegrationTest(ChampionshipSeasonRepository seasons, ChampionshipPredictionService predictions,
                                                     ChampionshipStandingsService standings, RaceRepository races, TransactionLock lock,
                                                     PlatformTransactionManager transactionManager, JdbcClient jdbc) {
        this.seasons = seasons;
        this.predictions = predictions;
        this.standings = standings;
        this.races = races;
        this.lock = lock;
        this.transactionManager = transactionManager;
        this.transactions = new TransactionTemplate(transactionManager);
        this.jdbc = jdbc;
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM championship_seasons WHERE season IN (:previous, :current)")
                .param("previous", SEASON - 1).param("current", SEASON).update();
        jdbc.sql("DELETE FROM races WHERE season = :season AND name = 'Championship sync race'").param("season", SEASON).update();
    }

    @Test
    void targetsTheUtcYearWithoutPollingWhenOnlyAnOlderSeasonIsConfigured() {
        predictions.configure(SEASON - 1, V2027);
        var clock = Clock.fixed(NOW, ZoneOffset.ofHours(-5));
        var sync = service(clock);
        sync.syncStandings();
        verifyNoInteractions(provider);

        predictions.configure(SEASON, V2027);
        when(provider.loadStandings(SEASON)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return Optional.of(new Snapshot(1, ORDER));
        });
        sync.syncStandings();
        assertThat(saved()).isEqualTo(new ChampionshipDriverStandings(SEASON, ORDER, 1, NOW));
        assertThat(seasons.findDriverStandings(SEASON - 1)).isEmpty();
    }

    @Test
    void acceptsCorrectionsAndNewRoundsWithoutRegressingOrChangingTheWindow() {
        var configuration = new ChampionshipSeason(SEASON, V2027, MID_SEASON, true, true, ORDER, ORDER);
        transactions.executeWithoutResult(status -> seasons.save(configuration));
        saveFinishedRace(2, RaceType.RACE);
        saveFinishedRace(5, RaceType.RACE);
        saveFinishedRace(8, RaceType.SPRINT);

        syncRound(4, ORDER, NOW);
        assertThat(seasons.findDriverStandings(SEASON)).isEmpty();
        syncRound(5, ORDER, NOW);
        var first = saved();
        syncRound(5, ORDER, NOW.plusSeconds(1));
        assertThat(saved()).isEqualTo(first);
        syncRound(5, ORDER.reversed(), NOW.plusSeconds(2));
        assertThat(saved()).isEqualTo(new ChampionshipDriverStandings(SEASON, ORDER.reversed(), 5, NOW.plusSeconds(2)));
        syncRound(6, ORDER.reversed(), NOW.plusSeconds(3));
        var newer = saved();
        assertThat(newer.lastSyncedRound()).isEqualTo(6);
        syncRound(5, ORDER, NOW.plusSeconds(4));
        assertThat(saved()).isEqualTo(newer);
        assertThat(seasons.findBySeason(SEASON)).contains(configuration);
    }

    @Test
    void keepsTheSavedSnapshotAfterAnEmptyResponseOrExternalFailure() {
        predictions.configure(SEASON, V2027);
        syncRound(5, ORDER, NOW);
        var previous = saved();
        var sync = service(Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
        when(provider.loadStandings(SEASON)).thenReturn(Optional.empty());
        sync.syncStandings();
        assertThat(saved()).isEqualTo(previous);

        when(provider.loadStandings(SEASON)).thenThrow(new IllegalStateException("upstream unavailable"));
        assertThatThrownBy(sync::syncStandings).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(SEASON));
        assertThat(saved()).isEqualTo(previous);
    }

    @Test
    void doesNotOverwriteAManualCorrectionMadeDuringTheFetch() {
        predictions.configure(SEASON, V2027);
        syncRound(5, ORDER, NOW);
        var manual = List.of("Piastri", "Norris", "Verstappen");
        when(provider.loadStandings(SEASON)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            standings.saveStandings(SEASON, manual);
            return Optional.of(new Snapshot(6, ORDER.reversed()));
        });
        service(Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC)).syncStandings();
        assertThat(saved().orderedDrivers()).containsExactlyElementsOf(manual);
        assertThat(saved().lastSyncedRound()).isEqualTo(5);

        syncRound(6, ORDER.reversed(), NOW.plusSeconds(2));
        assertThat(saved()).isEqualTo(new ChampionshipDriverStandings(SEASON, ORDER.reversed(), 6, NOW.plusSeconds(2)));
    }

    private void saveFinishedRace(int round, RaceType type) {
        transactions.executeWithoutResult(status -> races.create(new Race(
                new RaceId(UUID.randomUUID()), SEASON, round, type, "Championship sync race", RaceStatus.FINISHED, NOW,
                NOW.plusSeconds(3600))));
    }

    private void syncRound(int round, List<String> order, Instant now) {
        doReturn(Optional.of(new Snapshot(round, order))).when(provider).loadStandings(SEASON);
        service(Clock.fixed(now, ZoneOffset.UTC)).syncStandings();
    }

    private ChampionshipStandingsSyncService service(Clock clock) {
        return new ChampionshipStandingsSyncService(seasons, provider, races, lock, clock, transactionManager);
    }

    private ChampionshipDriverStandings saved() {
        return seasons.findDriverStandings(SEASON).orElseThrow();
    }
}
