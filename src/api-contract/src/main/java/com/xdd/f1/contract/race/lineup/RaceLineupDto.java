package com.xdd.f1.contract.race.lineup;

import com.xdd.f1.contract.race.RaceDto;

import java.util.List;

public record RaceLineupDto(RaceDto race, List<RaceLineupTeamDto> teams, boolean prepared) {
}
