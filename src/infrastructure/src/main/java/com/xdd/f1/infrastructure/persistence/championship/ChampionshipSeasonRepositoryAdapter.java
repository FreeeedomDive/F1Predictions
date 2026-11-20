package com.xdd.f1.infrastructure.persistence.championship;

import com.xdd.f1.application.championship.ChampionshipSeasonNotFoundException;
import com.xdd.f1.application.championship.ChampionshipSeasonRepository;
import com.xdd.f1.domain.championship.ChampionshipDriverStandings;
import com.xdd.f1.domain.championship.ChampionshipRulesVersion;
import com.xdd.f1.domain.championship.ChampionshipSeason;
import com.xdd.f1.domain.championship.ChampionshipStage;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ChampionshipSeasonRepositoryAdapter implements ChampionshipSeasonRepository {
    private final ChampionshipSeasonJpaRepository repository;

    public ChampionshipSeasonRepositoryAdapter(ChampionshipSeasonJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ChampionshipSeason> findBySeason(int season) {
        return repository.findById(season).map(entity -> new ChampionshipSeason(
                entity.season, ChampionshipRulesVersion.fromValue(entity.rulesVersion),
                ChampionshipStage.valueOf(entity.stage), entity.stageStarted, entity.isOpen,
                entity.preSeasonRoster, entity.midSeasonRoster
        ));
    }

    @Override
    public void save(ChampionshipSeason season) {
        var entity = repository.findById(season.season()).orElseGet(ChampionshipSeasonEntity::new);
        entity.season = season.season();
        entity.rulesVersion = season.rulesVersion().value();
        entity.stage = season.stage().name();
        entity.stageStarted = season.stageStarted();
        entity.isOpen = season.isOpen();
        entity.preSeasonRoster = season.preSeasonRoster();
        entity.midSeasonRoster = season.midSeasonRoster();
        repository.save(entity);
    }

    @Override
    public Optional<ChampionshipDriverStandings> findDriverStandings(int season) {
        return repository.findById(season).filter(entity -> entity.driverStandings != null)
                .map(entity -> new ChampionshipDriverStandings(
                        entity.season, entity.driverStandings, entity.lastSyncedRound, entity.standingsUpdatedAt));
    }

    @Override
    public void saveDriverStandings(ChampionshipDriverStandings standings) {
        var entity = repository.findById(standings.season())
                .orElseThrow(() -> new ChampionshipSeasonNotFoundException(standings.season()));
        entity.driverStandings = standings.orderedDrivers();
        entity.lastSyncedRound = standings.lastSyncedRound();
        entity.standingsUpdatedAt = standings.updatedAt();
        repository.save(entity);
    }
}
