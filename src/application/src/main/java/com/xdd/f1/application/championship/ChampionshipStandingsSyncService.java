package com.xdd.f1.application.championship;

import com.xdd.f1.application.championship.ChampionshipStandingsProvider.Snapshot;
import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.domain.championship.ChampionshipDriverStandings;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
public class ChampionshipStandingsSyncService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChampionshipStandingsSyncService.class);

    private final ChampionshipSeasonRepository seasons;
    private final ChampionshipStandingsProvider provider;
    private final RaceRepository races;
    private final TransactionLock lock;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public ChampionshipStandingsSyncService(ChampionshipSeasonRepository seasons, ChampionshipStandingsProvider provider,
                                            RaceRepository races, TransactionLock lock, Clock clock,
                                            PlatformTransactionManager transactionManager) {
        this.seasons = seasons;
        this.provider = provider;
        this.races = races;
        this.lock = lock;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void syncStandings() {
        int season = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).getYear();
        if (seasons.findBySeason(season).isEmpty()) {
            LOGGER.debug("No championship configured for season {}", season);
            return;
        }
        var beforeFetch = seasons.findDriverStandings(season);
        try {
            var snapshot = provider.loadStandings(season);
            if (snapshot.isEmpty()) {
                LOGGER.debug("No championship driver standings available for season {}", season);
                return;
            }
            var result = snapshot.get();
            var outcome = transactions.execute(status -> apply(season, result, beforeFetch));
            if (outcome == Outcome.UPDATED) {
                LOGGER.info("Updated championship driver standings for season {} at round {}", season, result.round());
            } else {
                LOGGER.debug("Championship driver standings sync for season {} at round {}: {}", season, result.round(), outcome);
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not synchronize championship driver standings for season " + season, exception);
        }
    }

    private Outcome apply(int season, Snapshot snapshot, Optional<ChampionshipDriverStandings> beforeFetch) {
        lock.acquire("championship:" + season);
        if (seasons.findBySeason(season).isEmpty()) {
            return Outcome.SKIPPED;
        }
        var current = seasons.findDriverStandings(season);
        if (!current.equals(beforeFetch)) {
            return Outcome.STALE;
        }

        var lastSyncedRound = current.map(ChampionshipDriverStandings::lastSyncedRound).orElse(0);
        var lastFinishedRound = races.findFinishedBySeason(season).stream()
                .filter(race -> race.type() == RaceType.RACE)
                .mapToInt(Race::round).max().orElse(0);
        if (snapshot.round() < Math.max(lastSyncedRound, lastFinishedRound)) {
            return Outcome.STALE;
        }
        if (snapshot.round() == lastSyncedRound
                && current.filter(saved -> saved.orderedDrivers().equals(snapshot.orderedDrivers())).isPresent()) {
            return Outcome.UNCHANGED;
        }
        seasons.saveDriverStandings(new ChampionshipDriverStandings(
                season, snapshot.orderedDrivers(), snapshot.round(), clock.instant()));
        return Outcome.UPDATED;
    }

    private enum Outcome {
        UPDATED,
        UNCHANGED,
        STALE,
        SKIPPED
    }
}
