package com.xdd.f1.domain.prediction;

import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.DriverReplacement;

import java.util.List;
import java.util.Objects;

public record Prediction(
        PredictionId id,
        LeagueId leagueId,
        LeagueParticipantId participantId,
        RaceId raceId,
        PredictionValues values,
        List<String> teamsPickedDrivers
) {
    public Prediction {
        Objects.requireNonNull(id, "Prediction ID must not be null");
        Objects.requireNonNull(leagueId, "League ID must not be null");
        Objects.requireNonNull(participantId, "League participant ID must not be null");
        Objects.requireNonNull(raceId, "Race ID must not be null");
        Objects.requireNonNull(values, "Prediction values must not be null");
        teamsPickedDrivers = teamsPickedDrivers == null ? null : List.copyOf(teamsPickedDrivers);
    }

    public Prediction replaceDrivers(List<DriverReplacement> replacements) {
        var dnf = values.dnfPrediction();
        var updated = new PredictionValues(
                replace(values.tenthPlacePickedDriver(), replacements),
                dnf == null ? null : new DnfPrediction(
                        dnf.dnfPickedDrivers().stream().map(driver -> replace(driver, replacements)).toList()),
                values.safetyCarsPrediction(), values.driverPositionPrediction(), values.firstPlaceLeadPrediction()
        );
        var teams = teamsPickedDrivers == null ? null : teamsPickedDrivers.stream()
                .map(name -> replacements.stream().filter(pair -> pair.previousDriver().name().equals(name))
                        .map(pair -> pair.newDriver().name()).findFirst().orElse(name))
                .toList();
        return new Prediction(id, leagueId, participantId, raceId, updated, teams);
    }

    private Driver replace(Driver driver, List<DriverReplacement> replacements) {
        return replacements.stream().filter(pair -> pair.previousDriver().equals(driver))
                .map(DriverReplacement::newDriver).findFirst().orElse(driver);
    }
}
