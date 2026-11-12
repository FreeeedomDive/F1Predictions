package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.standings.SeasonParticipantChart;
import com.xdd.f1.application.standings.SeasonStandings;
import com.xdd.f1.application.standings.SeasonStandingsRow;
import com.xdd.f1.contract.standings.*;

final class SeasonStandingsDtoMapper {
    private SeasonStandingsDtoMapper() {
    }

    static SeasonStandingsDto toDto(SeasonStandings standings) {
        return new SeasonStandingsDto(
                standings.season(),
                standings.events().stream().map(RaceDtoMapper::toDto).toList(),
                standings.rows().stream().map(SeasonStandingsDtoMapper::toDto).toList(),
                standings.currentLeaderParticipantId() == null ? null : standings.currentLeaderParticipantId().value(),
                standings.currentLeaderPoints(),
                standings.pointsLeft(),
                standings.charts().stream().map(SeasonStandingsDtoMapper::toDto).toList(),
                standings.championChart()
        );
    }

    private static SeasonStandingsRowDto toDto(SeasonStandingsRow row) {
        return new SeasonStandingsRowDto(
                row.participantId().value(),
                row.participantName(),
                row.totalPoints(),
                row.maximumTenthPlaceCount(),
                row.results().stream()
                        .map(result -> new SeasonRacePointsDto(result.raceId().value(), RaceResultDtoMapper.toDto(result.points())))
                        .toList(),
                row.previousPodiums().stream().map(podium -> new SeasonPodiumDto(podium.season(), podium.place())).toList()
        );
    }

    private static SeasonParticipantChartDto toDto(SeasonParticipantChart chart) {
        return new SeasonParticipantChartDto(chart.participantId().value(), chart.points());
    }
}
