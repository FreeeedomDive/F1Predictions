package com.xdd.f1.domain.race.lineup;

import com.xdd.f1.domain.error.DomainValidationException;

import java.util.List;
import java.util.Objects;

public record Team(TeamBranding branding, List<Driver> drivers) {
    public Team {
        Objects.requireNonNull(branding, "Team branding must not be null");
        Objects.requireNonNull(drivers, "Team drivers must not be null");
        drivers = List.copyOf(drivers);
        if (drivers.isEmpty()) {
            throw new DomainValidationException("Team must have at least one driver");
        }
    }
}
