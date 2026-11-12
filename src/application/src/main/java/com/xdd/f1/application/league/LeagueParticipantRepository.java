package com.xdd.f1.application.league;

import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.user.UserId;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LeagueParticipantRepository {
    Optional<LeagueParticipant> findById(LeagueParticipantId id);

    Optional<LeagueParticipant> findByLeagueAndUser(LeagueId leagueId, UserId userId);

    List<UserLeague> findLeaguesByUserId(UserId userId);

    Map<LeagueParticipantId, String> findNamesByIds(List<LeagueParticipantId> ids);

    void create(LeagueParticipant participant);

    LeagueParticipant updateDisplayName(LeagueParticipantId id, String newDisplayName);
}
