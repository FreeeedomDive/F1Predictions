package com.xdd.f1.bootstrap.race;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties("jolpica")
public record JolpicaProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public JolpicaProperties {
        Objects.requireNonNull(connectTimeout, "Jolpica connect timeout must not be null");
        Objects.requireNonNull(readTimeout, "Jolpica read timeout must not be null");
        if (connectTimeout.isZero() || connectTimeout.isNegative()
                || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("Jolpica timeouts must be positive");
        }
    }
}
