package com.xdd.f1.application.race.lineup;

import com.xdd.f1.application.race.RaceNotFoundException;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.application.prediction.PredictionRepository;
import com.xdd.f1.application.prediction.PredictionConditionsRepository;
import com.xdd.f1.contract.race.DriverReplacementDto;
import com.xdd.f1.contract.race.RaceDriversReplacedEvent;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.DriverReplacement;
import com.xdd.f1.domain.race.lineup.RaceLineup;
import com.xdd.f1.domain.race.lineup.Team;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class RaceLineupService {
    private final RaceRepository raceRepository;
    private final RaceLineupRepository lineupRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final TransactionLock lock;
    private final PredictionRepository predictions;
    private final PredictionConditionsRepository conditions;

    public RaceLineupService(
            RaceRepository raceRepository,
            RaceLineupRepository lineupRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            TransactionLock lock,
            PredictionRepository predictions,
            PredictionConditionsRepository conditions
    ) {
        this.raceRepository = raceRepository;
        this.lineupRepository = lineupRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.lock = lock;
        this.predictions = predictions;
        this.conditions = conditions;
    }

    @Transactional(readOnly = true)
    public Optional<RaceLineupEditor> findTarget(int season) {
        var races = raceRepository.findAllBySeason(season);
        var chronological = Comparator.comparing(Race::closeAt).thenComparing(race -> race.id().value());
        var now = clock.instant();
        var target = races.stream()
                .filter(race -> race.status() == RaceStatus.OPEN)
                .min(chronological)
                .or(() -> races.stream()
                        .filter(race -> race.status() == RaceStatus.SCHEDULED && race.closeAt().isAfter(now))
                        .min(chronological));
        return target.map(this::toEditor);
    }

    @Transactional(readOnly = true)
    public RaceLineupEditor getForEditing(RaceId raceId) {
        var race = raceRepository.findById(raceId).orElseThrow(() -> new RaceNotFoundException(raceId));
        return toEditor(race);
    }

    @Transactional
    public RaceLineup addTeam(RaceId raceId, Team team) {
        return saveTeam(getEditableRace(raceId), Optional.empty(), team);
    }

    @Transactional
    public RaceLineup updateTeam(RaceId raceId, String previousName, Team team) {
        return saveTeam(getEditableRace(raceId), Optional.of(previousName), team);
    }

    @Transactional
    public RaceLineup swapDrivers(RaceId raceId, Driver firstDriver, Driver secondDriver) {
        var race = getEditableRace(raceId);
        ensureForRace(race);
        var current = lineupRepository.findByRaceId(raceId)
                .orElseThrow(() -> new RaceLineupNotFoundException(raceId));
        var updated = current.swapDrivers(firstDriver, secondDriver);
        if (!current.equals(updated)) {
            lineupRepository.update(updated);
            for (var team : updated.teams()) {
                var replacements = current.findTeam(team.branding().name()).orElseThrow().driverReplacementsTo(team);
                publishReplacements(race, team, replacements);
            }
        }
        return updated;
    }

    @Transactional
    public void ensureForRace(Race race) {
        lock.acquire("race:" + race.id().value());
        if (lineupRepository.findByRaceId(race.id()).isPresent()) {
            return;
        }

        var previous = lineupRepository.findLatestBefore(race.closeAt())
                .orElseThrow(() -> new RaceLineupNotPreparedException(race.id()));
        lineupRepository.createIfAbsent(new RaceLineup(race.id(), previous.teams()));
    }

    private RaceLineup saveTeam(Race race, Optional<String> previousName, Team team) {
        if (team.branding().badge().length() > 3) {
            throw new DomainValidationException("Team badge must not exceed 3 characters");
        }
        var saved = lineupRepository.findByRaceId(race.id());
        var current = saved.or(() -> lineupRepository.findLatestBefore(race.closeAt())
                .map(previous -> new RaceLineup(race.id(), previous.teams())));
        var updated = applyTeamChange(race.id(), current, previousName, team);

        if (saved.isEmpty()) {
            if (lineupRepository.createIfAbsent(updated)) {
                completeTeamChange(race, current, previousName, team);
                return updated;
            }
            current = Optional.of(lineupRepository.findByRaceId(race.id())
                    .orElseThrow(() -> new RaceLineupNotFoundException(race.id())));
            updated = applyTeamChange(race.id(), current, previousName, team);
        }

        if (!current.orElseThrow().equals(updated)) {
            lineupRepository.update(updated);
            completeTeamChange(race, current, previousName, team);
        }
        return updated;
    }

    private RaceLineup applyTeamChange(RaceId raceId, Optional<RaceLineup> current, Optional<String> previousName, Team team) {
        if (previousName.isPresent()) {
            return current.orElseThrow(() -> new RaceLineupNotPreparedException(raceId))
                    .updateTeam(previousName.get(), team);
        }
        return current.map(lineup -> lineup.addTeam(team))
                .orElseGet(() -> new RaceLineup(raceId, List.of(team)));
    }

    private void completeTeamChange(Race race, Optional<RaceLineup> current, Optional<String> previousName, Team team) {
        var replacements = current.flatMap(lineup -> previousName.flatMap(lineup::findTeam))
                .map(previous -> previous.driverReplacementsTo(team))
                .orElse(List.of());
        if (replacements.isEmpty()) {
            return;
        }
        for (var prediction : predictions.findAllByRaceId(race.id())) {
            var updated = prediction.replaceDrivers(replacements);
            if (!updated.equals(prediction)) {
                predictions.update(updated);
            }
        }
        conditions.findByRaceId(race.id()).ifPresent(currentConditions -> {
            var updated = currentConditions.replaceDrivers(replacements);
            if (!updated.equals(currentConditions)) {
                conditions.update(updated);
            }
        });
        publishReplacements(race, team, replacements);
    }

    private void publishReplacements(Race race, Team team, List<DriverReplacement> replacements) {
        if (replacements.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new RaceDriversReplacedEvent(
                race.id().value(),
                race.name(),
                race.type().name(),
                team.branding().name(),
                team.branding().backgroundColor(),
                replacements.stream()
                        .map(replacement -> new DriverReplacementDto(
                                replacement.previousDriver().name(), replacement.newDriver().name()
                        ))
                        .toList()
        ));
    }

    private Race getEditableRace(RaceId raceId) {
        lock.acquire("race:" + raceId.value());
        var race = raceRepository.findById(raceId).orElseThrow(() -> new RaceNotFoundException(raceId));
        if (race.status() != RaceStatus.SCHEDULED && race.status() != RaceStatus.OPEN) {
            throw new RaceLineupNotEditableException(race.id(), race.status());
        }
        return race;
    }

    private RaceLineupEditor toEditor(Race race) {
        var saved = lineupRepository.findByRaceId(race.id());
        var teams = saved.or(() -> lineupRepository.findLatestBefore(race.closeAt()))
                .map(RaceLineup::teams)
                .orElse(List.of());
        return new RaceLineupEditor(race, teams, saved.isPresent());
    }
}
