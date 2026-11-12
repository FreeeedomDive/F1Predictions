package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.standings.SeasonStandingsService;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.contract.standings.SeasonStandingsDto;
import com.xdd.f1.domain.league.LeagueId;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leagues/{leagueId}/standings")
public class StandingsController {
    private final SeasonStandingsService service;

    public StandingsController(SeasonStandingsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<SeasonStandingsDto> getStandings(@CurrentUser AuthenticatedUser user,
                                                           @PathVariable UUID leagueId, @RequestParam int season) {
        var standings = service.getStandings(user.id(), new LeagueId(leagueId), season);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(SeasonStandingsDtoMapper.toDto(standings));
    }
}
