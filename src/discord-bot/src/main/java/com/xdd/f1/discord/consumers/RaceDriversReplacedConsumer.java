package com.xdd.f1.discord.consumers;

import com.xdd.f1.contract.race.RaceDriversMovedEvent;
import com.xdd.f1.contract.race.RaceDriversReplacedEvent;
import com.xdd.f1.discord.notifications.RaceNotificationSender;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "discord", name = "enabled", havingValue = "true")
public class RaceDriversReplacedConsumer {

    private final RaceNotificationSender notificationSender;

    public RaceDriversReplacedConsumer(RaceNotificationSender notificationSender) {
        this.notificationSender = notificationSender;
    }

    @ApplicationModuleListener
    public void onRaceDriversReplaced(RaceDriversReplacedEvent event) {
        var replacements = event.replacements().stream()
                .map(replacement -> replacement.previousDriverName() + " → " + replacement.newDriverName())
                .collect(Collectors.joining("\n"));
        send(event.raceType(), event.raceName(), event.teamName(), event.teamColor(), "Driver replacements", replacements);
    }

    @ApplicationModuleListener
    public void onRaceDriversMoved(RaceDriversMovedEvent event) {
        var departures = event.departures().isEmpty() ? "—" : String.join(", ", event.departures());
        var arrivals = event.arrivals().isEmpty() ? "—" : String.join(", ", event.arrivals());
        var moves = departures + " → " + arrivals;
        send(event.raceType(), event.raceName(), event.teamName(), event.teamColor(), "Driver moves", moves);
    }

    private void send(String raceType, String raceName, String team, String color, String title, String changes) {
        var label = raceName + (raceType.equals("SPRINT") ? " (Sprint)" : "");
        var embed = new EmbedBuilder()
                .setColor(Color.decode(color))
                .addField(title, label + " — " + team + "\n" + changes, false)
                .build();

        notificationSender.sendToLeagues(raceType, embed);
    }
}
