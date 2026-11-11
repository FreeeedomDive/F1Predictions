package com.xdd.f1.contract.race;

import com.xdd.f1.contract.race.result.RaceParticipantPointsDto;
import com.xdd.f1.contract.race.result.RaceResultValuesDto;

import java.util.List;
import java.util.UUID;

public record RaceFinishedEvent(
        UUID leagueId,
        UUID raceId,
        String raceName,
        String raceType,
        int season,
        String rulesVersion,
        RaceResultValuesDto result,
        List<RaceParticipantPointsDto> points
) {
}
