package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.lineup.RaceLineupEditor;
import com.xdd.f1.contract.race.lineup.RaceLineupDto;
import com.xdd.f1.contract.race.lineup.RaceLineupTeamDto;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.Team;
import com.xdd.f1.domain.race.lineup.TeamBranding;

final class RaceLineupDtoMapper {

    private RaceLineupDtoMapper() {
    }

    static RaceLineupDto toDto(RaceLineupEditor editor) {
        return new RaceLineupDto(
                RaceDtoMapper.toDto(editor.race()),
                editor.teams().stream().map(RaceLineupDtoMapper::toDto).toList(),
                editor.prepared()
        );
    }

    private static Driver toDriver(String name) {
        if (name == null) {
            throw new DomainValidationException("Driver name is required");
        }
        return new Driver(name);
    }

    private static RaceLineupTeamDto toDto(Team team) {
        return new RaceLineupTeamDto(
                team.branding().name(),
                team.branding().badge(),
                team.branding().backgroundColor(),
                team.branding().textColor(),
                team.drivers().stream().map(Driver::name).toList()
        );
    }

    static Team toDomain(RaceLineupTeamDto team) {
        if (team == null || team.name() == null || team.badge() == null
                || team.backgroundColor() == null || team.textColor() == null || team.drivers() == null) {
            throw new DomainValidationException("Team name, badge, colors and drivers are required");
        }
        return new Team(
                new TeamBranding(team.name(), team.badge(), team.backgroundColor(), team.textColor()),
                team.drivers().stream().map(RaceLineupDtoMapper::toDriver).toList()
        );
    }
}
