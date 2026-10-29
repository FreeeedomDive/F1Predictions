package com.xdd.f1.discord.configuration;

import com.xdd.f1.contract.league.LeagueApi;
import com.xdd.f1.discord.commands.AbstractCommand;
import com.xdd.f1.discord.commands.CommandListener;
import com.xdd.f1.discord.league.LeagueGuildListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    public JDA jda(LeagueGuildListener listener, CommandListener commandListener) {
        return JDABuilder.createDefault(properties.token())
                .addEventListeners(listener, commandListener)
                .build();
    }

    @Bean(destroyMethod = "close")
    public ExecutorService discordExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public LeagueGuildListener leagueGuildListener(
            LeagueApi leagues,
            @Qualifier("discordExecutor") ExecutorService executor
    ) {
        return new LeagueGuildListener(leagues, executor);
    }

    @Bean
    public CommandListener commandListener(List<AbstractCommand> commands) {
        return new CommandListener(commands);
    }
}