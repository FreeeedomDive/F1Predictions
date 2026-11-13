package com.xdd.f1.infrastructure.persistence.prediction;

import com.xdd.f1.application.prediction.PredictionNotFoundException;
import com.xdd.f1.application.prediction.PredictionRepository;
import com.xdd.f1.application.statistics.SeasonStatsPrediction;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.prediction.*;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.lineup.Driver;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PredictionRepositoryAdapter implements PredictionRepository {
    private final PredictionJpaRepository repository;

    public PredictionRepositoryAdapter(PredictionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Prediction> findByLeagueAndRaceAndParticipant(LeagueId leagueId, RaceId raceId, LeagueParticipantId participantId) {
        return repository.findByLeagueIdAndRaceIdAndParticipantId(leagueId.value(), raceId.value(), participantId.value())
                .map(this::toDomain);
    }

    @Override
    public List<Prediction> findAllByRaceId(RaceId raceId) {
        return repository.findAllByRaceId(raceId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<SeasonStatsPrediction> findSeasonStatistics(LeagueId leagueId, int season) {
        return repository.findSeasonStatistics(leagueId.value(), season).stream()
                .map(row -> new SeasonStatsPrediction(toDomain(row.getPrediction()), row.getParticipantName(), row.getTenthPlacePoints()))
                .toList();
    }

    @Override
    public void create(Prediction prediction) {
        var entity = new PredictionEntity();
        entity.id = prediction.id().value();
        updateEntity(entity, prediction);
        repository.save(entity);
    }

    @Override
    public void update(Prediction prediction) {
        var entity = repository.findById(prediction.id().value())
                .orElseThrow(() -> new PredictionNotFoundException(prediction.id()));
        updateEntity(entity, prediction);
    }

    private Prediction toDomain(PredictionEntity entity) {
        entity = Hibernate.unproxy(entity, PredictionEntity.class);
        return new Prediction(
                new PredictionId(entity.id),
                new LeagueId(entity.leagueId),
                new LeagueParticipantId(entity.participantId),
                new RaceId(entity.raceId),
                new PredictionValues(
                        entity.tenthPlacePickedDriver == null ? null : new Driver(entity.tenthPlacePickedDriver),
                        entity.dnfPickedDrivers == null ? null
                                : new DnfPrediction(entity.dnfPickedDrivers.stream().map(Driver::new).toList()),
                        entity.safetyCarsPrediction == null ? null : SafetyCarsCount.valueOf(entity.safetyCarsPrediction),
                        entity.driverPositionPrediction,
                        entity.firstPlaceLeadPrediction
                ),
                entity.teamsPickedDrivers
        );
    }

    private void updateEntity(PredictionEntity entity, Prediction prediction) {
        entity = Hibernate.unproxy(entity, PredictionEntity.class);
        entity.leagueId = prediction.leagueId().value();
        entity.participantId = prediction.participantId().value();
        entity.raceId = prediction.raceId().value();
        var values = prediction.values();
        entity.tenthPlacePickedDriver = values.tenthPlacePickedDriver() == null ? null : values.tenthPlacePickedDriver().name();
        entity.dnfPickedDrivers = values.dnfPrediction() == null ? null
                : values.dnfPrediction().dnfPickedDrivers().stream().map(Driver::name).toList();
        entity.safetyCarsPrediction = values.safetyCarsPrediction() == null ? null : values.safetyCarsPrediction().name();
        entity.driverPositionPrediction = values.driverPositionPrediction();
        entity.firstPlaceLeadPrediction = values.firstPlaceLeadPrediction();
        entity.teamsPickedDrivers = prediction.teamsPickedDrivers();
    }
}
