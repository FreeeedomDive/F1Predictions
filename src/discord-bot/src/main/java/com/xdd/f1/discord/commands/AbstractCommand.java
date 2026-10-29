package com.xdd.f1.discord.commands;

import com.xdd.f1.contract.league.LeagueApi;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;

public abstract class AbstractCommand {

    private final LeagueApi leagues;
    private final ExecutorService executor;
    private final Logger log;

    protected abstract CommandDefinition definition();
    protected abstract String getErrorMessage(CommandContext context);
    protected abstract String executeWithResponse(CommandContext context);

    protected AbstractCommand(
            LeagueApi leagues,
            ExecutorService executor,
            Logger log
    ) {
        this.leagues = leagues;
        this.executor = executor;
        this.log = log;
    }

    public void execute(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        var member = event.getMember();

        if (guild == null || member == null) {
            event.reply("This command is only available in a server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        var guildId = guild.getIdLong();
        var guildName = guild.getName();
        event.deferReply(true).queue(hook ->
                        executor.execute(() -> {
                            try {
                                var league = leagues.ensureDiscordLeague(
                                        guildId,
                                        guildName
                                );
                                var context = new CommandContext(event, league);

                                var errorMessage = getErrorMessage(context);

                                if (!errorMessage.isEmpty()) {
                                    hook.editOriginal(errorMessage).queue();
                                    return;
                                }

                                var message = executeWithResponse(context);

                                hook.editOriginal(message).queue();
                            } catch (RuntimeException exception) {
                                var command = event.getFullCommandName();
                                log.error(
                                        "Failed to execute command {} in guild {}",
                                        command,
                                        guildName,
                                        exception
                                );

                                hook.editOriginal("Failed to execute command.").queue();
                            }
                        }),
                error -> log.error("Failed to acknowledge interaction", error)
        );
    }
}
