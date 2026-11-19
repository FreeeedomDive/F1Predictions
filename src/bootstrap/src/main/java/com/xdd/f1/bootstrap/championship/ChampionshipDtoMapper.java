package com.xdd.f1.bootstrap.championship;

import com.xdd.f1.application.championship.ChampionshipDetails;
import com.xdd.f1.contract.championship.ChampionshipDetailsDto;
import com.xdd.f1.contract.championship.ChampionshipPredictionDto;
import com.xdd.f1.contract.championship.ChampionshipPredictionSourceDto;
import com.xdd.f1.contract.championship.ChampionshipSeasonDto;
import com.xdd.f1.contract.championship.ChampionshipStageDto;
import com.xdd.f1.domain.championship.ChampionshipDriverStandings;
import com.xdd.f1.domain.championship.ChampionshipPrediction;
import com.xdd.f1.domain.championship.ChampionshipPredictionSource;
import com.xdd.f1.domain.championship.ChampionshipSeason;
import com.xdd.f1.domain.championship.ChampionshipStage;

import java.util.List;

final class ChampionshipDtoMapper {
    private ChampionshipDtoMapper() {
    }

    static ChampionshipDetailsDto toDto(ChampionshipDetails details) {
        var season = toDto(details.season(), details.configuration(), details.driverStandings());
        return new ChampionshipDetailsDto(season.season(), season.configured(), season.rulesVersion(),
                season.window(), season.driverStandings(), season.standingsUpdate(),
                toDto(details.preSeasonPrediction()), toDto(details.midSeasonPrediction()),
                details.rows().stream().map(row -> new ChampionshipDetailsDto.ParticipantPoints(
                        row.participantId().value(), row.displayName(), row.totalPoints())).toList());
    }

    static ChampionshipSeasonDto toDto(int season, ChampionshipSeason configuration, ChampionshipDriverStandings standings) {
        var window = configuration == null
                ? new ChampionshipSeasonDto.Window(null, false, false, List.of(), List.of())
                : new ChampionshipSeasonDto.Window(ChampionshipStageDto.valueOf(configuration.stage().name()),
                        configuration.stageStarted(), configuration.isOpen(),
                        configuration.preSeasonRoster(), configuration.midSeasonRoster());
        return new ChampionshipSeasonDto(season, configuration != null,
                configuration == null ? null : configuration.rulesVersion().value(), window,
                standings == null ? List.of() : standings.orderedDrivers(),
                standings == null ? null : new ChampionshipSeasonDto.StandingsUpdate(standings.lastSyncedRound(), standings.updatedAt()));
    }

    static ChampionshipPredictionDto toDto(ChampionshipPrediction prediction, List<Integer> points) {
        return new ChampionshipPredictionDto(ChampionshipStageDto.valueOf(prediction.stage().name()),
                prediction.orderedDrivers(), toDto(prediction.predictionSource()), points);
    }

    static ChampionshipPredictionSourceDto toDto(ChampionshipPredictionSource source) {
        return source == null ? null : ChampionshipPredictionSourceDto.valueOf(source.name());
    }

    static ChampionshipStage toDomain(ChampionshipStageDto stage) {
        return ChampionshipStage.valueOf(stage.name());
    }

    private static ChampionshipPredictionDto toDto(ChampionshipDetails.OwnPrediction own) {
        return own == null ? null : toDto(own.prediction(), own.points());
    }
}
