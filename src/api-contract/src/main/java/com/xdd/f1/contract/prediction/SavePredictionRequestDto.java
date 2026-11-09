package com.xdd.f1.contract.prediction;

import java.math.BigDecimal;
import java.util.List;

public record SavePredictionRequestDto(String tenthPlacePickedDriver, List<String> dnfPickedDrivers,
                                       SafetyCarsCountDto safetyCarsPrediction, Integer driverPositionPrediction,
                                       BigDecimal firstPlaceLeadPrediction) {
}
