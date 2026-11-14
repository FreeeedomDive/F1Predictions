package com.xdd.f1.bootstrap.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("internal-api")
public record InternalApiProperties(String serviceKey) {
}
