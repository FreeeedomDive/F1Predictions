package com.xdd.f1.discord.commands;

import com.xdd.f1.contract.league.LeagueDto;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public record CommandContext(SlashCommandInteractionEvent event, LeagueDto league) {
}
