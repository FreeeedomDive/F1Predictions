package com.xdd.f1.application.statistics;

import java.util.List;

public record SeasonStats(
        List<SeasonDriverStats> tenthPlacePointsRating,
        List<SeasonDriverStats> mostPickedForTenthPlace,
        List<SeasonDriverStats> tenthPickedButDnfed,
        List<SeasonDriverStats> driverOwnTenthPlacePoints,
        List<SeasonDriverStats> tenthPlacePredictionEfficiency,
        List<SeasonDriverStats> mostDnfDrivers,
        List<SeasonDriverStats> mostPickedForDnf,
        List<SeasonParticipantStats> tenthPlaceDnfAntiRating,
        List<SeasonLeadGapPredictionStats> closestLeadGapPredictions,
        List<SeasonSafetyCarStats> safetyCarPickCounts,
        List<SeasonSafetyCarStats> safetyCarActualCounts,
        List<SeasonSafetyCarStats> safetyCarCorrectGuesses
) {
    public static SeasonStats empty() {
        return new SeasonStats(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }
}
