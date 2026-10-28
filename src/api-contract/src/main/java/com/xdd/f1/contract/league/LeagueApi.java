package com.xdd.f1.contract.league;

import java.util.UUID;

public interface LeagueApi {
    LeagueDto ensureDiscordLeague(long guildId, String name);

    void updateNotificationsChannel(UUID leagueId, String channelId);
}
