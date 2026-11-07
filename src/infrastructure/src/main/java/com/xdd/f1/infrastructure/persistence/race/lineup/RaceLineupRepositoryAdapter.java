package com.xdd.f1.infrastructure.persistence.race.lineup;

import com.xdd.f1.application.race.lineup.RaceLineupNotFoundException;
import com.xdd.f1.application.race.lineup.RaceLineupRepository;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.RaceLineup;
import com.xdd.f1.domain.race.lineup.Team;
import com.xdd.f1.domain.race.lineup.TeamBranding;
import jakarta.persistence.EntityManager;
import org.hibernate.query.TypedParameterValue;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class RaceLineupRepositoryAdapter implements RaceLineupRepository {
    private final RaceLineupJpaRepository repository;
    private final EntityManager entityManager;

    public RaceLineupRepositoryAdapter(RaceLineupJpaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<RaceLineup> findByRaceId(RaceId raceId) {
        return repository.findById(raceId.value()).map(this::toDomain);
    }

    @Override
    public Optional<RaceLineup> findLatestBefore(Instant closeAt) {
        return repository.findLatestBefore(closeAt).map(this::toDomain);
    }

    @Override
    public boolean createIfAbsent(RaceLineup lineup) {
        var teamsType = entityManager.getMetamodel().entity(RaceLineupEntity.class)
                .getSingularAttribute("teams", List.class).getType();
        var teams = TypedParameterValue.of(teamsType, toTeamData(lineup));
        return repository.createIfAbsent(lineup.raceId().value(), teams) == 1;
    }

    @Override
    public void update(RaceLineup lineup) {
        var entity = repository.findById(lineup.raceId().value())
                .orElseThrow(() -> new RaceLineupNotFoundException(lineup.raceId()));
        entity.teams = toTeamData(lineup);
    }

    private RaceLineup toDomain(RaceLineupEntity entity) {
        return new RaceLineup(
                new RaceId(entity.raceId),
                entity.teams.stream().map(this::toDomain).toList()
        );
    }

    private Team toDomain(RaceLineupTeamData data) {
        return new Team(
                new TeamBranding(data.name(), data.badge(), data.backgroundColor(), data.textColor()),
                data.drivers().stream().map(Driver::new).toList()
        );
    }

    private List<RaceLineupTeamData> toTeamData(RaceLineup lineup) {
        return lineup.teams().stream()
                .map(team -> new RaceLineupTeamData(
                        team.branding().name(),
                        team.branding().badge(),
                        team.branding().backgroundColor(),
                        team.branding().textColor(),
                        team.drivers().stream().map(Driver::name).toList()
                ))
                .toList();
    }
}
