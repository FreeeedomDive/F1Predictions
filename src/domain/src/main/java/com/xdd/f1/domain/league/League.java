package com.xdd.f1.domain.league;

import java.time.Instant;

public record League(LeagueId id, String name, LeagueSource source, LeagueSettings settings, Instant createdAt) {
}
