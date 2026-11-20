package com.xdd.f1.domain.championship;

import com.xdd.f1.domain.error.DomainValidationException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record ChampionshipDriverStandings(
        int season,
        List<String> orderedDrivers,
        Integer lastSyncedRound,
        Instant updatedAt
) {
    public ChampionshipDriverStandings {
        if (orderedDrivers == null || orderedDrivers.isEmpty()
                || orderedDrivers.stream().anyMatch(driver -> driver == null || driver.isBlank())) {
            throw new DomainValidationException("Championship standings must contain driver names");
        }
        if (new HashSet<>(orderedDrivers).size() != orderedDrivers.size()) {
            throw new DomainValidationException("Driver must occur only once in the championship standings");
        }
        orderedDrivers = List.copyOf(orderedDrivers);
        Objects.requireNonNull(updatedAt, "Championship standings update time must not be null");
    }
}
