package com.xdd.f1.domain.race.lineup;

import com.xdd.f1.domain.error.DomainValidationException;

import java.util.Objects;

public record Driver(String name) {
    public Driver {
        Objects.requireNonNull(name, "Driver name must not be null");
        if (name.isBlank()) {
            throw new DomainValidationException("Driver name must not be blank");
        }
    }
}
