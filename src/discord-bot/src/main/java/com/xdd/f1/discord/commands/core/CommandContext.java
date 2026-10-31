package com.xdd.f1.discord.commands.core;

import com.xdd.f1.contract.league.LeagueDto;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.entities.Member;

public record CommandContext(SlashCommandInteractionEvent event, LeagueDto league, Member member) {
}
