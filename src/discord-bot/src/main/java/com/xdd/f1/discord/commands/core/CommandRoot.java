package com.xdd.f1.discord.commands.core;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;

public record CommandRoot(String name, String description, DefaultMemberPermissions permissions) {

    public static final CommandRoot SETTINGS = new CommandRoot(
            "f1settings",
            "Configure F1 predictions",
            DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)
    );

    public static final CommandRoot F1 = new CommandRoot(
            "f1",
            "Formula 1 predictions",
            DefaultMemberPermissions.ENABLED
    );

}
