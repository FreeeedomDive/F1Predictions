package com.xdd.f1.discord.consumers;

import com.xdd.f1.contract.race.RaceFinishedEvent;
import com.xdd.f1.discord.notifications.RaceNotificationSender;
import com.xdd.f1.discord.notifications.RaceResultEmbedBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "discord", name = "enabled", havingValue = "true")
public class RaceFinishedConsumer {
    private final RaceNotificationSender notificationSender;
    private final RaceResultEmbedBuilder embedBuilder;

    public RaceFinishedConsumer(RaceNotificationSender notificationSender, RaceResultEmbedBuilder embedBuilder) {
        this.notificationSender = notificationSender;
        this.embedBuilder = embedBuilder;
    }

    @ApplicationModuleListener
    public void onRaceFinished(RaceFinishedEvent event) {
        notificationSender.sendToLeague(event.leagueId(), event.raceType(), embedBuilder.build(event));
    }
}
