package com.xdd.f1.contract.race.lineup;

import com.xdd.f1.contract.race.DriverReplacementDto;

import java.util.List;

public record SaveRaceLineupRequestDto(
        List<RaceLineupTeamDto> baseTeams, List<TeamEdit> teams, List<DriverReplacementDto> replacements
) {
    public record TeamEdit(String previousName, RaceLineupTeamDto team) {
    }
}
