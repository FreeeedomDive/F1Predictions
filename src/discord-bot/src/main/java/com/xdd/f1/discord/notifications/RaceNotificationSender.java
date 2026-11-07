package com.xdd.f1.discord.notifications;

import com.xdd.f1.contract.league.LeagueApi;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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
            if (raceType.equals("SPRINT") && !league.settings().sprintsEnabled()) {
                continue;
            }

            var channelId = league.settings().notificationsChannelId();
            if (channelId == null || channelId.isBlank()) {
                continue;
            }

            var channel = jda.getChannelById(GuildMessageChannel.class, channelId);
            if (channel == null) {
                log.warn("Discord notification channel {} for league {} was not found", channelId, league.id());
                continue;
            }

            channel.sendMessageEmbeds(embed).complete();
        }
    }
}
