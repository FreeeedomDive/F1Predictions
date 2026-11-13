package com.xdd.f1.bootstrap.historyimport;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("imports")
public record ImportProperties(String apiKey) {
}
