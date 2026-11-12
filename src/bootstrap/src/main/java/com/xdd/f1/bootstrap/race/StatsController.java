package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.statistics.SeasonStatsService;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.contract.statistics.SeasonStatsDto;
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
@RequestMapping("/api/v1/leagues/{leagueId}/stats")
public class StatsController {
    private final SeasonStatsService service;

    public StatsController(SeasonStatsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<SeasonStatsDto> getSeasonStats(@CurrentUser AuthenticatedUser user,
                                                         @PathVariable UUID leagueId, @RequestParam int season) {
        var stats = service.getSeasonStats(user.id(), new LeagueId(leagueId), season);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(SeasonStatsDtoMapper.toDto(stats));
    }
}
