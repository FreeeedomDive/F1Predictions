package com.xdd.f1.contract.race.lineup;

import java.util.List;

public record RaceLineupTeamDto(
        String name,
        String badge,
        String backgroundColor,
        String textColor,
        List<String> drivers
) {
}
