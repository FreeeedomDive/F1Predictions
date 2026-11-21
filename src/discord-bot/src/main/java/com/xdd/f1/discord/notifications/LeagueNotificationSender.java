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
public class LeagueNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(LeagueNotificationSender.class);

    private final LeagueApi leagueApi;
    private final JDA jda;

    public LeagueNotificationSender(LeagueApi leagueApi, JDA jda) {
        this.leagueApi = leagueApi;
        this.jda = jda;
    }

    public void sendToLeague(UUID leagueId, MessageEmbed embed) {
        leagueApi.findDiscordLeagues().stream().filter(league -> league.id().equals(leagueId))
                .findFirst().ifPresent(league -> send(league, List.of(embed)));
    }

    void send(LeagueDto league, List<MessageEmbed> embeds) {
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
