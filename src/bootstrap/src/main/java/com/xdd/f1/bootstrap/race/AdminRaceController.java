package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.contract.common.ListResponseDto;
import com.xdd.f1.contract.race.RaceDto;
import com.xdd.f1.domain.race.RaceId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/adminApi/v1/races")
public class AdminRaceController {

    private final RaceService raceService;

    public AdminRaceController(RaceService raceService) {
        this.raceService = raceService;
    }

    @GetMapping
    public ListResponseDto<RaceDto> getRaces(@CurrentUser AuthenticatedUser user, @RequestParam int season) {
        var races = raceService.findAllBySeason(season).stream().map(RaceDtoMapper::toDto).toList();
        return new ListResponseDto<>(races);
    }

    @PatchMapping("{raceId}/open")
    public void openRace(@CurrentUser AuthenticatedUser user, @PathVariable UUID raceId) {
        raceService.openRacePredictions(new RaceId(raceId));
    }

    @PatchMapping("{raceId}/close")
    public void closeRace(@CurrentUser AuthenticatedUser user, @PathVariable UUID raceId) {
        raceService.closeRacePredictions(new RaceId(raceId));
    }
}
