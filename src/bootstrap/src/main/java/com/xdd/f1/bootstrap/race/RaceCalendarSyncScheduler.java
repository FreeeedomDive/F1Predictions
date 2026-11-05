package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.RaceService;
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

    private final RaceService raceService;
    private final RaceCalendarSyncProperties properties;

    public RaceCalendarSyncScheduler(RaceService raceService, RaceCalendarSyncProperties properties) {
        this.raceService = raceService;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${races.calendar-sync.interval}"
    )
    public void syncCalendar() {
        raceService.syncCalendar(properties.season());
    }
}
