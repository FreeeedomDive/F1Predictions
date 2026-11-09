package com.xdd.f1.domain.prediction;

import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.lineup.Driver;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class PredictionValidator {
    private PredictionValidator() {
    }

    public static void validate(int season, Prediction prediction) {
        Objects.requireNonNull(prediction, "Prediction must not be null");
        switch (season) {
            case 2023 -> validate2023(prediction);
            case 2024, 2025 -> validate2024And2025(season, prediction);
            case 2026, 2027 -> validate2026And2027(season, prediction);
            default -> throw unsupportedSeason(season);
        }
    }

    public static Optional<PredictionConditions> createConditions(int season, RaceId raceId, Supplier<Driver> chooseDriver) {
        return switch (season) {
            case 2023, 2024, 2025 -> Optional.empty();
            case 2026, 2027 -> Optional.of(new PredictionConditions(raceId, chooseDriver.get()));
            default -> throw unsupportedSeason(season);
        };
    }

    private static void validate2023(Prediction prediction) {
        var values = prediction.values();
        requireAbsent(values.safetyCarsPrediction(), "Safety cars prediction", 2023);
        requireAbsent(values.firstPlaceLeadPrediction(), "First place lead prediction", 2023);
        requireAbsent(values.driverPositionPrediction(), "Driver position prediction", 2023);
        requireAbsent(prediction.teamsPickedDrivers(), "Team predictions", 2023);
        var dnf = values.dnfPrediction();
        if (dnf != null && dnf.dnfPickedDrivers().size() != 1) {
            throw new DomainValidationException("DNF prediction in 2023 must select exactly one first DNF driver");
        }
    }

    private static void validate2024And2025(int season, Prediction prediction) {
        validateFiveDnfOrNobody(prediction.values().dnfPrediction());
        requireAbsent(prediction.values().driverPositionPrediction(), "Driver position prediction", season);
        var teams = prediction.teamsPickedDrivers();
        if (teams != null && (teams.size() != 10 || teams.stream().distinct().count() != 10
                || teams.stream().anyMatch(String::isBlank))) {
            throw new DomainValidationException("Team predictions in 2024–2025 must select ten different drivers");
        }
    }

    private static void validate2026And2027(int season, Prediction prediction) {
        validateFiveDnfOrNobody(prediction.values().dnfPrediction());
        requireAbsent(prediction.teamsPickedDrivers(), "Team predictions", season);
    }

    private static void validateFiveDnfOrNobody(DnfPrediction dnf) {
        if (dnf != null && !dnf.dnfPickedDrivers().isEmpty() && dnf.dnfPickedDrivers().size() != 5) {
            throw new DomainValidationException("DNF prediction must select five different drivers or no DNF");
        }
    }

    private static void requireAbsent(Object value, String category, int season) {
        if (value != null) {
            throw new DomainValidationException(category + " is not available in season " + season);
        }
    }

    private static DomainValidationException unsupportedSeason(int season) {
        return new DomainValidationException("Prediction rules are not defined for season " + season);
    }
}
