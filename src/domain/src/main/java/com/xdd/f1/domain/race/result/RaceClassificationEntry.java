package com.xdd.f1.domain.race.result;

import com.xdd.f1.domain.race.lineup.Driver;

import java.util.Objects;

public record RaceClassificationEntry(Driver driver, RaceDriverStatus status) {
    public RaceClassificationEntry {
        Objects.requireNonNull(driver, "Driver must not be null");
        Objects.requireNonNull(status, "Driver status must not be null");
    }
}
