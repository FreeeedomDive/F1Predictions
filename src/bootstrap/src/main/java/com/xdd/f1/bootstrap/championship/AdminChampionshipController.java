package com.xdd.f1.bootstrap.championship;

import com.xdd.f1.application.championship.ChampionshipPredictionService;
import com.xdd.f1.application.championship.ChampionshipStandingsService;
import com.xdd.f1.bootstrap.auth.RequireRights;
import com.xdd.f1.contract.championship.ChampionshipPredictionStatusDto;
import com.xdd.f1.contract.championship.ChampionshipSeasonDto;
import com.xdd.f1.contract.championship.ChampionshipStageDto;
import com.xdd.f1.contract.championship.ConfigureChampionshipRequestDto;
import com.xdd.f1.contract.championship.OpenChampionshipStageRequestDto;
import com.xdd.f1.contract.championship.SaveDriverStandingsRequestDto;
import com.xdd.f1.contract.common.ListResponseDto;
import com.xdd.f1.domain.championship.ChampionshipRulesVersion;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.rights.Right;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequireRights(Right.EDIT_CHAMPIONSHIP)
@RequestMapping("/adminApi/v1/championship/{season}")
public class AdminChampionshipController {
    private final ChampionshipStandingsService standings;
    private final ChampionshipPredictionService predictions;

    public AdminChampionshipController(ChampionshipStandingsService standings, ChampionshipPredictionService predictions) {
        this.standings = standings;
        this.predictions = predictions;
    }

    @GetMapping
    public ChampionshipSeasonDto get(@PathVariable int season) {
        var state = standings.getSeasonState(season);
        return ChampionshipDtoMapper.toDto(season, state.configuration(), state.driverStandings());
    }

    @PutMapping("/configuration")
    public ResponseEntity<Void> configure(@PathVariable int season, @RequestBody ConfigureChampionshipRequestDto request) {
        predictions.configure(season, ChampionshipRulesVersion.fromValue(request.rulesVersion()));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/stages/{stage}/open")
    public ResponseEntity<Void> open(@PathVariable int season, @PathVariable ChampionshipStageDto stage,
                                     @RequestBody OpenChampionshipStageRequestDto request) {
        if (request.lineupRaceId() == null) {
            throw new DomainValidationException("Championship lineup race ID must not be null");
        }
        predictions.openStage(season, ChampionshipDtoMapper.toDomain(stage), new RaceId(request.lineupRaceId()));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/stages/{stage}/close")
    public ResponseEntity<Void> close(@PathVariable int season, @PathVariable ChampionshipStageDto stage) {
        predictions.closeStage(season, ChampionshipDtoMapper.toDomain(stage));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/standings")
    public ResponseEntity<Void> saveStandings(@PathVariable int season, @RequestBody SaveDriverStandingsRequestDto request) {
        standings.saveStandings(season, request.driverStandings());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/prediction-statuses")
    public ResponseEntity<ListResponseDto<ChampionshipPredictionStatusDto>> getPredictionStatuses(
            @PathVariable int season, @RequestParam UUID leagueId
    ) {
        var rows = predictions.findPredictionStatuses(new LeagueId(leagueId), season).stream()
                .map(status -> new ChampionshipPredictionStatusDto(status.participantId().value(),
                        status.displayName(), status.hasPreSeasonPrediction(),
                        ChampionshipDtoMapper.toDto(status.midSeasonPredictionSource())))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(new ListResponseDto<>(rows));
    }
}
