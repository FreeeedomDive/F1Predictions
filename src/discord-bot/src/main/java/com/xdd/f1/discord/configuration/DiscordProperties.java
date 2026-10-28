package com.xdd.f1.discord.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("discord")
public record DiscordProperties(boolean enabled, String token) {
}
