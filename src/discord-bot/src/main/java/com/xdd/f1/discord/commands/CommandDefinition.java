package com.xdd.f1.discord.commands;

import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

public record CommandDefinition(CommandRoot root, SubcommandData subcommand) {

    public String path() {
        return root.name() + " " + subcommand.getName();
    }

}
