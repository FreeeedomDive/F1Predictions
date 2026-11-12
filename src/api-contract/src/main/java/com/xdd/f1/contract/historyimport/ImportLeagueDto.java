package com.xdd.f1.contract.historyimport;

import com.xdd.f1.contract.league.LeagueSettingsDto;

import java.util.UUID;

public record ImportLeagueDto(UUID id, String name, String discordGuildId, LeagueSettingsDto settings) {
}
