package com.xdd.f1.discord.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;

public record CommandRoot(String name, String description, DefaultMemberPermissions permissions) {

    public static final CommandRoot SETTINGS = new CommandRoot(
            "f1settings",
            "Configure F1 predictions",
            DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)
    );

}
