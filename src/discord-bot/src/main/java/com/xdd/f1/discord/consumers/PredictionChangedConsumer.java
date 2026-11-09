package com.xdd.f1.discord.consumers;

import com.xdd.f1.contract.prediction.PredictionChangedEvent;
import com.xdd.f1.discord.notifications.RaceNotificationSender;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.awt.Color;

@Component
@ConditionalOnProperty(prefix = "discord", name = "enabled", havingValue = "true")
public class PredictionChangedConsumer {
    private final RaceNotificationSender sender;

    public PredictionChangedConsumer(RaceNotificationSender sender) {
        this.sender = sender;
    }

    @ApplicationModuleListener
    public void onPredictionChanged(PredictionChangedEvent event) {
        var raceName = event.raceName() + (event.raceType().equals("SPRINT") ? " (Sprint)" : "");
        var embed = new EmbedBuilder()
                .setColor(new Color(0x5865F2))
                .setTitle(event.created() ? "Prediction added" : "Prediction updated")
                .addField(raceName, event.participantName(), false)
                .build();
        sender.sendToLeague(event.leagueId(), event.raceType(), embed);
    }
}
