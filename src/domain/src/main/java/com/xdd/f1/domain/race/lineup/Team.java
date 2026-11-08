package com.xdd.f1.domain.race.lineup;

import com.xdd.f1.domain.error.DomainValidationException;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public record Team(TeamBranding branding, List<Driver> drivers) {
    public Team {
        Objects.requireNonNull(branding, "Team branding must not be null");
        Objects.requireNonNull(drivers, "Team drivers must not be null");
        drivers = List.copyOf(drivers);
        if (drivers.isEmpty()) {
            throw new DomainValidationException("Team must have at least one driver");
        }
    }

    public List<DriverReplacement> driverReplacementsTo(Team updated) {
        var removed = drivers.stream().filter(driver -> !updated.drivers().contains(driver)).toList();
        var added = updated.drivers().stream().filter(driver -> !drivers.contains(driver)).toList();
        if (removed.isEmpty()) {
            return List.of();
        }
        if (removed.size() != added.size()) {
            throw new DomainValidationException("Each removed driver must have a replacement in the same team");
        }
        return IntStream.range(0, removed.size())
                .mapToObj(index -> new DriverReplacement(removed.get(index), added.get(index)))
                .toList();
    }
}
