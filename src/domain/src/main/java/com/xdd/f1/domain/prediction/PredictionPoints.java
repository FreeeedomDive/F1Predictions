package com.xdd.f1.domain.prediction;

import com.xdd.f1.domain.error.DomainValidationException;

import java.util.Objects;

public record PredictionPoints(
        PredictionId predictionId,
        RulesVersion rulesVersion,
        int totalPoints,
        Integer tenthPlacePoints,
        Integer dnfsPoints,
        Integer safetyCarsPoints,
        Integer firstPlaceLeadPoints,
        Integer driverPositionPoints,
        Integer teamMatesPoints
) {
    public PredictionPoints {
        Objects.requireNonNull(predictionId, "Prediction ID must not be null");
        Objects.requireNonNull(rulesVersion, "Rules version must not be null");
        requireNonNegative(totalPoints);
        requireNonNegative(tenthPlacePoints);
        requireNonNegative(dnfsPoints);
        requireNonNegative(safetyCarsPoints);
        requireNonNegative(firstPlaceLeadPoints);
        requireNonNegative(driverPositionPoints);
        requireNonNegative(teamMatesPoints);
    }

    private static void requireNonNegative(Integer points) {
        if (points != null && points < 0) {
            throw new DomainValidationException("Prediction points must not be negative");
        }
    }
}
