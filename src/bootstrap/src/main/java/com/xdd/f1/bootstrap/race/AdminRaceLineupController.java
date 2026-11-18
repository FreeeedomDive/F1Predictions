package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.lineup.RaceLineupService;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.bootstrap.auth.RequireRights;
import com.xdd.f1.contract.race.lineup.RaceLineupDto;
import com.xdd.f1.contract.race.lineup.SaveRaceLineupRequestDto;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.rights.Right;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequireRights(Right.EDIT_RACE_LINEUPS)
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

    @PutMapping("races/{raceId}/lineup")
    public RaceLineupDto save(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID raceId,
            @RequestBody SaveRaceLineupRequestDto request
    ) {
        RaceLineupDtoMapper.validate(request);
        return RaceLineupDtoMapper.toDto(lineupService.save(
                new RaceId(raceId),
                request.baseTeams().stream().map(RaceLineupDtoMapper::toDomain).toList(),
                request.teams().stream().map(RaceLineupDtoMapper::toEdit).toList(),
                request.replacements().stream().map(RaceLineupDtoMapper::toReplacement).toList()
        ));
    }
}
