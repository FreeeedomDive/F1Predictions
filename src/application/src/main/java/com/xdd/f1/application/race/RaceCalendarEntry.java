package com.xdd.f1.application.race;

import com.xdd.f1.domain.race.RaceType;

import java.time.Instant;

public record RaceCalendarEntry(
        int season,
        int round,
        RaceType type,
        String name,
        Instant openAt,
        Instant closeAt
) {
}
