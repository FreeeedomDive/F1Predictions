package com.xdd.f1.infrastructure.persistence.league;

import com.xdd.f1.application.league.LeagueNotFoundException;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.domain.league.*;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class LeagueRepositoryAdapter implements LeagueRepository {

    private final LeagueJpaRepository repository;

    public LeagueRepositoryAdapter(LeagueJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<League> findById(LeagueId id) {
        var league = repository.findById(id.value());
        return league.map(this::toDomain);
    }

    @Override
    public Optional<League> findByDiscordId(long guildId) {
        var league = repository.findBySourceAndSourceId(LeagueSourceType.DISCORD, String.valueOf(guildId));
        return league.map(this::toDomain);
    }

    @Override
    public List<League> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public void create(League league) {
        var entity = toEntity(league);
        repository.save(entity);
    }

    @Override
    public void updateNotificationsChannel(LeagueId id, String channelId) {
        var league = repository.findById(id.value()).orElseThrow(() -> new LeagueNotFoundException(id));
        league.settings.notificationsChannelId = channelId;
    }

    private League toDomain(LeagueEntity entity) {
        LeagueSource source = switch (entity.source) {
            case DISCORD -> new LeagueSource.Discord(Long.parseLong(entity.sourceId));
            case MANUAL -> new LeagueSource.Manual();
        };

        return new League(new LeagueId(entity.id), entity.name, source, new LeagueSettings(entity.settings.notificationsChannelId), entity.createdAt);
    }

    private LeagueEntity toEntity(League league) {
        LeagueSourceType sourceType = switch (league.source()) {
            case LeagueSource.Discord _ -> LeagueSourceType.DISCORD;
            case LeagueSource.Manual _ -> LeagueSourceType.MANUAL;
        };
        String sourceId = switch (league.source()) {
            case LeagueSource.Discord discord -> String.valueOf(discord.guildId());
            case LeagueSource.Manual _ -> league.id().value().toString();
        };

        var leagueEntity = new LeagueEntity();
        leagueEntity.id = league.id().value();
        leagueEntity.name = league.name();
        leagueEntity.source = sourceType;
        leagueEntity.sourceId = sourceId;
        leagueEntity.createdAt = league.createdAt();

        var leagueSettingsEntity = new LeagueSettingsEntity();
        leagueSettingsEntity.notificationsChannelId = league.settings().notificationsChannelId();

        leagueEntity.settings = leagueSettingsEntity;
        leagueSettingsEntity.league = leagueEntity;

        return leagueEntity;
    }
}
