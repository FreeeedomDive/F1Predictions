package com.xdd.f1.domain.error;

public class DomainValidationException extends IllegalArgumentException {

    public DomainValidationException(String message) {
        super(message);
    }
}
