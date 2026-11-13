package com.xdd.f1.discord.consumers;

import com.xdd.f1.contract.race.RaceStartingGridUpdatedEvent;
import com.xdd.f1.discord.notifications.RaceNotificationSender;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@ConditionalOnProperty(prefix = "discord", name = "enabled", havingValue = "true")
public class RaceStartingGridUpdatedConsumer {
    private final RaceNotificationSender notificationSender;

    public RaceStartingGridUpdatedConsumer(RaceNotificationSender notificationSender) {
        this.notificationSender = notificationSender;
    }

    @ApplicationModuleListener
    public void onRaceStartingGridUpdated(RaceStartingGridUpdatedEvent event) {
        var raceName = event.raceName() + (event.raceType().equals("SPRINT") ? " (Sprint)" : "");
        var grid = IntStream.range(0, event.startingGrid().size())
                .mapToObj(index -> (index + 1) + ". " + event.startingGrid().get(index))
                .collect(Collectors.joining("\n"));
        var embed = new EmbedBuilder()
                .setColor(new Color(0x8B5CF6))
                .setTitle("Starting grid " + (event.firstPublication() ? "published" : "updated")
                        + " · " + raceName + " · " + event.season())
                .setDescription(grid)
                .build();

        notificationSender.sendToLeagues(event.season(), event.raceType(), embed);
    }
}
