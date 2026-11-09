package com.xdd.f1.application.prediction;

import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.application.race.lineup.RaceLineupNotFoundException;
import com.xdd.f1.application.race.lineup.RaceLineupRepository;
import com.xdd.f1.domain.prediction.PredictionValidator;
import com.xdd.f1.domain.race.Race;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class PredictionConditionsService {
    private final PredictionConditionsRepository conditions;
    private final RaceLineupRepository lineups;
    private final TransactionLock lock;

    public PredictionConditionsService(PredictionConditionsRepository conditions, RaceLineupRepository lineups,
                                       TransactionLock lock) {
        this.conditions = conditions;
        this.lineups = lineups;
        this.lock = lock;
    }

    @Transactional
    public void ensureForRace(Race race) {
        lock.acquire("race:" + race.id().value());
        if (conditions.findByRaceId(race.id()).isPresent()) {
            return;
        }
        PredictionValidator.createConditions(race.season(), race.id(), () -> {
            var lineup = lineups.findByRaceId(race.id()).orElseThrow(() -> new RaceLineupNotFoundException(race.id()));
            var drivers = lineup.teams().stream().flatMap(team -> team.drivers().stream()).toList();
            return drivers.get(ThreadLocalRandom.current().nextInt(drivers.size()));
        }).ifPresent(conditions::createIfAbsent);
    }

}
