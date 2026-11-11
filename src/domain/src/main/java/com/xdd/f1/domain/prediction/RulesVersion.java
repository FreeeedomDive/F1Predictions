package com.xdd.f1.domain.prediction;

import com.xdd.f1.domain.error.DomainValidationException;

import java.util.Objects;

public record RulesVersion(String value) {
    public RulesVersion {
        Objects.requireNonNull(value, "Rules version must not be null");
        if (value.isBlank()) {
            throw new DomainValidationException("Rules version must not be blank");
        }
    }
}
