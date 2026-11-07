package com.xdd.f1.bootstrap.race;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("races.calendar-sync")
public record RaceCalendarSyncProperties(int season) {
}
