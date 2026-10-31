package com.xdd.f1.discord.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("web")
public record WebAppProperties(String appBaseUrl) {
}
