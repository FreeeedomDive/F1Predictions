package com.xdd.f1.application.league;

import com.xdd.f1.domain.league.*;
import com.xdd.f1.domain.user.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class LeagueService {
    private final LeagueRepository repository;
    private final LeagueParticipantRepository participantRepository;

    public LeagueService(
            LeagueRepository repository,
            LeagueParticipantRepository participantRepository
    ) {
        this.repository = repository;
        this.participantRepository = participantRepository;
    }

    @Transactional
    public League createManual(String name) {
        var league = new League(
                new LeagueId(UUID.randomUUID()),
                name,
                new LeagueSource.Manual(),
                new LeagueSettings(null),
                Instant.now()
        );
        repository.create(league);
        return league;
    }

    @Transactional
    public League ensureDiscordLeague(long guildId, String name) {
        var existing = repository.findByDiscordId(guildId);
        if (existing.isPresent()) {
            return existing.get();
        }

        var league = new League(
                new LeagueId(UUID.randomUUID()),
                name,
                new LeagueSource.Discord(guildId),
                new LeagueSettings(null),
                Instant.now()
        );
        repository.create(league);
        return league;
    }

    @Transactional(readOnly = true)
    public List<UserLeague> findLeaguesByUserId(UserId userId) {
        return participantRepository.findLeaguesByUserId(userId);
    }

    @Transactional
    public void updateNotificationsChannel(LeagueId id, String channelId) {
        repository.updateNotificationsChannel(id, channelId);
    }
}
