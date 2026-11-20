package com.xdd.f1.bootstrap.historyimport;

import com.xdd.f1.application.championship.ChampionshipImportResult;
import com.xdd.f1.application.championship.ChampionshipImportService;
import com.xdd.f1.domain.championship.ChampionshipPrediction;
import com.xdd.f1.domain.championship.ChampionshipPredictionSource;
import com.xdd.f1.domain.championship.ChampionshipRulesVersion;
import com.xdd.f1.domain.championship.ChampionshipSeason;
import com.xdd.f1.domain.championship.ChampionshipStage;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/importApi/v1/championship/{season}")
public class ChampionshipImportController {
    private final ChampionshipImportService imports;

    public ChampionshipImportController(ChampionshipImportService imports) {
        this.imports = imports;
    }

    @PostMapping("/validate")
    public ChampionshipImportResult validate(@PathVariable int season, @Valid @RequestBody ChampionshipImportRequest request) {
        return process(season, request, false);
    }

    @PostMapping
    public ChampionshipImportResult load(@PathVariable int season, @Valid @RequestBody ChampionshipImportRequest request) {
        return process(season, request, true);
    }

    private ChampionshipImportResult process(int season, ChampionshipImportRequest request, boolean write) {
        var leagueId = new LeagueId(request.leagueId());
        var configuration = new ChampionshipSeason(season, ChampionshipRulesVersion.fromValue(request.rulesVersion()),
                parseStage(request.currentStage()), true, request.predictionsOpen(),
                request.preSeasonRoster(), request.midSeasonRoster());
        var predictions = request.predictions().stream().map(prediction -> new ChampionshipPrediction(
                leagueId, season, new LeagueParticipantId(prediction.participantId()),
                parseStage(prediction.stage()), prediction.driverStandings(),
                ChampionshipPredictionSource.USER
        )).toList();
        return write
                ? imports.load(leagueId, configuration, request.driverStandings(), predictions)
                : imports.validate(leagueId, configuration, request.driverStandings(), predictions);
    }

    private static ChampionshipStage parseStage(String stage) {
        try {
            return ChampionshipStage.valueOf(stage);
        } catch (IllegalArgumentException exception) {
            throw new DomainValidationException("Unknown championship stage: " + stage);
        }
    }
}
