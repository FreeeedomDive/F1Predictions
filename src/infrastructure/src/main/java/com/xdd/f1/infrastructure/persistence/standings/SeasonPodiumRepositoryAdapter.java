package com.xdd.f1.infrastructure.persistence.standings;

import com.xdd.f1.application.standings.SeasonPodiumEntry;
import com.xdd.f1.application.standings.SeasonPodiumRepository;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class SeasonPodiumRepositoryAdapter implements SeasonPodiumRepository {
    private final SeasonPodiumJpaRepository repository;

    public SeasonPodiumRepositoryAdapter(SeasonPodiumJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SeasonPodiumEntry> findAllByLeagueBeforeSeason(LeagueId leagueId, int season) {
        return repository.findAllByLeagueBeforeSeason(leagueId.value(), season).stream().map(this::toDomain).toList();
    }

    @Override
    public void save(SeasonPodiumEntry podium) {
        var id = new SeasonPodiumEntityId(podium.leagueId().value(), podium.season());
        var entity = repository.findById(id).orElseGet(SeasonPodiumEntity::new);
        entity.id = id;
        entity.firstPlaceParticipantId = value(podium.firstPlaceParticipantId());
        entity.secondPlaceParticipantId = value(podium.secondPlaceParticipantId());
        entity.thirdPlaceParticipantId = value(podium.thirdPlaceParticipantId());
        repository.save(entity);
    }

    private SeasonPodiumEntry toDomain(SeasonPodiumEntity entity) {
        return new SeasonPodiumEntry(
                new LeagueId(entity.id.leagueId()),
                entity.id.season(),
                participantId(entity.firstPlaceParticipantId),
                participantId(entity.secondPlaceParticipantId),
                participantId(entity.thirdPlaceParticipantId)
        );
    }

    private UUID value(LeagueParticipantId participantId) {
        return participantId == null ? null : participantId.value();
    }

    private LeagueParticipantId participantId(UUID value) {
        return value == null ? null : new LeagueParticipantId(value);
    }
}
