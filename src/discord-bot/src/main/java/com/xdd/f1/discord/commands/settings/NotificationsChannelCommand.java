package com.xdd.f1.discord.commands.settings;

import com.xdd.f1.contract.league.LeagueApi;
import com.xdd.f1.discord.commands.core.*;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

@DiscordComponent
public class NotificationsChannelCommand extends AbstractCommand {

    private static final Logger log = LoggerFactory.getLogger(NotificationsChannelCommand.class);
    private final LeagueApi leagues;

    protected NotificationsChannelCommand(LeagueApi leagues, ExecutorService executor) {
        super(leagues, executor, log);
        this.leagues = leagues;
    }

    @Override
    public CommandDefinition definition() {
        var mode = new OptionData(
                OptionType.STRING,
                "mode",
                "Use the current channel or disable notifications",
                true
        )
                .addChoice("current", "current")
                .addChoice("off", "off");

        var subcommand = new SubcommandData(
                "channel",
                "Configure the notification channel"
        ).addOptions(mode);

        return new CommandDefinition(CommandRoot.SETTINGS, subcommand);
    }

    @Override
    protected String validateInput(CommandContext context) {
        var modeOption = context.event().getOption("mode");

        if (modeOption == null) {
            return "Choose current or off.";
        }

        var mode = modeOption.getAsString();

        if (!mode.equals("current") && !mode.equals("off")) {
            return "Unknown notification mode.";
        }

        return "";
    }

    @Override
    protected String executeWithResponse(CommandContext context) {
        var mode = Objects.requireNonNull(context.event().getOption("mode")).getAsString();

        var channelId = mode.equals("current")
                ? context.event().getChannel().getId()
                : null;
        leagues.updateNotificationsChannel(
                context.league().id(),
                channelId
        );

        return channelId == null
                ? "Notifications disabled."
                : "Notifications will be sent to this channel.";
    }
}
