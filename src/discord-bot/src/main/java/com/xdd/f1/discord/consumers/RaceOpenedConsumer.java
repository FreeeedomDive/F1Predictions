package com.xdd.f1.discord.consumers;

import com.xdd.f1.contract.race.RaceOpenedEvent;
import com.xdd.f1.discord.notifications.RaceNotificationSender;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.awt.Color;

@Component
@ConditionalOnProperty(prefix = "discord", name = "enabled", havingValue = "true")
public class RaceOpenedConsumer {

    private final RaceNotificationSender notificationSender;

    public RaceOpenedConsumer(RaceNotificationSender notificationSender) {
        this.notificationSender = notificationSender;
    }

    @ApplicationModuleListener
    public void onRaceOpened(RaceOpenedEvent event) {
        var raceName = event.raceName() + (event.raceType().equals("SPRINT") ? " (Sprint)" : "");
        var embed = new EmbedBuilder()
                .setColor(Color.GREEN)
                .addField(
                        "Predictions opened",
                        "Predictions are now open for " + raceName + ".\nDeadline: <t:"
                                + event.closeAt().getEpochSecond() + ":F> (<t:"
                                + event.closeAt().getEpochSecond() + ":R>)",
                        false
                )
                .build();

        notificationSender.sendToLeagues(event.raceType(), embed);
    }
}
