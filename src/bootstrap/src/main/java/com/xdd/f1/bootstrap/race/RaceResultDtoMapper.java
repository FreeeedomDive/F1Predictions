package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.result.RaceResultDetails;
import com.xdd.f1.application.race.result.RaceResultEditor;
import com.xdd.f1.contract.prediction.PredictionConditionsDto;
import com.xdd.f1.contract.prediction.PredictionPointsDto;
import com.xdd.f1.contract.race.result.*;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.prediction.PredictionConditions;
import com.xdd.f1.domain.prediction.PredictionPoints;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.result.*;

import java.util.List;

final class RaceResultDtoMapper {
    private RaceResultDtoMapper() {
    }

    static RaceResultEditorDto toDto(RaceResultEditor editor) {
        return new RaceResultEditorDto(RaceDtoMapper.toDto(editor.race()), editor.race().season(),
                editor.teams().stream().map(RaceLineupDtoMapper::toDto).toList(), editor.lineupPrepared(),
                toDto(editor.conditions()), editor.result() == null ? null : toDto(editor.result()));
    }

    static RaceResultDto toDto(RaceResult result) {
        return new RaceResultDto(result.raceId().value(), result.rulesVersion().value(), toDto(result.result()), toDto(result.draft()),
                result.startingGrid() == null ? List.of() : result.startingGrid().stream().map(Driver::name).toList());
    }

    static RaceResultDetailsDto toDto(RaceResultDetails details) {
        return new RaceResultDetailsDto(RaceDtoMapper.toDto(details.race()), details.race().season(), details.rulesVersion().value(),
                toDto(details.result()), toDto(details.points()));
    }

    static RaceResultValues toDomain(RaceResultValuesDto request) {
        if (request == null) {
            throw new DomainValidationException("Race result is required");
        }
        var classification = request.classification() == null ? null : request.classification().stream().map(entry -> {
            if (entry == null || entry.driverName() == null || entry.status() == null) {
                throw new DomainValidationException("Driver name and status are required for each classification entry");
            }
            return new RaceClassificationEntry(new Driver(entry.driverName()), RaceDriverStatus.valueOf(entry.status().name()));
        }).toList();
        return new RaceResultValues(classification, request.safetyCars(), request.firstPlaceLead());
    }

    private static RaceResultValuesDto toDto(RaceResultValues values) {
        if (values == null) {
            return null;
        }
        var classification = values.classification() == null ? null : values.classification().stream()
                .map(entry -> new RaceClassificationEntryDto(entry.driver().name(), RaceDriverStatusDto.valueOf(entry.status().name()))).toList();
        return new RaceResultValuesDto(classification, values.safetyCars(), values.firstPlaceLead());
    }

    private static PredictionConditionsDto toDto(PredictionConditions conditions) {
        if (conditions == null) {
            return null;
        }
        return new PredictionConditionsDto(conditions.positionPredictionDriver() == null ? null : conditions.positionPredictionDriver().name());
    }

    static PredictionPointsDto toDto(PredictionPoints points) {
        if (points == null) {
            return null;
        }
        return new PredictionPointsDto(points.rulesVersion().value(), points.totalPoints(), points.tenthPlacePoints(), points.dnfsPoints(),
                points.safetyCarsPoints(), points.firstPlaceLeadPoints(), points.teamMatesPoints(), points.driverPositionPoints());
    }
}
