package com.xdd.f1.bootstrap.race;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties("openf1")
public record OpenF1Properties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public OpenF1Properties {
        Objects.requireNonNull(connectTimeout, "OpenF1 connect timeout must not be null");
        Objects.requireNonNull(readTimeout, "OpenF1 read timeout must not be null");
        if (connectTimeout.isZero() || connectTimeout.isNegative()
                || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("OpenF1 timeouts must be positive");
        }
    }
}
