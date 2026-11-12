package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.statistics.*;
import com.xdd.f1.contract.prediction.SafetyCarsCountDto;
import com.xdd.f1.contract.statistics.*;

import java.util.List;

final class SeasonStatsDtoMapper {
    private SeasonStatsDtoMapper() {
    }

    static SeasonStatsDto toDto(SeasonStats stats) {
        return new SeasonStatsDto(
                drivers(stats.tenthPlacePointsRating()),
                drivers(stats.mostPickedForTenthPlace()),
                drivers(stats.tenthPickedButDnfed()),
                drivers(stats.driverOwnTenthPlacePoints()),
                drivers(stats.tenthPlacePredictionEfficiency()),
                drivers(stats.mostDnfDrivers()),
                drivers(stats.mostPickedForDnf()),
                stats.tenthPlaceDnfAntiRating().stream()
                        .map(entry -> new SeasonParticipantStatsDto(
                                entry.participantId().value(), entry.participantName(), entry.score()
                        )).toList(),
                stats.closestLeadGapPredictions().stream()
                        .map(entry -> new SeasonLeadGapPredictionStatsDto(
                                entry.participantId().value(), entry.participantName(), entry.raceName(),
                                entry.predictedGap(), entry.actualGap(), entry.difference()
                        )).toList(),
                safetyCars(stats.safetyCarPickCounts()),
                safetyCars(stats.safetyCarActualCounts()),
                safetyCars(stats.safetyCarCorrectGuesses())
        );
    }

    private static List<SeasonDriverStatsDto> drivers(List<SeasonDriverStats> stats) {
        return stats.stream().map(entry -> new SeasonDriverStatsDto(entry.driver(), entry.score())).toList();
    }

    private static List<SeasonSafetyCarStatsDto> safetyCars(List<SeasonSafetyCarStats> stats) {
        return stats.stream()
                .map(entry -> new SeasonSafetyCarStatsDto(SafetyCarsCountDto.valueOf(entry.safetyCars().name()), entry.score()))
                .toList();
    }
}
