package com.xdd.f1.application.league;

import com.xdd.f1.domain.league.League;
import com.xdd.f1.domain.league.LeagueId;

import java.util.List;
import java.util.Optional;

public interface LeagueRepository {
    Optional<League> findById(LeagueId id);
    Optional<League> findByDiscordId(long guildId);
    List<League> findAll();
    void create(League league);
    void updateNotificationsChannel(LeagueId id, String channelId);
}
