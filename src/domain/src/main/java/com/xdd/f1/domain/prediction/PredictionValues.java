package com.xdd.f1.domain.prediction;

import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.race.lineup.Driver;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record PredictionValues(
        Driver tenthPlacePickedDriver,
        DnfPrediction dnfPrediction,
        SafetyCarsCount safetyCarsPrediction,
        Integer driverPositionPrediction,
        BigDecimal firstPlaceLeadPrediction
) {
    public PredictionValues {
        if (driverPositionPrediction != null && driverPositionPrediction < 1) {
            throw new DomainValidationException("Driver position prediction must be greater than zero");
        }
        if (firstPlaceLeadPrediction != null) {
            if (firstPlaceLeadPrediction.signum() < 0 || firstPlaceLeadPrediction.compareTo(new BigDecimal("999999.999")) > 0) {
                throw new DomainValidationException("First place lead prediction must be between 0 and 999999.999 seconds");
            }
            try {
                firstPlaceLeadPrediction = firstPlaceLeadPrediction.setScale(3, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException exception) {
                throw new DomainValidationException("First place lead prediction must have at most three decimal places");
            }
        }
    }
}
