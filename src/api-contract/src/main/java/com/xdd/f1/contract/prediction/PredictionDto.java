package com.xdd.f1.contract.prediction;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PredictionDto(UUID id, UUID leagueId, UUID participantId, UUID raceId,
                            String tenthPlacePickedDriver, List<String> dnfPickedDrivers,
                            SafetyCarsCountDto safetyCarsPrediction, Integer driverPositionPrediction,
                            BigDecimal firstPlaceLeadPrediction, List<String> teamsPickedDrivers) {
}
