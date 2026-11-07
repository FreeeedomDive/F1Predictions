package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.domain.race.RaceId;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/adminApi/v1/races")
public class AdminRaceController {

    private final RaceService raceService;

    public AdminRaceController(RaceService raceService) {
        this.raceService = raceService;
    }

    @PatchMapping("{raceId}/open")
    public void openRace(@CurrentUser AuthenticatedUser user, @PathVariable UUID raceId) {
        raceService.openRacePredictions(new RaceId(raceId));
    }
}
