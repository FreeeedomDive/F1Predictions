package com.xdd.f1.contract.standings;

import com.xdd.f1.contract.race.RaceDto;

import java.util.List;
import java.util.UUID;

public record SeasonStandingsDto(
        int season,
        List<RaceDto> events,
        List<SeasonStandingsRowDto> rows,
        UUID currentLeaderParticipantId,
        int currentLeaderPoints,
        int pointsLeft,
        List<SeasonParticipantChartDto> charts,
        List<Integer> championChart
) {
}
