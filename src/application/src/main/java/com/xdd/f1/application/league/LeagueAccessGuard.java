package com.xdd.f1.application.league;

import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.user.UserId;
import org.springframework.stereotype.Service;

@Service
public class LeagueAccessGuard {

    private final LeagueParticipantRepository repository;

    public LeagueAccessGuard(LeagueParticipantRepository repository) {
        this.repository = repository;
    }

    public void checkAccess(UserId userId, LeagueId leagueId) {
        var leagueParticipant = repository.findByLeagueAndUser(leagueId, userId);
        if (leagueParticipant.isEmpty()){
            throw new LeagueParticipantNotFoundException(userId, leagueId);
        }
    }
}
