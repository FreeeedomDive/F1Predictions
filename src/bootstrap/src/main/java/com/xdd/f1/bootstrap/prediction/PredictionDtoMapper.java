package com.xdd.f1.bootstrap.prediction;

import com.xdd.f1.contract.prediction.*;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.prediction.*;
import com.xdd.f1.domain.race.lineup.Driver;

public final class PredictionDtoMapper {
    private PredictionDtoMapper() {
    }

    public static PredictionDto toDto(Prediction prediction) {
        var values = prediction.values();
        var dnf = values.dnfPrediction();
        return new PredictionDto(prediction.id().value(), prediction.leagueId().value(), prediction.participantId().value(),
                prediction.raceId().value(), values.tenthPlacePickedDriver() == null ? null : values.tenthPlacePickedDriver().name(),
                dnf == null ? null : dnf.dnfPickedDrivers().stream().map(Driver::name).toList(),
                values.safetyCarsPrediction() == null ? null : SafetyCarsCountDto.valueOf(values.safetyCarsPrediction().name()),
                values.driverPositionPrediction(), values.firstPlaceLeadPrediction(), prediction.teamsPickedDrivers());
    }

    public static PredictionValues toDomain(SavePredictionRequestDto request) {
        if (request == null) {
            throw new DomainValidationException("Prediction is required");
        }
        var dnf = request.dnfPickedDrivers();
        return new PredictionValues(request.tenthPlacePickedDriver() == null ? null : toDriver(request.tenthPlacePickedDriver()),
                dnf == null ? null : new DnfPrediction(dnf.stream()
                        .map(PredictionDtoMapper::toDriver).toList()),
                request.safetyCarsPrediction() == null ? null : SafetyCarsCount.valueOf(request.safetyCarsPrediction().name()),
                request.driverPositionPrediction(), request.firstPlaceLeadPrediction());
    }

    private static Driver toDriver(String name) {
        if (name == null) {
            throw new DomainValidationException("Selected driver must not be null");
        }
        return new Driver(name);
    }
}
