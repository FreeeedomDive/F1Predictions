package com.xdd.f1.domain.race.lineup;

import com.xdd.f1.domain.error.DomainValidationException;

import java.util.Objects;
import java.util.regex.Pattern;

public record TeamBranding(String name, String badge, String backgroundColor, String textColor) {
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    public TeamBranding {
        Objects.requireNonNull(name, "Team name must not be null");
        Objects.requireNonNull(badge, "Team badge must not be null");
        Objects.requireNonNull(backgroundColor, "Team background color must not be null");
        Objects.requireNonNull(textColor, "Team text color must not be null");

        if (name.isBlank()) {
            throw new DomainValidationException("Team name must not be blank");
        }
        if (badge.isBlank()) {
            throw new DomainValidationException("Team badge must not be blank");
        }
        if (!HEX_COLOR.matcher(backgroundColor).matches()) {
            throw new DomainValidationException("Team background color must be a six-digit hex color");
        }
        if (!HEX_COLOR.matcher(textColor).matches()) {
            throw new DomainValidationException("Team text color must be a six-digit hex color");
        }
    }
}
