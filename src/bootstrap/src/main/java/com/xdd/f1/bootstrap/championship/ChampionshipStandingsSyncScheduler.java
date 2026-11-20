package com.xdd.f1.bootstrap.championship;

import com.xdd.f1.application.championship.ChampionshipStandingsSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "championship.standings-sync", name = "enabled", havingValue = "true")
public class ChampionshipStandingsSyncScheduler {
    private final ChampionshipStandingsSyncService syncService;

    public ChampionshipStandingsSyncScheduler(ChampionshipStandingsSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(fixedDelayString = "${championship.standings-sync.interval}", scheduler = "championshipStandingsTaskScheduler")
    public void syncStandings() {
        syncService.syncStandings();
    }
}
