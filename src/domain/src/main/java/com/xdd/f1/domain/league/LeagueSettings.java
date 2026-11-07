package com.xdd.f1.domain.league;

import com.xdd.f1.domain.error.DomainValidationException;

public record LeagueSettings(String notificationsChannelId, boolean sprintsEnabled, int firstSeason) {

    public LeagueSettings {
        if (firstSeason <= 0) {
            throw new DomainValidationException("First season must be greater than zero");
        }
    }
}
