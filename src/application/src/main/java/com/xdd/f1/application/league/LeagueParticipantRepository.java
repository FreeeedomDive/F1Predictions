package com.xdd.f1.application.league;

import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.user.UserId;

import java.util.List;
import java.util.Optional;

public interface LeagueParticipantRepository {
    Optional<LeagueParticipant> findByLeagueAndUser(LeagueId leagueId, UserId userId);

    List<UserLeague> findLeaguesByUserId(UserId userId);

    void create(LeagueParticipant participant);

    LeagueParticipant updateDisplayName(LeagueParticipantId id, String newDisplayName);
}
