package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.lineup.RaceLineupService;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.contract.race.lineup.RaceLineupDto;
import com.xdd.f1.contract.race.lineup.RaceLineupTeamDto;
import com.xdd.f1.contract.race.lineup.SwapRaceDriversRequestDto;
import com.xdd.f1.domain.race.RaceId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/adminApi/v1")
public class AdminRaceLineupController {

    private final RaceLineupService lineupService;

    public AdminRaceLineupController(RaceLineupService lineupService) {
        this.lineupService = lineupService;
    }

    @GetMapping("raceLineups/target")
    public ResponseEntity<RaceLineupDto> getTarget(@CurrentUser AuthenticatedUser user, @RequestParam int season) {
        return lineupService.findTarget(season)
                .map(RaceLineupDtoMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("races/{raceId}/lineup/teams")
    public void addTeam(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID raceId,
            @RequestBody RaceLineupTeamDto request
    ) {
        lineupService.addTeam(new RaceId(raceId), RaceLineupDtoMapper.toDomain(request));
    }

    @PutMapping("races/{raceId}/lineup/teams/{teamName}")
    public void updateTeam(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID raceId,
            @PathVariable String teamName,
            @RequestBody RaceLineupTeamDto request
    ) {
        lineupService.updateTeam(
                new RaceId(raceId),
                teamName,
                RaceLineupDtoMapper.toDomain(request)
        );
    }

    @PostMapping("races/{raceId}/lineup/swapDrivers")
    public RaceLineupDto swapDrivers(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID raceId,
            @RequestBody SwapRaceDriversRequestDto request
    ) {
        var id = new RaceId(raceId);
        lineupService.swapDrivers(
                id,
                RaceLineupDtoMapper.toDriver(request.firstDriverName()),
                RaceLineupDtoMapper.toDriver(request.secondDriverName())
        );
        return RaceLineupDtoMapper.toDto(lineupService.getForEditing(id));
    }
}
