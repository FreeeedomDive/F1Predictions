package com.xdd.f1.infrastructure.persistence.prediction;

import com.xdd.f1.application.prediction.PredictionPointsNotFoundException;
import com.xdd.f1.application.prediction.PredictionPointsRepository;
import com.xdd.f1.domain.prediction.PredictionId;
import com.xdd.f1.domain.prediction.PredictionPoints;
import com.xdd.f1.domain.prediction.RulesVersion;
import com.xdd.f1.domain.race.RaceId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PredictionPointsRepositoryAdapter implements PredictionPointsRepository {
    private final PredictionPointsJpaRepository repository;

    public PredictionPointsRepositoryAdapter(PredictionPointsJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PredictionPoints> findByPredictionId(PredictionId predictionId) {
        return repository.findById(predictionId.value()).map(this::toDomain);
    }

    @Override
    public List<PredictionPoints> findAllByRaceId(RaceId raceId) {
        return repository.findAllByRaceId(raceId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public void create(PredictionPoints points) {
        var entity = new PredictionPointsEntity();
        entity.predictionId = points.predictionId().value();
        updateEntity(entity, points);
        repository.save(entity);
    }

    @Override
    public void update(PredictionPoints points) {
        var entity = repository.findById(points.predictionId().value())
                .orElseThrow(() -> new PredictionPointsNotFoundException(points.predictionId()));
        updateEntity(entity, points);
    }

    private PredictionPoints toDomain(PredictionPointsEntity entity) {
        return new PredictionPoints(
                new PredictionId(entity.predictionId), new RulesVersion(entity.rulesVersion), entity.totalPoints,
                entity.tenthPlacePoints, entity.dnfsPoints, entity.safetyCarsPoints,
                entity.firstPlaceLeadPoints, entity.driverPositionPoints, entity.teamMatesPoints
        );
    }

    private void updateEntity(PredictionPointsEntity entity, PredictionPoints points) {
        entity.rulesVersion = points.rulesVersion().value();
        entity.totalPoints = points.totalPoints();
        entity.tenthPlacePoints = points.tenthPlacePoints();
        entity.dnfsPoints = points.dnfsPoints();
        entity.safetyCarsPoints = points.safetyCarsPoints();
        entity.firstPlaceLeadPoints = points.firstPlaceLeadPoints();
        entity.driverPositionPoints = points.driverPositionPoints();
        entity.teamMatesPoints = points.teamMatesPoints();
    }
}
