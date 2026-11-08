package com.xdd.f1.domain.race.lineup;

import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.race.RaceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    public Optional<Team> findTeam(String name) {
        return teams.stream().filter(team -> team.branding().name().equals(name)).findFirst();
    }

    public RaceLineup addTeam(Team team) {
        var updatedTeams = new ArrayList<>(teams);
        updatedTeams.add(team);
        return new RaceLineup(raceId, updatedTeams);
    }

    public RaceLineup updateTeam(String previousName, Team updatedTeam) {
        if (findTeam(previousName).isEmpty()) {
            if (teams.contains(updatedTeam)) {
                return this;
            }
            throw new DomainValidationException("Team not found in race lineup: " + previousName);
        }
        var updatedTeams = teams.stream()
                .map(team -> team.branding().name().equals(previousName) ? updatedTeam : team)
                .toList();
        return new RaceLineup(raceId, updatedTeams);
    }

    public RaceLineup swapDrivers(Driver firstDriver, Driver secondDriver) {
        Objects.requireNonNull(firstDriver, "First driver must not be null");
        Objects.requireNonNull(secondDriver, "Second driver must not be null");
        var drivers = teams.stream().flatMap(team -> team.drivers().stream()).toList();
        if (!drivers.contains(firstDriver) || !drivers.contains(secondDriver)) {
            throw new DomainValidationException("Both drivers must belong to the race lineup to be swapped");
        }
        if (firstDriver.equals(secondDriver)) {
            return this;
        }

        var updatedTeams = teams.stream()
                .map(team -> new Team(
                        team.branding(),
                        team.drivers().stream()
                                .map(driver -> driver.equals(firstDriver) ? secondDriver
                                        : driver.equals(secondDriver) ? firstDriver : driver)
                                .toList()
                ))
                .toList();
        return new RaceLineup(raceId, updatedTeams);
    }
}
