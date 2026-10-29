package com.xdd.f1.application.league;

import com.xdd.f1.domain.league.LeagueParticipantId;

public class LeagueParticipantNotFoundException extends RuntimeException{
    public LeagueParticipantNotFoundException(LeagueParticipantId id) {
        super("League participant not found with id: " + id.value());
    }
}
