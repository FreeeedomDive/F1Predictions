package com.xdd.f1.infrastructure.persistence.race.result;

import com.xdd.f1.application.race.result.RaceResultNotFoundException;
import com.xdd.f1.application.race.result.RaceResultRepository;
import com.xdd.f1.domain.prediction.RulesVersion;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.result.*;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class RaceResultRepositoryAdapter implements RaceResultRepository {
    private final RaceResultJpaRepository repository;

    public RaceResultRepositoryAdapter(RaceResultJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RaceResult> findByRaceId(RaceId raceId) {
        return repository.findById(raceId.value()).map(entity -> new RaceResult(
                new RaceId(entity.raceId), new RulesVersion(entity.rulesVersion),
                toDomain(entity.result), toDomain(entity.draft)
        ));
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

    private RaceResultValues toDomain(RaceResultValuesData values) {
        if (values == null) {
            return null;
        }
        var classification = values.classification() == null ? null : values.classification().stream()
                .map(Driver::new).toList();
        var statuses = values.driverStatuses() == null ? null : values.driverStatuses().entrySet().stream()
                .collect(Collectors.toMap(entry -> new Driver(entry.getKey()), entry -> RaceDriverStatus.valueOf(entry.getValue())));
        return new RaceResultValues(classification, statuses, values.safetyCars(), values.firstPlaceLead());
    }

    private RaceResultValuesData toData(RaceResultValues values) {
        if (values == null) {
            return null;
        }
        var classification = values.classification() == null ? null : values.classification().stream()
                .map(Driver::name).toList();
        var statuses = values.driverStatuses() == null ? null : values.driverStatuses().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().name(), entry -> entry.getValue().name()));
        return new RaceResultValuesData(classification, statuses, values.safetyCars(), values.firstPlaceLead());
    }

    private void updateEntity(RaceResultEntity entity, RaceResult result) {
        entity.rulesVersion = result.rulesVersion().value();
        entity.result = toData(result.result());
        entity.draft = toData(result.draft());
    }
}
