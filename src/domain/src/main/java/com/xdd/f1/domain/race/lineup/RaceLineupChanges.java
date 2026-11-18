package com.xdd.f1.domain.race.lineup;

import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.race.RaceId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RaceLineupChanges(
        RaceLineup lineup, List<DriverReplacement> replacements, List<TeamChange> teams
) {
    public static RaceLineupChanges calculate(
            RaceId raceId, List<Team> before, List<TeamEdit> edits, List<DriverReplacement> confirmed
    ) {
        var lineup = new RaceLineup(raceId, edits.stream().map(TeamEdit::team).toList());
        var remaining = new HashMap<String, Team>();
        before.forEach(team -> remaining.put(team.branding().name(), team));
        var previousTeams = new HashMap<Team, Team>();
        for (var edit : edits) {
            var team = edit.team();
            if (team.branding().badge().length() > 3) {
                throw new DomainValidationException("Team badge must not exceed 3 characters");
            }
            if (team.drivers().size() > 2) {
                throw new DomainValidationException("Team must not have more than two drivers");
            }
            if (edit.previousName() != null) {
                var previous = remaining.remove(edit.previousName());
                if (previous == null) {
                    throw new DomainValidationException("Invalid or repeated original team: " + edit.previousName());
                }
                previousTeams.put(team, previous);
            }
        }
        if (!remaining.isEmpty()) {
            throw new DomainValidationException("Existing teams must not be removed from the lineup");
        }

        var oldOwners = driverOwners(before);
        var newOwners = driverOwners(lineup.teams());
        var removed = oldOwners.keySet().stream().filter(driver -> !newOwners.containsKey(driver)).toList();
        var added = newOwners.keySet().stream().filter(driver -> !oldOwners.containsKey(driver)).toList();
        var replacements = new ArrayList<DriverReplacement>();
        var matchedRemoved = new HashSet<Driver>();
        var matchedAdded = new HashSet<Driver>();
        for (var pair : confirmed) {
            if (!removed.contains(pair.previousDriver()) || !added.contains(pair.newDriver())
                    || !matchedRemoved.add(pair.previousDriver()) || !matchedAdded.add(pair.newDriver())) {
                throw new DomainValidationException("Driver replacements must pair distinct removed and new drivers");
            }
            replacements.add(pair);
        }
        var unpairedRemoved = removed.stream().filter(driver -> !matchedRemoved.contains(driver)).toList();
        var unpairedAdded = added.stream().filter(driver -> !matchedAdded.contains(driver)).toList();
        if (unpairedRemoved.size() == 1 && unpairedAdded.size() == 1) {
            var outgoing = unpairedRemoved.getFirst();
            var incoming = unpairedAdded.getFirst();
            if (oldOwners.get(outgoing).equals(previousTeams.get(newOwners.get(incoming)))) {
                replacements.add(new DriverReplacement(outgoing, incoming));
                unpairedRemoved = List.of();
            }
        }
        if (!unpairedRemoved.isEmpty()) {
            throw new DomainValidationException("Choose a replacement for each driver removed from the lineup");
        }

        var teamChanges = lineup.teams().stream().map(team -> {
            var previous = previousTeams.get(team);
            var outgoing = previous == null ? List.<Driver>of() : previous.drivers().stream()
                    .filter(driver -> newOwners.containsKey(driver) && !team.equals(newOwners.get(driver))).toList();
            var incoming = team.drivers().stream()
                    .filter(driver -> oldOwners.containsKey(driver) && !oldOwners.get(driver).equals(previous)).toList();
            var replaced = replacements.stream()
                    .filter(pair -> oldOwners.get(pair.previousDriver()).equals(previous)).toList();
            return new TeamChange(team.branding(), replaced, outgoing, incoming);
        }).toList();
        return new RaceLineupChanges(lineup, List.copyOf(replacements), teamChanges);
    }

    private static Map<Driver, Team> driverOwners(List<Team> teams) {
        var owners = new LinkedHashMap<Driver, Team>();
        for (var team : teams) {
            team.drivers().forEach(driver -> owners.put(driver, team));
        }
        return owners;
    }

    public record TeamEdit(String previousName, Team team) {
    }

    public record TeamChange(
            TeamBranding branding, List<DriverReplacement> replacements, List<Driver> departures, List<Driver> arrivals
    ) {
    }
}
