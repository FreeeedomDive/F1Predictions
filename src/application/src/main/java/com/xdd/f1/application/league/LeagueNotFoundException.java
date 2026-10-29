package com.xdd.f1.application.league;

import com.xdd.f1.domain.league.LeagueId;

public class LeagueNotFoundException extends RuntimeException {
    public LeagueNotFoundException(LeagueId id) {
        super("League not found with id: " + id.value());
    }
}
