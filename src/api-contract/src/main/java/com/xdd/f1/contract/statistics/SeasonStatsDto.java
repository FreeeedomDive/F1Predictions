package com.xdd.f1.contract.statistics;

import java.util.List;

public record SeasonStatsDto(
        List<SeasonDriverStatsDto> tenthPlacePointsRating,
        List<SeasonDriverStatsDto> mostPickedForTenthPlace,
        List<SeasonDriverStatsDto> tenthPickedButDnfed,
        List<SeasonDriverStatsDto> driverOwnTenthPlacePoints,
        List<SeasonDriverStatsDto> tenthPlacePredictionEfficiency,
        List<SeasonDriverStatsDto> mostDnfDrivers,
        List<SeasonDriverStatsDto> mostPickedForDnf,
        List<SeasonParticipantStatsDto> tenthPlaceDnfAntiRating,
        List<SeasonLeadGapPredictionStatsDto> closestLeadGapPredictions,
        List<SeasonSafetyCarStatsDto> safetyCarPickCounts,
        List<SeasonSafetyCarStatsDto> safetyCarActualCounts,
        List<SeasonSafetyCarStatsDto> safetyCarCorrectGuesses
) {
}
