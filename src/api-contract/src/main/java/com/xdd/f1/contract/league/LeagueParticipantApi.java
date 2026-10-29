package com.xdd.f1.contract.league;

import java.util.UUID;

public interface LeagueParticipantApi {
    LeagueParticipantDto ensureDiscordParticipant(UUID leagueId, long discordUserId, String displayName);
}
