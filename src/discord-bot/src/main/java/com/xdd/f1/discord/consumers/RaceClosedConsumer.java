package com.xdd.f1.discord.consumers;

import com.xdd.f1.contract.race.RaceClosedEvent;
import com.xdd.f1.discord.notifications.RaceNotificationSender;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.awt.Color;

@Component
@ConditionalOnProperty(prefix = "discord", name = "enabled", havingValue = "true")
public class RaceClosedConsumer {

    private final RaceNotificationSender notificationSender;

    public RaceClosedConsumer(RaceNotificationSender notificationSender) {
        this.notificationSender = notificationSender;
    }

    @ApplicationModuleListener
    public void onRaceClosed(RaceClosedEvent event) {
        var raceName = event.raceName() + (event.raceType().equals("SPRINT") ? " (Sprint)" : "");
        var embed = new EmbedBuilder()
                .setColor(Color.RED)
                .addField(
                        "Predictions closed",
                        "Predictions are closed for " + raceName + ".",
                        false
                )
                .build();

        notificationSender.sendToLeagues(event.raceType(), embed);
    }
}
