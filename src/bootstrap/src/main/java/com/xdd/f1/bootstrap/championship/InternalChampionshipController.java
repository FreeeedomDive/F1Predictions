package com.xdd.f1.bootstrap.championship;

import com.xdd.f1.application.championship.ChampionshipPredictionService;
import com.xdd.f1.application.championship.ChampionshipStandingsService;
import com.xdd.f1.bootstrap.auth.InternalCurrentParticipant;
import com.xdd.f1.contract.championship.ChampionshipDetailsDto;
import com.xdd.f1.contract.championship.ChampionshipPredictionDto;
import com.xdd.f1.contract.championship.ChampionshipStageDto;
import com.xdd.f1.contract.championship.SaveDriverStandingsRequestDto;
import com.xdd.f1.domain.league.LeagueParticipant;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/leagues/{leagueId}/championship/{season}")
public class InternalChampionshipController {
    private final ChampionshipStandingsService standings;
    private final ChampionshipPredictionService predictions;

    public InternalChampionshipController(ChampionshipStandingsService standings, ChampionshipPredictionService predictions) {
        this.standings = standings;
        this.predictions = predictions;
    }

    @GetMapping
    public ResponseEntity<ChampionshipDetailsDto> get(@InternalCurrentParticipant LeagueParticipant participant,
                                                      @PathVariable int season) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ChampionshipDtoMapper.toDto(standings.getDetails(participant.leagueId(), season, participant.id())));
    }

    @PutMapping("/predictions/{stage}")
    public ResponseEntity<ChampionshipPredictionDto> save(
            @InternalCurrentParticipant LeagueParticipant participant, @PathVariable int season,
            @PathVariable ChampionshipStageDto stage, @RequestBody SaveDriverStandingsRequestDto request
    ) {
        var prediction = predictions.save(participant.leagueId(), season, participant.id(),
                ChampionshipDtoMapper.toDomain(stage), request.driverStandings());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ChampionshipDtoMapper.toDto(prediction, List.of()));
    }
}
