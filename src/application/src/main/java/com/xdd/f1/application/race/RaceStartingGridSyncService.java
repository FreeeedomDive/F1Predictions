package com.xdd.f1.application.race;

import com.xdd.f1.application.race.result.RaceResultService;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.result.RaceResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class RaceStartingGridSyncService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RaceStartingGridSyncService.class);

    private final RaceRepository races;
    private final StartingGridProvider provider;
    private final RaceResultService results;
    private final Clock clock;
    private final Counter updated;
    private final Counter unchanged;
    private final Counter errors;

    public RaceStartingGridSyncService(RaceRepository races, StartingGridProvider provider,
                                       RaceResultService results, Clock clock, MeterRegistry registry) {
        this.races = races;
        this.provider = provider;
        this.results = results;
        this.clock = clock;
        this.updated = registry.counter("f1.race.starting.grid.sync", "outcome", "updated");
        this.unchanged = registry.counter("f1.race.starting.grid.sync", "outcome", "unchanged");
        this.errors = registry.counter("f1.race.starting.grid.sync", "outcome", "error");
    }

    public void syncStartingGrids() {
        for (var race : races.findRacesToSyncStartingGrid(clock.instant())) {
            try {
                var grid = provider.loadStartingGrid(race);
                if (results.applyStartingGrid(race, grid)) {
                    updated.increment();
                    LOGGER.info("Updated starting grid for race {}", race.id().value());
                } else {
                    unchanged.increment();
                }
            } catch (RuntimeException exception) {
                errors.increment();
                LOGGER.warn("Could not synchronize starting grid for race {}", race.id().value(), exception);
            }
        }
    }

    public RaceResult syncStartingGrid(RaceId raceId) {
        var race = races.findById(raceId).orElseThrow(() -> new RaceNotFoundException(raceId));
        var grid = provider.loadStartingGrid(race);
        return results.applyStartingGridManually(race, grid);
    }
}
