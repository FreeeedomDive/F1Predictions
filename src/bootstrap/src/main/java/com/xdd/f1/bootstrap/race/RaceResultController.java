package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.result.RaceResultService;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.contract.race.result.RaceResultDetailsDto;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.race.RaceId;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leagues/{leagueId}/races/{raceId}/result")
public class RaceResultController {
    private final RaceResultService service;

    public RaceResultController(RaceResultService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<RaceResultDetailsDto> getOwnResult(@CurrentUser AuthenticatedUser user,
                                                           @PathVariable UUID leagueId, @PathVariable UUID raceId) {
        return service.findOwnResult(user.id(), new LeagueId(leagueId), new RaceId(raceId))
                .map(details -> ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                        .body(RaceResultDtoMapper.toDto(details)))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore().cachePrivate()).build());
    }
}
