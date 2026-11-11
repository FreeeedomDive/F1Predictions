package com.xdd.f1.discord.notifications;

import com.xdd.f1.contract.league.LeagueApi;
import com.xdd.f1.contract.league.LeagueDto;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "discord", name = "enabled", havingValue = "true")
public class RaceNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(RaceNotificationSender.class);

    private final LeagueApi leagueApi;
    private final JDA jda;

    public RaceNotificationSender(LeagueApi leagueApi, JDA jda) {
        this.leagueApi = leagueApi;
        this.jda = jda;
    }

    public void sendToLeagues(String raceType, MessageEmbed embed) {
        var discordLeagues = leagueApi.findDiscordLeagues();
        for (var league : discordLeagues) {
            send(league, raceType, List.of(embed));
        }
    }

    public void sendToLeague(UUID leagueId, String raceType, MessageEmbed embed) {
        sendToLeague(leagueId, raceType, List.of(embed));
    }

    public void sendToLeague(UUID leagueId, String raceType, List<MessageEmbed> embeds) {
        leagueApi.findDiscordLeagues().stream().filter(league -> league.id().equals(leagueId))
                .findFirst().ifPresent(league -> send(league, raceType, embeds));
    }

    private void send(LeagueDto league, String raceType, List<MessageEmbed> embeds) {
        if (raceType.equals("SPRINT") && !league.settings().sprintsEnabled()) {
            return;
        }
        var channelId = league.settings().notificationsChannelId();
        if (channelId == null || channelId.isBlank()) {
            return;
        }
        var channel = jda.getChannelById(GuildMessageChannel.class, channelId);
        if (channel == null) {
            log.warn("Discord notification channel {} for league {} was not found", channelId, league.id());
            return;
        }
        for (var embed : embeds) {
            channel.sendMessageEmbeds(embed).complete();
        }
    }
}
