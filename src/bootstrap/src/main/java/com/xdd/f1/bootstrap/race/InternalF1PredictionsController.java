package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.prediction.PredictionService;
import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.application.race.lineup.RaceLineupService;
import com.xdd.f1.application.race.result.RaceResultService;
import com.xdd.f1.application.standings.SeasonStandingsService;
import com.xdd.f1.application.statistics.SeasonStatsService;
import com.xdd.f1.bootstrap.auth.InternalCurrentParticipant;
import com.xdd.f1.bootstrap.prediction.PredictionDtoMapper;
import com.xdd.f1.contract.common.ListResponseDto;
import com.xdd.f1.contract.prediction.PredictionDto;
import com.xdd.f1.contract.prediction.SavePredictionRequestDto;
import com.xdd.f1.contract.race.RaceDetailsDto;
import com.xdd.f1.contract.race.RaceDto;
import com.xdd.f1.contract.race.lineup.RaceLineupTeamDto;
import com.xdd.f1.contract.race.result.RaceResultDetailsDto;
import com.xdd.f1.contract.standings.SeasonStandingsDto;
import com.xdd.f1.contract.statistics.SeasonStatsDto;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/leagues/{leagueId}")
public class InternalF1PredictionsController {
    private final RaceService races;
    private final PredictionService predictions;
    private final RaceResultService results;
    private final SeasonStandingsService standings;
    private final SeasonStatsService stats;
    private final RaceLineupService lineups;
    private final Clock clock;

    public InternalF1PredictionsController(RaceService races, PredictionService predictions, RaceResultService results,
                                           SeasonStandingsService standings, SeasonStatsService stats,
                                           RaceLineupService lineups, Clock clock) {
        this.races = races;
        this.predictions = predictions;
        this.results = results;
        this.standings = standings;
        this.stats = stats;
        this.lineups = lineups;
        this.clock = clock;
    }

    @GetMapping("/races")
    public ResponseEntity<ListResponseDto<RaceDto>> getRaces(@PathVariable UUID leagueId,
            @InternalCurrentParticipant LeagueParticipant participant, @RequestParam Optional<Integer> season) {
        var calendar = races.findSeasonCalendarForLeague(participant.userId(), new LeagueId(leagueId), season);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ListResponseDto<>(calendar.stream().map(RaceDtoMapper::toDto).toList()));
    }

    @GetMapping("/races/{raceId}")
    public ResponseEntity<RaceDetailsDto> getRace(@PathVariable UUID leagueId, @PathVariable UUID raceId,
            @InternalCurrentParticipant LeagueParticipant participant) {
        var details = predictions.getDetails(participant.userId(), new LeagueId(leagueId), new RaceId(raceId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(RaceDtoMapper.toDto(details));
    }

    @GetMapping("/races/{raceId}/prediction")
    public ResponseEntity<PredictionDto> getOwnPrediction(@PathVariable UUID leagueId, @PathVariable UUID raceId,
            @InternalCurrentParticipant LeagueParticipant participant) {
        return predictions.findOwn(participant.userId(), new LeagueId(leagueId), new RaceId(raceId))
                .map(prediction -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .body(PredictionDtoMapper.toDto(prediction)))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }

    @PutMapping("/races/{raceId}/prediction")
    public ResponseEntity<PredictionDto> savePrediction(@PathVariable UUID leagueId, @PathVariable UUID raceId,
            @InternalCurrentParticipant LeagueParticipant participant, @RequestBody SavePredictionRequestDto request) {
        var prediction = predictions.save(participant.userId(), new LeagueId(leagueId), new RaceId(raceId),
                PredictionDtoMapper.toDomain(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PredictionDtoMapper.toDto(prediction));
    }

    @GetMapping("/races/{raceId}/result")
    public ResponseEntity<RaceResultDetailsDto> getOwnResult(@PathVariable UUID leagueId, @PathVariable UUID raceId,
            @InternalCurrentParticipant LeagueParticipant participant) {
        return results.findOwnResult(participant.userId(), new LeagueId(leagueId), new RaceId(raceId))
                .map(result -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .body(RaceResultDtoMapper.toDto(result)))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }

    @GetMapping("/standings")
    public ResponseEntity<SeasonStandingsDto> getStandings(@PathVariable UUID leagueId, @RequestParam int season) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(SeasonStandingsDtoMapper.toDto(standings.getStandings(new LeagueId(leagueId), season)));
    }

    @GetMapping("/stats")
    public ResponseEntity<SeasonStatsDto> getStats(@PathVariable UUID leagueId,
            @InternalCurrentParticipant LeagueParticipant participant, @RequestParam int season) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(SeasonStatsDtoMapper.toDto(stats.getSeasonStats(participant.userId(), new LeagueId(leagueId), season)));
    }

    @GetMapping("/teams")
    public ResponseEntity<ListResponseDto<RaceLineupTeamDto>> getTeams(@PathVariable UUID leagueId,
            @InternalCurrentParticipant LeagueParticipant participant, @RequestParam Optional<Integer> season) {
        var calendar = races.findSeasonCalendarForLeague(participant.userId(), new LeagueId(leagueId), season);
        var chronological = Comparator.comparing(Race::closeAt).thenComparing(race -> race.id().value());
        var target = calendar.stream().filter(race -> race.closeAt().isAfter(clock.instant())).min(chronological)
                .or(() -> calendar.stream().max(chronological));
        var teams = target.map(race -> lineups.getForEditing(race.id()).teams().stream()
                .map(RaceLineupDtoMapper::toDto).toList()).orElse(List.of());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ListResponseDto<>(teams));
    }
}
