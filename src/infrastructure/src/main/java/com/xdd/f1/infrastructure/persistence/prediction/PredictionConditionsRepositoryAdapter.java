package com.xdd.f1.infrastructure.persistence.prediction;

import com.xdd.f1.application.prediction.PredictionConditionsNotFoundException;
import com.xdd.f1.application.prediction.PredictionConditionsRepository;
import com.xdd.f1.domain.prediction.PredictionConditions;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.lineup.Driver;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PredictionConditionsRepositoryAdapter implements PredictionConditionsRepository {
    private final PredictionConditionsJpaRepository repository;

    public PredictionConditionsRepositoryAdapter(PredictionConditionsJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PredictionConditions> findByRaceId(RaceId raceId) {
        return repository.findById(raceId.value()).map(entity -> new PredictionConditions(
                new RaceId(entity.raceId), entity.positionPredictionDriver == null ? null : new Driver(entity.positionPredictionDriver)
        ));
    }

    @Override
    public boolean createIfAbsent(PredictionConditions conditions) {
        var driver = conditions.positionPredictionDriver();
        return repository.createIfAbsent(conditions.raceId().value(), driver == null ? null : driver.name()) == 1;
    }

    @Override
    public void update(PredictionConditions conditions) {
        var entity = repository.findById(conditions.raceId().value())
                .orElseThrow(() -> new PredictionConditionsNotFoundException(conditions.raceId()));
        var driver = conditions.positionPredictionDriver();
        entity.positionPredictionDriver = driver == null ? null : driver.name();
    }
}
