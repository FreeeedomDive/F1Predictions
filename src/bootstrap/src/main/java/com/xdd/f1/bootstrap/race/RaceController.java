package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.application.prediction.PredictionService;
import com.xdd.f1.application.prediction.RaceDetails;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.contract.common.ListResponseDto;
import com.xdd.f1.contract.race.RaceDto;
import com.xdd.f1.contract.race.RaceDetailsDto;
import com.xdd.f1.contract.prediction.PredictionConditionsDto;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.prediction.PredictionConditions;
import com.xdd.f1.domain.race.RaceId;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leagues/{leagueId}/races")
public class RaceController {

    private final RaceService raceService;
    private final PredictionService predictions;

    public RaceController(RaceService raceService, PredictionService predictions) {
        this.raceService = raceService;
        this.predictions = predictions;
    }

    @GetMapping()
    public ListResponseDto<RaceDto> getRaces(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID leagueId,
            @RequestParam Optional<Integer> season) {
        var races = raceService.findSeasonCalendarForLeague(user.id(), new LeagueId(leagueId), season);
        return new ListResponseDto<>(races.stream().map(RaceDtoMapper::toDto).toList());
    }

    @GetMapping("/{raceId}")
    public ResponseEntity<RaceDetailsDto> getRace(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID leagueId,
            @PathVariable UUID raceId
    ) {
        var details = predictions.getDetails(user.id(), new LeagueId(leagueId), new RaceId(raceId));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(toDto(details));
    }

    @GetMapping("/current")
    public ResponseEntity<RaceDetailsDto> getCurrentRace(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID leagueId,
            @RequestParam Optional<Integer> season
    ) {
        return predictions.findCurrentDetails(user.id(), new LeagueId(leagueId), season)
                .map(details -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore().cachePrivate())
                        .body(toDto(details)))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore().cachePrivate()).build());
    }

    private RaceDetailsDto toDto(RaceDetails details) {
        var conditions = toDto(details.conditions());
        var teams = details.teams().stream().map(RaceLineupDtoMapper::toDto).toList();
        return new RaceDetailsDto(
                RaceDtoMapper.toDto(details.race()),
                details.race().season(),
                teams,
                details.lineupPrepared(),
                conditions,
                details.predictionsOpen(),
                details.serverTime()
        );
    }

    private PredictionConditionsDto toDto(PredictionConditions conditions) {
        if (conditions == null) {
            return null;
        }
        var driver = conditions.positionPredictionDriver();
        return new PredictionConditionsDto(driver == null ? null : driver.name());
    }
}
