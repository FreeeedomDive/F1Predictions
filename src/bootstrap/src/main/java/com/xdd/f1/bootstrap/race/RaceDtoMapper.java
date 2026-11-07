package com.xdd.f1.bootstrap.race;

import com.xdd.f1.contract.race.RaceDto;
import com.xdd.f1.contract.race.RaceStatusDto;
import com.xdd.f1.contract.race.RaceTypeDto;
import com.xdd.f1.domain.race.Race;

final class RaceDtoMapper {

    private RaceDtoMapper() {
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
