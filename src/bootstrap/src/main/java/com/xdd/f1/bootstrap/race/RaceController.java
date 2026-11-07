package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.contract.common.ListResponseDto;
import com.xdd.f1.contract.race.RaceDto;
import com.xdd.f1.domain.league.LeagueId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public ListResponseDto<RaceDto> getRaces(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID leagueId,
            @RequestParam Optional<Integer> season) {
        var races = raceService.findSeasonCalendarForLeague(user.id(), new LeagueId(leagueId), season);
        return new ListResponseDto<>(races.stream().map(RaceDtoMapper::toDto).toList());
    }
}
