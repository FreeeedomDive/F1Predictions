package com.xdd.f1.infrastructure.persistence.race.lineup;

import java.util.List;

public record RaceLineupTeamData(
        String name,
        String badge,
        String backgroundColor,
        String textColor,
        List<String> drivers
) {
}
