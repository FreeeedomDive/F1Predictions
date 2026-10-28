package com.xdd.f1.discord.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandListener extends ListenerAdapter {

    private final Map<String, AbstractCommand> commandMap;
    private static final Logger log =
            LoggerFactory.getLogger(CommandListener.class);

    public CommandListener(List<AbstractCommand> commands) {
        var map = new HashMap<String, AbstractCommand>();
        for (var command : commands) {
            var path = command.definition().path();

            if (map.putIfAbsent(path, command) != null) {
                throw new IllegalStateException("Duplicate command path: " + path);
            }
        }
        this.commandMap = Map.copyOf(map);
    }

    @Override
    public void onReady(@NonNull ReadyEvent event) {
        var definitionsByRoot = commandMap.values().stream()
                .map(AbstractCommand::definition)
                .collect(Collectors.groupingBy(CommandDefinition::root));
        for (var entry : definitionsByRoot.entrySet()) {
            var root = entry.getKey();

            var subcommands = entry.getValue().stream()
                    .map(CommandDefinition::subcommand)
                    .toList();

            var slashCommand = Commands.slash(root.name(), root.description())
                    .setContexts(InteractionContextType.GUILD)
                    .setDefaultPermissions(root.permissions())
                    .addSubcommands(subcommands);

            event.getJDA().upsertCommand(slashCommand).queue(
                    _ -> log.info("Registered {} command", slashCommand.getName()),
                    error -> log.error(
                            "Failed to register {} command",
                            slashCommand.getName(),
                            error
                    )
            );
        }
    }

    @Override
    public void onSlashCommandInteraction(@NonNull SlashCommandInteractionEvent event) {
        var executedCommand = event.getFullCommandName();
        var command = commandMap.get(executedCommand);
        if (command == null) {
            event.reply("Unknown command").setEphemeral(true).queue();
            return;
        }
        command.execute(event);
    }
}
