package com.xdd.f1.bootstrap.race;

import com.xdd.f1.contract.race.RaceDto;
import com.xdd.f1.application.prediction.RaceDetails;
import com.xdd.f1.contract.prediction.PredictionConditionsDto;
import com.xdd.f1.contract.race.RaceDetailsDto;
import com.xdd.f1.contract.race.RaceStatusDto;
import com.xdd.f1.contract.race.RaceTypeDto;
import com.xdd.f1.domain.race.Race;

final class RaceDtoMapper {

    private RaceDtoMapper() {
    }

    static RaceDetailsDto toDto(RaceDetails details) {
        var conditions = details.conditions();
        var driver = conditions == null ? null : conditions.positionPredictionDriver();
        return new RaceDetailsDto(
                toDto(details.race()),
                details.race().season(),
                details.teams().stream().map(RaceLineupDtoMapper::toDto).toList(),
                details.lineupPrepared(),
                conditions == null ? null : new PredictionConditionsDto(driver == null ? null : driver.name()),
                details.predictionsOpen(),
                details.serverTime(),
                details.startingGrid()
        );
    }

    static RaceDto toDto(Race race) {
        return new RaceDto(
                race.id().value(),
                race.name(),
                RaceTypeDto.valueOf(race.type().name()),
                RaceStatusDto.valueOf(race.status().name()),
                race.openAt(),
                race.closeAt()
        );
    }
}
