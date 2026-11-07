package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.contract.race.RaceDto;
import com.xdd.f1.contract.race.RaceStatusDto;
import com.xdd.f1.contract.race.RaceTypeDto;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.RaceType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leagues/{leagueId}/races")
public class RaceController {

    private final RaceService raceService;

    public RaceController(RaceService raceService) {
        this.raceService = raceService;
    }

    @GetMapping()
    public List<RaceDto> getRaces(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID leagueId,
            @RequestParam Optional<Integer> season) {
        var races = raceService.findSeasonCalendarForLeague(user.id(), new LeagueId(leagueId), season);
        return races.stream().map(this::toDto).toList();
    }

    private RaceDto toDto(Race race) {
        return new RaceDto(
                race.id().value(),
                race.name(),
                toDto(race.type()),
                toDto(race.status()),
                race.openAt(),
                race.closeAt()
        );
    }

    private RaceTypeDto toDto(RaceType type) {
        return RaceTypeDto.valueOf(String.valueOf(type));
    }

    private RaceStatusDto toDto(RaceStatus status) {
        return RaceStatusDto.valueOf(String.valueOf(status));
    }
}
