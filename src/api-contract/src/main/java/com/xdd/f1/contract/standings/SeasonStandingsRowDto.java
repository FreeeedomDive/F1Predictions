package com.xdd.f1.contract.standings;

import java.util.List;
import java.util.UUID;

public record SeasonStandingsRowDto(
        UUID participantId,
        String participantName,
        int totalPoints,
        int maximumTenthPlaceCount,
        List<SeasonRacePointsDto> results,
        List<SeasonPodiumDto> previousPodiums
) {
}
