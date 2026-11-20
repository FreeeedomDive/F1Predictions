package com.xdd.f1.bootstrap.championship;

import com.xdd.f1.application.championship.ChampionshipDetails;
import com.xdd.f1.application.championship.ChampionshipScoredPrediction;
import com.xdd.f1.application.championship.ChampionshipTeamInfo;
import com.xdd.f1.bootstrap.race.RaceLineupDtoMapper;
import com.xdd.f1.contract.championship.ChampionshipDetailsDto;
import com.xdd.f1.contract.championship.ChampionshipParticipantPointsDto;
import com.xdd.f1.contract.championship.ChampionshipPredictionDto;
import com.xdd.f1.contract.championship.ChampionshipPredictionSourceDto;
import com.xdd.f1.contract.championship.ChampionshipSeasonDto;
import com.xdd.f1.contract.championship.ChampionshipStandingsUpdateDto;
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
        var season = toDto(details.season(), details.configuration(), details.driverStandings(), details.teamInfo());
        return new ChampionshipDetailsDto(season.season(), season.configured(), season.rulesVersion(),
                season.currentStage(), season.stageStarted(), season.predictionsOpen(),
                season.preSeasonRoster(), season.midSeasonRoster(), season.driverStandings(), season.standingsUpdate(),
                toDto(details.preSeasonPrediction()), toDto(details.midSeasonPrediction()),
                details.rows().stream().map(row -> new ChampionshipParticipantPointsDto(
                        row.participantId().value(), row.displayName(), row.totalPoints())).toList(),
                season.teams(), season.currentDrivers());
    }

    static ChampionshipSeasonDto toDto(int season, ChampionshipSeason configuration, ChampionshipDriverStandings standings,
                                       ChampionshipTeamInfo teamInfo) {
        return new ChampionshipSeasonDto(season, configuration != null,
                configuration == null ? null : configuration.rulesVersion().value(),
                configuration == null ? null : ChampionshipStageDto.valueOf(configuration.stage().name()),
                configuration != null && configuration.stageStarted(),
                configuration != null && configuration.isOpen(),
                configuration == null ? List.of() : configuration.preSeasonRoster(),
                configuration == null ? List.of() : configuration.midSeasonRoster(),
                standings == null ? List.of() : standings.orderedDrivers(),
                standings == null ? null : new ChampionshipStandingsUpdateDto(standings.lastSyncedRound(), standings.updatedAt()),
                teamInfo.teams().stream().map(RaceLineupDtoMapper::toDto).toList(), teamInfo.currentDrivers());
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

    private static ChampionshipPredictionDto toDto(ChampionshipScoredPrediction own) {
        return own == null ? null : toDto(own.prediction(), own.points());
    }
}
