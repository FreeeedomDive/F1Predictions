package com.xdd.f1.discord.consumers;

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
        var raceName = event.raceName() + (event.raceType().equals("SPRINT") ? " (Sprint)" : "");
        var replacements = event.replacements().stream()
                .map(replacement -> replacement.previousDriverName() + " → " + replacement.newDriverName())
                .collect(Collectors.joining("\n"));
        var embed = new EmbedBuilder()
                .setColor(Color.decode(event.teamColor()))
                .addField(
                        "Driver replacements",
                        raceName + " — " + event.teamName() + "\n" + replacements,
                        false
                )
                .build();

        notificationSender.sendToLeagues(event.raceType(), embed);
    }
}
