package com.xdd.f1.bootstrap.prediction;

import com.xdd.f1.application.prediction.PredictionService;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.contract.prediction.PredictionDto;
import com.xdd.f1.contract.prediction.SavePredictionRequestDto;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.race.RaceId;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leagues/{leagueId}/races/{raceId}/prediction")
public class PredictionController {
    private final PredictionService service;

    public PredictionController(PredictionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PredictionDto> getOwn(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID leagueId,
            @PathVariable UUID raceId
    ) {
        var prediction = service.findOwn(user.id(), new LeagueId(leagueId), new RaceId(raceId));
        return prediction.map(value -> ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                        .body(PredictionDtoMapper.toDto(value)))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore().cachePrivate()).build());
    }

    @PutMapping
    public ResponseEntity<PredictionDto> save(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID leagueId,
            @PathVariable UUID raceId,
            @RequestBody SavePredictionRequestDto request
    ) {
        var prediction = service.save(user.id(), new LeagueId(leagueId), new RaceId(raceId), PredictionDtoMapper.toDomain(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(PredictionDtoMapper.toDto(prediction));
    }
}
