package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.RaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "races.transition-scheduler",
        name = "enabled",
        havingValue = "true"
)
public class RaceTransitionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RaceTransitionScheduler.class);

    private final RaceService raceService;

    public RaceTransitionScheduler(RaceService raceService) {
        this.raceService = raceService;
    }

    @Scheduled(
            fixedDelayString = "${races.transition-scheduler.interval}",
            initialDelayString = "${races.transition-scheduler.interval}"
    )
    public void scheduleRaceTransitions() {
        var result = raceService.processScheduledTransitions();
        LOGGER.info("Processed race transitions: {}", result);
    }
}
