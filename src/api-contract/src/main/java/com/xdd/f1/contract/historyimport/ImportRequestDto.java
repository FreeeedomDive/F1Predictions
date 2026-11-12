package com.xdd.f1.contract.historyimport;

import com.xdd.f1.contract.league.LeagueSettingsDto;
import com.xdd.f1.contract.prediction.PredictionConditionsDto;
import com.xdd.f1.contract.prediction.SafetyCarsCountDto;
import com.xdd.f1.contract.race.lineup.RaceLineupTeamDto;
import com.xdd.f1.contract.race.result.RaceResultValuesDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImportRequestDto<T>(UUID leagueId, List<T> data) {
    public record LeagueSettings(UUID leagueId, LeagueSettingsDto settings) {
    }

    public record User(UUID id, String discordId, String displayName, Instant createdAt, Instant joinedAt) {
    }

    public record Race(UUID id, Integer season, Integer round, String type, String name, String status,
                       Instant openAt, Instant closeAt) {
    }

    public record RaceLineup(UUID raceId, List<RaceLineupTeamDto> teams, PredictionConditionsDto conditions) {
    }

    public record Prediction(UUID raceId, UUID userId, String tenthPlacePickedDriver, List<String> dnfPickedDrivers,
                             SafetyCarsCountDto safetyCarsPrediction, Integer driverPositionPrediction,
                             BigDecimal firstPlaceLeadPrediction, List<String> teamsPickedDrivers) {
    }

    public record Result(UUID raceId, String rulesVersion, RaceResultValuesDto result, List<Points> points) {
    }

    public record Points(UUID userId, Integer totalPoints, Integer tenthPlacePoints, Integer dnfsPoints,
                         Integer safetyCarsPoints, Integer firstPlaceLeadPoints, Integer teamMatesPoints,
                         Integer driverPositionPoints) {
    }

    public record SeasonPodium(Integer season, List<UUID> userIds) {
    }
}
