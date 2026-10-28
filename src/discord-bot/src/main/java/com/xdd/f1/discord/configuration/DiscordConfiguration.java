package com.xdd.f1.discord.configuration;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DiscordProperties.class)
@ConditionalOnProperty(
        prefix = "discord",
        name = "enabled",
        havingValue = "true"
)
public class DiscordConfiguration {

    private final DiscordProperties properties;

    public DiscordConfiguration(DiscordProperties properties) {
        this.properties = properties;
    }

    @Bean(destroyMethod = "shutdown")
    public JDA jda(){
        var builder = JDABuilder.createDefault(properties.token());
        return builder.build();
    }
}