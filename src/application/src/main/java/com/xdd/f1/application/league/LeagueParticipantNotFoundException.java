package com.xdd.f1.application.league;

import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.user.UserId;

public class LeagueParticipantNotFoundException extends RuntimeException{
    public LeagueParticipantNotFoundException(LeagueParticipantId id) {
        super("League participant not found with id: " + id.value());
    }

    public LeagueParticipantNotFoundException(UserId userId, LeagueId leagueId){
        super("League participant not found with userId " + userId.value() + " and leagueId " + leagueId.value());
    }
}
