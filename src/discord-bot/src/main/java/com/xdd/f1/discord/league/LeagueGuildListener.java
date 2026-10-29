package com.xdd.f1.discord.league;

import com.xdd.f1.contract.league.LeagueApi;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public class LeagueGuildListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(LeagueGuildListener.class);

    private final LeagueApi leagues;
    private final ExecutorService executor;

    public LeagueGuildListener(
            LeagueApi leagues,
            ExecutorService executor
    ) {
        this.leagues = leagues;
        this.executor = executor;
    }

    @Override
    public void onReady(ReadyEvent event) {
        for (var guild : event.getJDA().getGuilds()) {
            ensureLeague(guild);
        }
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        ensureLeague(event.getGuild());
    }

    private void ensureLeague(Guild guild) {
        long guildId = guild.getIdLong();
        String guildName = guild.getName();

        executor.execute(() -> {
            try {
                var league = leagues.ensureDiscordLeague(
                        guildId,
                        guildName
                );

                log.info(
                        "League {} is available for Discord guild {}",
                        league.id(),
                        guildId
                );
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to ensure league for Discord guild {}",
                        guildId,
                        exception
                );
            }
        });
    }
}