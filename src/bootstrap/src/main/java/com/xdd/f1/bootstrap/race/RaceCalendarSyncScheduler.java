package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.RaceCalendarSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "races.calendar-sync",
        name = "enabled",
        havingValue = "true"
)
public class RaceCalendarSyncScheduler {

    private final RaceCalendarSyncService syncService;
    private final RaceCalendarSyncProperties properties;

    public RaceCalendarSyncScheduler(RaceCalendarSyncService syncService, RaceCalendarSyncProperties properties) {
        this.syncService = syncService;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${races.calendar-sync.interval}",
            scheduler = "raceCalendarTaskScheduler"
    )
    public void syncCalendar() {
        syncService.syncCalendar(properties.season());
    }
}
