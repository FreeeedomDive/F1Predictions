package com.xdd.f1.bootstrap.race;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jolpica")
public record JolpicaProperties(String baseUrl) {
}
