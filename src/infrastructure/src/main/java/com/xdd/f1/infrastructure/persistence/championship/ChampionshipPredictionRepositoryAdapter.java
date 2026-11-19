package com.xdd.f1.infrastructure.persistence.championship;

import com.xdd.f1.application.championship.ChampionshipPredictionRepository;
import com.xdd.f1.domain.championship.ChampionshipPrediction;
import com.xdd.f1.domain.championship.ChampionshipStage;
import com.xdd.f1.domain.championship.ChampionshipSubmissionSource;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ChampionshipPredictionRepositoryAdapter implements ChampionshipPredictionRepository {
    private final ChampionshipPredictionJpaRepository repository;

    public ChampionshipPredictionRepositoryAdapter(ChampionshipPredictionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ChampionshipPrediction> findByLeagueAndSeasonAndParticipantAndStage(
            LeagueId leagueId, int season, LeagueParticipantId participantId, ChampionshipStage stage
    ) {
        return repository.findById(new ChampionshipPredictionEntityId(leagueId.value(), season, participantId.value(), stage.name()))
                .map(this::toDomain);
    }

    @Override
    public List<ChampionshipPrediction> findAllBySeason(int season) {
        return repository.findAllByIdSeason(season).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ChampionshipPrediction> findAllByLeagueAndSeason(LeagueId leagueId, int season) {
        return repository.findAllByIdLeagueIdAndIdSeason(leagueId.value(), season).stream().map(this::toDomain).toList();
    }

    @Override
    public void save(ChampionshipPrediction prediction) {
        var id = new ChampionshipPredictionEntityId(prediction.leagueId().value(), prediction.season(),
                prediction.participantId().value(), prediction.stage().name());
        var entity = repository.findById(id).orElseGet(ChampionshipPredictionEntity::new);
        entity.id = id;
        entity.orderedDrivers = prediction.orderedDrivers();
        entity.submissionSource = prediction.submissionSource().name();
        repository.save(entity);
    }

    private ChampionshipPrediction toDomain(ChampionshipPredictionEntity entity) {
        return new ChampionshipPrediction(new LeagueId(entity.id.leagueId()), entity.id.season(),
                new LeagueParticipantId(entity.id.participantId()), ChampionshipStage.valueOf(entity.id.stage()),
                entity.orderedDrivers, ChampionshipSubmissionSource.valueOf(entity.submissionSource));
    }
}
