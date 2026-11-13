package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.RaceStartingGridSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "races.starting-grid-sync", name = "enabled", havingValue = "true")
public class RaceStartingGridSyncScheduler {
    private final RaceStartingGridSyncService syncService;

    public RaceStartingGridSyncScheduler(RaceStartingGridSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(
            fixedDelayString = "${races.starting-grid-sync.interval}",
            scheduler = "raceCalendarTaskScheduler"
    )
    public void syncStartingGrids() {
        syncService.syncStartingGrids();
    }
}
