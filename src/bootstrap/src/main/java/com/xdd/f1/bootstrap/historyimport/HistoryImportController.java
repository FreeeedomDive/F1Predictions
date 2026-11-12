package com.xdd.f1.bootstrap.historyimport;

import com.xdd.f1.application.historyimport.HistoryImportService;
import com.xdd.f1.contract.historyimport.*;
import com.xdd.f1.contract.league.LeagueSettingsDto;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueSettings;
import com.xdd.f1.domain.league.LeagueSource;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.xdd.f1.bootstrap.historyimport.ImportDtoMapper.*;

@RestController
@RequestMapping("/importApi/v1")
public class HistoryImportController {
    private final HistoryImportService service;

    public HistoryImportController(HistoryImportService service) {
        this.service = service;
    }

    @GetMapping("/leagues/{leagueId}")
    public ImportLeagueDto league(@PathVariable UUID leagueId) {
        var league = service.getLeague(new LeagueId(leagueId));
        var discordId = league.source() instanceof LeagueSource.Discord discord ? String.valueOf(discord.guildId()) : null;
        return new ImportLeagueDto(league.id().value(), league.name(), discordId,
                new LeagueSettingsDto(league.settings().notificationsChannelId(), league.settings().sprintsEnabled(), league.settings().firstSeason()));
    }

    @PostMapping("/leagueSettings")
    public ImportReportDto leagueSettings(@RequestBody ImportRequestDto.LeagueSettings request) {
        var settings = required(request.settings(), "settings");
        return report(service.importLeagueSettings(new LeagueId(required(request.leagueId(), "leagueId")),
                new LeagueSettings(settings.notificationsChannelId(), settings.sprintsEnabled(), settings.firstSeason())));
    }

    @PostMapping("/users")
    public ImportReportDto users(@RequestBody ImportRequestDto<ImportRequestDto.User> request) {
        return report(service.importUsers(leagueId(request), list(request.data(), "data", ImportDtoMapper::user)));
    }

    @PostMapping("/races")
    public ImportReportDto races(@RequestBody ImportRequestDto<ImportRequestDto.Race> request) {
        return report(service.importRaces(leagueId(request), list(request.data(), "data", ImportDtoMapper::race)));
    }

    @PostMapping("/raceLineups")
    public ImportReportDto lineups(@RequestBody ImportRequestDto<ImportRequestDto.RaceLineup> request) {
        return report(service.importLineups(leagueId(request), list(request.data(), "data", ImportDtoMapper::lineup)));
    }

    @PostMapping("/predictions")
    public ImportReportDto predictions(@RequestBody ImportRequestDto<ImportRequestDto.Prediction> request) {
        return report(service.importPredictions(leagueId(request), list(request.data(), "data", ImportDtoMapper::prediction)));
    }

    @PostMapping("/results")
    public ImportReportDto results(@RequestBody ImportRequestDto<ImportRequestDto.Result> request) {
        return report(service.importResults(leagueId(request), list(request.data(), "data", ImportDtoMapper::result)));
    }

    @PostMapping("/seasonPodiums")
    public ImportReportDto podiums(@RequestBody ImportRequestDto<ImportRequestDto.SeasonPodium> request) {
        return report(service.importPodiums(leagueId(request), list(request.data(), "data", ImportDtoMapper::podium)));
    }

    private LeagueId leagueId(ImportRequestDto<?> request) {
        return new LeagueId(required(request.leagueId(), "leagueId"));
    }
}
