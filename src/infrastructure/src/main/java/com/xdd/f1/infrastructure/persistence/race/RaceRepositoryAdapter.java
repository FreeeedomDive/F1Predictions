package com.xdd.f1.infrastructure.persistence.race;

import com.xdd.f1.application.race.RaceNotFoundException;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.RaceType;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class RaceRepositoryAdapter implements RaceRepository {

    private final RaceJpaRepository repository;

    public RaceRepositoryAdapter(RaceJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Race> findById(RaceId id) {
        return repository.findProjectedById(id.value()).map(this::toDomain);
    }

    @Override
    public List<Race> findAllBySeason(int season) {
        return repository.findAllBySeasonOrderByOpenAtAsc(season).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Race> findFinishedBySeason(int season) {
        return repository.findAllBySeasonAndStatusOrderByOpenAtAsc(season, RaceStatus.FINISHED.name())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Race> findRacesToOpen(Instant now) {
        return repository.findAllByStatusAndOpenAtLessThanEqualOrderByOpenAtAsc(RaceStatus.SCHEDULED.name(), now)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Race> findRacesToClose(Instant now) {
        return repository.findAllByStatusAndCloseAtLessThanEqualOrderByCloseAtAsc(RaceStatus.OPEN.name(), now)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Race> findRacesToSyncStartingGrid(Instant now) {
        return repository.findAllByStatusInAndOpenAtLessThanEqualAndCloseAtGreaterThanOrderByCloseAtAsc(
                        List.of(RaceStatus.OPEN.name(), RaceStatus.CLOSED.name()), now, now)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void create(Race race) {
        repository.save(toEntity(race));
    }

    @Override
    public void update(Race race) {
        var entity = repository.findById(race.id().value())
                .orElseThrow(() -> new RaceNotFoundException(race.id()));
        updateEntity(entity, race);
    }

    private Race toDomain(RaceProjection entity) {
        return new Race(
                new RaceId(entity.getId()),
                entity.getSeason(),
                entity.getRound(),
                RaceType.valueOf(entity.getType()),
                entity.getName(),
                RaceStatus.valueOf(entity.getStatus()),
                entity.getOpenAt(),
                entity.getCloseAt()
        );
    }

    private RaceEntity toEntity(Race race) {
        var entity = new RaceEntity();
        entity.id = race.id().value();
        updateEntity(entity, race);
        return entity;
    }

    private void updateEntity(RaceEntity entity, Race race) {
        entity = Hibernate.unproxy(entity, RaceEntity.class);
        entity.season = race.season();
        entity.round = race.round();
        entity.type = race.type().name();
        entity.name = race.name();
        entity.status = race.status().name();
        entity.openAt = race.openAt();
        entity.closeAt = race.closeAt();
    }
}
