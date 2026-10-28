package com.xdd.f1.domain.league;

public sealed interface LeagueSource permits
        LeagueSource.Discord,
        LeagueSource.Manual {
    record Discord(long guildId) implements LeagueSource {
    }

    record Manual() implements LeagueSource {
    }
}
