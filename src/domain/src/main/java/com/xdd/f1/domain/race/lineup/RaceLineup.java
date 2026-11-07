package com.xdd.f1.domain.race.lineup;

import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.race.RaceId;

import java.util.List;
import java.util.Objects;

public record RaceLineup(RaceId raceId, List<Team> teams) {
    public RaceLineup {
        Objects.requireNonNull(raceId, "Race ID must not be null");
        Objects.requireNonNull(teams, "Race teams must not be null");
        teams = List.copyOf(teams);
        if (teams.isEmpty()) {
            throw new DomainValidationException("Race lineup must have at least one team");
        }

        var teamNames = teams.stream().map(team -> team.branding().name()).toList();
        if (teamNames.stream().distinct().count() != teamNames.size()) {
            throw new DomainValidationException("Team must occur only once in a race lineup");
        }

        var driverNames = teams.stream()
                .flatMap(team -> team.drivers().stream())
                .map(Driver::name)
                .toList();
        if (driverNames.stream().distinct().count() != driverNames.size()) {
            throw new DomainValidationException("Driver must occur only once in a race lineup");
        }
    }

    public RaceLineup replaceDriver(Driver previousDriver, Driver newDriver) {
        Objects.requireNonNull(previousDriver, "Previous driver must not be null");
        Objects.requireNonNull(newDriver, "New driver must not be null");
        var drivers = teams.stream().flatMap(team -> team.drivers().stream()).toList();
        if (!drivers.contains(previousDriver)) {
            if (drivers.contains(newDriver)) {
                return this;
            }
            throw new DomainValidationException("Driver not found in race lineup: " + previousDriver.name());
        }
        if (previousDriver.equals(newDriver)) {
            return this;
        }

        var updatedTeams = teams.stream()
                .map(team -> new Team(
                        team.branding(),
                        team.drivers().stream()
                                .map(driver -> driver.equals(previousDriver) ? newDriver : driver)
                                .toList()
                ))
                .toList();
        return new RaceLineup(raceId, updatedTeams);
    }
}
