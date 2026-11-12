package com.xdd.f1.bootstrap.historyimport;

import com.xdd.f1.application.historyimport.HistoryImport;
import com.xdd.f1.contract.historyimport.ImportReportDto;
import com.xdd.f1.contract.historyimport.ImportRequestDto;
import com.xdd.f1.contract.prediction.PredictionPointsDto;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.prediction.*;
import com.xdd.f1.domain.race.*;
import com.xdd.f1.domain.race.lineup.*;
import com.xdd.f1.domain.race.result.*;
import com.xdd.f1.domain.user.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

final class ImportDtoMapper {
    private ImportDtoMapper() {
    }

    static HistoryImport.UserEntry user(ImportRequestDto.User entry) {
        var discordId = text(entry.discordId(), "discordId");
        try {
            if (!discordId.matches("[1-9][0-9]*") || Long.parseLong(discordId) <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            throw new DomainValidationException("discordId must be a positive Discord snowflake string");
        }
        return new HistoryImport.UserEntry(
                new User(new UserId(required(entry.id(), "id")), time(entry.createdAt(), "createdAt")),
                new UserIdentityId(UserIdentitySource.DISCORD, discordId), text(entry.displayName(), "displayName"),
                time(entry.joinedAt(), "joinedAt"));
    }

    static Race race(ImportRequestDto.Race entry) {
        return new Race(new RaceId(required(entry.id(), "id")), required(entry.season(), "season"), required(entry.round(), "round"),
                enumValue(RaceType.class, entry.type(), "type"), text(entry.name(), "name"),
                enumValue(RaceStatus.class, entry.status(), "status"), time(entry.openAt(), "openAt"), time(entry.closeAt(), "closeAt"));
    }

    static HistoryImport.LineupEntry lineup(ImportRequestDto.RaceLineup entry) {
        var raceId = new RaceId(required(entry.raceId(), "raceId"));
        var teams = list(entry.teams(), "teams", team -> {
            var badge = text(team.badge(), "badge");
            if (badge.length() > 3) {
                throw new DomainValidationException("Team badge must have at most three characters");
            }
            return new Team(new TeamBranding(text(team.name(), "name"), badge,
                    text(team.backgroundColor(), "backgroundColor"), text(team.textColor(), "textColor")),
                    list(team.drivers(), "drivers", Driver::new));
        });
        var conditions = entry.conditions() == null ? null : new PredictionConditions(raceId,
                driver(entry.conditions().positionPredictionDriver()));
        return new HistoryImport.LineupEntry(new RaceLineup(raceId, teams), conditions);
    }

    static HistoryImport.PredictionEntry prediction(ImportRequestDto.Prediction entry) {
        var dnf = entry.dnfPickedDrivers() == null ? null : new DnfPrediction(list(entry.dnfPickedDrivers(), "dnfPickedDrivers", Driver::new));
        return new HistoryImport.PredictionEntry(new RaceId(required(entry.raceId(), "raceId")),
                new UserId(required(entry.userId(), "userId")),
                new PredictionValues(driver(entry.tenthPlacePickedDriver()), dnf,
                        entry.safetyCarsPrediction() == null ? null : SafetyCarsCount.valueOf(entry.safetyCarsPrediction().name()),
                        entry.driverPositionPrediction(), gap(entry.firstPlaceLeadPrediction())),
                entry.teamsPickedDrivers() == null ? null : list(entry.teamsPickedDrivers(), "teamsPickedDrivers", name -> text(name, "driver")));
    }

    static HistoryImport.ResultEntry result(ImportRequestDto.Result entry) {
        var raceId = new RaceId(required(entry.raceId(), "raceId"));
        var values = required(entry.result(), "result");
        var classification = list(values.classification(), "classification", classified ->
                new RaceClassificationEntry(new Driver(text(classified.driverName(), "driverName")),
                        RaceDriverStatus.valueOf(required(classified.status(), "status").name())));
        var result = new RaceResult(raceId, new RulesVersion(text(entry.rulesVersion(), "rulesVersion")),
                new RaceResultValues(classification, values.safetyCars(), gap(values.firstPlaceLead())), null);
        var points = list(entry.points(), "points", value -> new HistoryImport.PointsEntry(
                new UserId(required(value.userId(), "userId")), required(value.totalPoints(), "totalPoints"),
                value.tenthPlacePoints(), value.dnfsPoints(), value.safetyCarsPoints(), value.firstPlaceLeadPoints(),
                value.teamMatesPoints(), value.driverPositionPoints()));
        return new HistoryImport.ResultEntry(result, points);
    }

    static HistoryImport.PodiumEntry podium(ImportRequestDto.SeasonPodium entry) {
        return new HistoryImport.PodiumEntry(required(entry.season(), "season"), list(entry.userIds(), "userIds", UserId::new));
    }

    static ImportReportDto report(HistoryImport.Report report) {
        return new ImportReportDto(report.created(), report.existing(), report.updated(), report.pointsDifferences().stream()
                .map(value -> new ImportReportDto.PointsDifference(value.raceId().value(), value.userId().value(),
                        points(value.imported()), points(value.calculated()))).toList());
    }

    static <T, R> List<R> list(List<T> values, String field, Function<T, R> mapper) {
        required(values, field);
        var mapped = new java.util.ArrayList<R>();
        for (var index = 0; index < values.size(); index++) {
            try {
                mapped.add(mapper.apply(required(values.get(index), field + " entry")));
            } catch (DomainValidationException exception) {
                throw new DomainValidationException(field + "[" + index + "]: " + exception.getMessage());
            }
        }
        return List.copyOf(mapped);
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new DomainValidationException(field + " is required");
        }
        return value;
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field + " must not be blank");
        }
        return value;
    }

    private static Driver driver(String value) {
        return value == null ? null : new Driver(text(value, "driver"));
    }

    private static Instant time(Instant value, String field) {
        return required(value, field).truncatedTo(ChronoUnit.MICROS);
    }

    private static BigDecimal gap(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0 || value.compareTo(new BigDecimal("999999.999")) > 0) {
            throw new DomainValidationException("Winner gap must be between 0 and 999999.999 seconds");
        }
        return value.setScale(3, RoundingMode.DOWN);
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String field) {
        try {
            return Enum.valueOf(type, text(value, field));
        } catch (IllegalArgumentException exception) {
            throw new DomainValidationException("Unsupported " + field + ": " + value);
        }
    }

    private static PredictionPointsDto points(PredictionPoints value) {
        return new PredictionPointsDto(value.rulesVersion().value(), value.totalPoints(), value.tenthPlacePoints(),
                value.dnfsPoints(), value.safetyCarsPoints(), value.firstPlaceLeadPoints(), value.teamMatesPoints(), value.driverPositionPoints());
    }
}
