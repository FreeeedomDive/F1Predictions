package com.xdd.f1.infrastructure.persistence.race.result;

import com.xdd.f1.application.race.result.RaceResultNotFoundException;
import com.xdd.f1.application.race.result.RaceResultRepository;
import com.xdd.f1.domain.prediction.RulesVersion;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.result.*;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RaceResultRepositoryAdapter implements RaceResultRepository {
    private final RaceResultJpaRepository repository;

    public RaceResultRepositoryAdapter(RaceResultJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RaceResult> findByRaceId(RaceId raceId) {
        return repository.findById(raceId.value()).map(this::toDomain);
    }

    @Override
    public List<RaceId> findPublishedRaceIdsBySeason(int season) {
        return repository.findPublishedRaceIdsBySeason(season).stream().map(RaceId::new).toList();
    }

    @Override
    public List<RaceResult> findAllPublishedBySeason(int season) {
        return repository.findAllByRace_SeasonAndResultIsNotNull(season).stream().map(this::toDomain).toList();
    }

    @Override
    public void create(RaceResult result) {
        var entity = new RaceResultEntity();
        entity.raceId = result.raceId().value();
        updateEntity(entity, result);
        repository.save(entity);
    }

    @Override
    public void update(RaceResult result) {
        var entity = repository.findById(result.raceId().value())
                .orElseThrow(() -> new RaceResultNotFoundException(result.raceId()));
        updateEntity(entity, result);
    }

    private RaceResult toDomain(RaceResultEntity entity) {
        return new RaceResult(
                new RaceId(entity.raceId), new RulesVersion(entity.rulesVersion),
                toDomain(entity.result), toDomain(entity.draft),
                entity.startingGrid == null ? null : entity.startingGrid.stream().map(Driver::new).toList()
        );
    }

    private RaceResultValues toDomain(RaceResultValuesData values) {
        if (values == null) {
            return null;
        }
        var classification = values.classification() == null ? null : values.classification().stream()
                .map(entry -> new RaceClassificationEntry(new Driver(entry.driverName()),
                        RaceDriverStatus.valueOf(entry.status()))).toList();
        return new RaceResultValues(classification, values.safetyCars(), values.firstPlaceLead());
    }

    private RaceResultValuesData toData(RaceResultValues values) {
        if (values == null) {
            return null;
        }
        var classification = values.classification() == null ? null : values.classification().stream()
                .map(entry -> new RaceResultValuesData.ClassificationEntry(entry.driver().name(),
                        entry.status().name())).toList();
        return new RaceResultValuesData(classification, values.safetyCars(), values.firstPlaceLead());
    }

    private void updateEntity(RaceResultEntity entity, RaceResult result) {
        entity.rulesVersion = result.rulesVersion().value();
        entity.result = toData(result.result());
        entity.draft = toData(result.draft());
        entity.startingGrid = result.startingGrid() == null ? null : result.startingGrid().stream().map(Driver::name).toList();
    }
}
