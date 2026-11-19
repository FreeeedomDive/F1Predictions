package com.xdd.f1.bootstrap.championship;

import com.xdd.f1.application.championship.ChampionshipPredictionService;
import com.xdd.f1.application.championship.ChampionshipStandingsService;
import com.xdd.f1.application.league.LeagueAccessGuard;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.contract.championship.ChampionshipDetailsDto;
import com.xdd.f1.contract.championship.ChampionshipPredictionDto;
import com.xdd.f1.contract.championship.ChampionshipStageDto;
import com.xdd.f1.contract.championship.SaveDriverStandingsRequestDto;
import com.xdd.f1.domain.league.LeagueId;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leagues/{leagueId}/championship/{season}")
public class ChampionshipController {
    private final LeagueAccessGuard access;
    private final ChampionshipStandingsService standings;
    private final ChampionshipPredictionService predictions;

    public ChampionshipController(LeagueAccessGuard access, ChampionshipStandingsService standings,
                                  ChampionshipPredictionService predictions) {
        this.access = access;
        this.standings = standings;
        this.predictions = predictions;
    }

    @GetMapping
    public ResponseEntity<ChampionshipDetailsDto> get(@CurrentUser AuthenticatedUser user, @PathVariable UUID leagueId,
                                                      @PathVariable int season) {
        var participant = access.checkAccess(user.id(), new LeagueId(leagueId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(ChampionshipDtoMapper.toDto(standings.getDetails(participant.leagueId(), season, participant.id())));
    }

    @PutMapping("/predictions/{stage}")
    public ResponseEntity<ChampionshipPredictionDto> save(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID leagueId, @PathVariable int season,
            @PathVariable ChampionshipStageDto stage, @RequestBody SaveDriverStandingsRequestDto request
    ) {
        var participant = access.checkAccess(user.id(), new LeagueId(leagueId));
        var prediction = predictions.save(participant.leagueId(), season, participant.id(),
                ChampionshipDtoMapper.toDomain(stage), request.driverStandings());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(ChampionshipDtoMapper.toDto(prediction, List.of()));
    }
}
