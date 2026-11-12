package com.xdd.f1.application.historyimport;

import com.xdd.f1.domain.prediction.PredictionConditions;
import com.xdd.f1.domain.prediction.PredictionPoints;
import com.xdd.f1.domain.prediction.PredictionValues;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.lineup.RaceLineup;
import com.xdd.f1.domain.race.result.RaceResult;
import com.xdd.f1.domain.user.User;
import com.xdd.f1.domain.user.UserId;
import com.xdd.f1.domain.user.UserIdentityId;

import java.time.Instant;
import java.util.List;

public final class HistoryImport {
    private HistoryImport() {
    }

    public record UserEntry(User user, UserIdentityId identity, String displayName, Instant joinedAt) {
    }

    public record LineupEntry(RaceLineup lineup, PredictionConditions conditions) {
    }

    public record PredictionEntry(RaceId raceId, UserId userId, PredictionValues values, List<String> teamsPickedDrivers) {
    }

    public record ResultEntry(RaceResult result, List<PointsEntry> points) {
    }

    public record PointsEntry(UserId userId, int totalPoints, Integer tenthPlacePoints, Integer dnfsPoints,
                              Integer safetyCarsPoints, Integer firstPlaceLeadPoints, Integer teamMatesPoints,
                              Integer driverPositionPoints) {
    }

    public record PodiumEntry(int season, List<UserId> userIds) {
    }

    public record Report(int created, int existing, int updated, List<PointsDifference> pointsDifferences) {
        public Report {
            pointsDifferences = List.copyOf(pointsDifferences);
        }
    }

    public record PointsDifference(RaceId raceId, UserId userId, PredictionPoints imported, PredictionPoints calculated) {
    }
}
