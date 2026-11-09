package com.xdd.f1.domain.prediction;

import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.race.lineup.Driver;

import java.util.List;
import java.util.Objects;

public record DnfPrediction(List<Driver> dnfPickedDrivers) {
    public DnfPrediction {
        Objects.requireNonNull(dnfPickedDrivers, "DNF drivers must not be null");
        dnfPickedDrivers = List.copyOf(dnfPickedDrivers);
        if (dnfPickedDrivers.stream().distinct().count() != dnfPickedDrivers.size()) {
            throw new DomainValidationException("DNF prediction must not include duplicate drivers");
        }
    }
}
