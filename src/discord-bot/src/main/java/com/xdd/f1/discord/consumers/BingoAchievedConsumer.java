package com.xdd.f1.discord.consumers;

import com.xdd.f1.contract.bingo.BingoAchievedEvent;
import com.xdd.f1.discord.notifications.LeagueNotificationSender;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.awt.Color;

@Component
@ConditionalOnProperty(prefix = "discord", name = "enabled", havingValue = "true")
public class BingoAchievedConsumer {
    private final LeagueNotificationSender notificationSender;

    public BingoAchievedConsumer(LeagueNotificationSender notificationSender) {
        this.notificationSender = notificationSender;
    }

    @ApplicationModuleListener
    public void onBingoAchieved(BingoAchievedEvent event) {
        var name = event.displayName().replace('\n', ' ').replace('\r', ' ').strip();
        int end = name.offsetByCodePoints(0, Math.min(100, name.codePointCount(0, name.length())));
        name = MarkdownSanitizer.escape(name.substring(0, end));
        var embed = new EmbedBuilder()
                .setColor(new Color(0xFFD700))
                .setTitle("Bingo!")
                .setDescription("**" + name + "** has achieved bingo!")
                .build();
        notificationSender.sendToLeague(event.leagueId(), embed);
    }
}
