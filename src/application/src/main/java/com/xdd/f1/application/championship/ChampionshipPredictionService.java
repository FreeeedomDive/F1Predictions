package com.xdd.f1.application.championship;

import com.xdd.f1.application.league.LeagueNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantRepository;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.application.race.RaceNotFoundException;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.application.race.lineup.RaceLineupNotFoundException;
import com.xdd.f1.application.race.lineup.RaceLineupRepository;
import com.xdd.f1.domain.championship.ChampionshipPrediction;
import com.xdd.f1.domain.championship.ChampionshipRulesVersion;
import com.xdd.f1.domain.championship.ChampionshipSeason;
import com.xdd.f1.domain.championship.ChampionshipStage;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.League;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.domain.race.lineup.Driver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static com.xdd.f1.domain.championship.ChampionshipPredictionSource.PRE_SEASON_COPY;
import static com.xdd.f1.domain.championship.ChampionshipPredictionSource.USER;
import static com.xdd.f1.domain.championship.ChampionshipStage.MID_SEASON;
import static com.xdd.f1.domain.championship.ChampionshipStage.PRE_SEASON;

@Service
public class ChampionshipPredictionService {
    private final ChampionshipSeasonRepository seasons;
    private final ChampionshipPredictionRepository predictions;
    private final LeagueRepository leagues;
    private final LeagueParticipantRepository participants;
    private final RaceRepository races;
    private final RaceLineupRepository lineups;
    private final TransactionLock lock;

    public ChampionshipPredictionService(ChampionshipSeasonRepository seasons, ChampionshipPredictionRepository predictions,
                                         LeagueRepository leagues, LeagueParticipantRepository participants,
                                         RaceRepository races, RaceLineupRepository lineups, TransactionLock lock) {
        this.seasons = seasons;
        this.predictions = predictions;
        this.leagues = leagues;
        this.participants = participants;
        this.races = races;
        this.lineups = lineups;
        this.lock = lock;
    }

    @Transactional
    public ChampionshipSeason configure(int season, ChampionshipRulesVersion rulesVersion) {
        lock.acquire("championship:" + season);
        var existing = seasons.findBySeason(season);
        if (existing.isPresent()) {
            if (existing.get().rulesVersion() != rulesVersion) {
                throw new ChampionshipConflictException(season, "rules version is already set");
            }
            return existing.get();
        }
        var configured = new ChampionshipSeason(season, rulesVersion, PRE_SEASON, false, false, List.of(), List.of());
        seasons.save(configured);
        return configured;
    }

    @Transactional
    public ChampionshipSeason openStage(int season, ChampionshipStage stage, RaceId lineupRaceId) {
        lock.acquire("championship:" + season);
        var current = getSeason(season);
        if (current.stage() == stage && current.isOpen()) {
            return current;
        }
        var roster = current.roster(stage);
        if (roster.isEmpty()) {
            roster = loadRoster(season, lineupRaceId);
        }
        var opened = new ChampionshipSeason(season, current.rulesVersion(), stage, true, true,
                stage == PRE_SEASON ? roster : current.preSeasonRoster(),
                stage == MID_SEASON ? roster : current.midSeasonRoster());
        return saveStageState(current, opened);
    }

    private List<String> loadRoster(int season, RaceId lineupRaceId) {
        if (lineupRaceId == null) {
            throw new DomainValidationException("A race lineup is required to open a stage without a driver list");
        }
        var lineupRace = races.findById(lineupRaceId).orElseThrow(() -> new RaceNotFoundException(lineupRaceId));
        if (lineupRace.season() != season || lineupRace.type() != RaceType.RACE) {
            throw new DomainValidationException("Championship roster must come from a race in the same season");
        }
        var lineup = lineups.findByRaceId(lineupRaceId).orElseThrow(() -> new RaceLineupNotFoundException(lineupRaceId));
        return lineup.teams().stream().flatMap(team -> team.drivers().stream()).map(Driver::name).toList();
    }

    @Transactional
    public ChampionshipSeason closeStage(int season, ChampionshipStage stage) {
        lock.acquire("championship:" + season);
        var current = getSeason(season);
        if (current.stage() == stage && !current.isOpen()) {
            return current;
        }
        var closed = new ChampionshipSeason(season, current.rulesVersion(), stage, true, false,
                current.preSeasonRoster(), current.midSeasonRoster());
        return saveStageState(current, closed);
    }

    private ChampionshipSeason saveStageState(ChampionshipSeason current, ChampionshipSeason next) {
        var wasPublished = current.isPublished(MID_SEASON);
        var willBePublished = next.isPublished(MID_SEASON);
        if (wasPublished && !willBePublished) {
            predictions.deletePreSeasonCopies(current.season());
        } else if (!wasPublished && willBePublished) {
            copyPreSeasonPredictions(current.season());
        }
        seasons.save(next);
        return next;
    }

    @Transactional
    public ChampionshipPrediction save(LeagueId leagueId, int season, LeagueParticipantId participantId,
                                       ChampionshipStage stage, List<String> orderedDrivers) {
        lock.acquire("championship:" + season);
        participants.findByLeagueAndId(leagueId, participantId)
                .orElseThrow(() -> new LeagueParticipantNotFoundException(participantId));
        var league = leagues.findById(leagueId).orElseThrow(() -> new LeagueNotFoundException(leagueId));
        if (season < league.settings().firstSeason()) {
            throw new ChampionshipSeasonNotFoundException(season);
        }
        var current = seasons.findBySeason(season).orElseThrow(() -> new ChampionshipPredictionsClosedException(season, stage));
        if (current.stage() != stage || !current.isOpen()) {
            throw new ChampionshipPredictionsClosedException(season, stage);
        }
        var roster = current.roster(stage);
        if (orderedDrivers == null || orderedDrivers.isEmpty() || orderedDrivers.size() != roster.size()
                || !new HashSet<>(orderedDrivers).equals(new HashSet<>(roster))) {
            throw new DomainValidationException("Championship prediction must rank every driver in the stage roster exactly once");
        }
        var prediction = new ChampionshipPrediction(leagueId, season, participantId, stage, orderedDrivers, USER);
        predictions.save(prediction);
        return prediction;
    }

    @Transactional(readOnly = true)
    public List<Race> findStageRosterRaces(int season) {
        return races.findBySeasonAndTypeWithSavedLineup(season, RaceType.RACE);
    }

    @Transactional(readOnly = true)
    public List<ChampionshipPredictionStatus> findPredictionStatuses(LeagueId leagueId, int season) {
        var league = leagues.findById(leagueId).orElseThrow(() -> new LeagueNotFoundException(leagueId));
        if (season < league.settings().firstSeason()) {
            return List.of();
        }
        var leaguePredictions = predictions.findAllByLeagueAndSeason(leagueId, season);
        var preSeasonParticipants = leaguePredictions.stream()
                .filter(prediction -> prediction.stage() == PRE_SEASON && !prediction.orderedDrivers().isEmpty())
                .map(ChampionshipPrediction::participantId).collect(Collectors.toSet());
        var midSeasonSources = leaguePredictions.stream().filter(prediction -> prediction.stage() == MID_SEASON)
                .collect(Collectors.toMap(ChampionshipPrediction::participantId, ChampionshipPrediction::predictionSource));
        return participants.findAllByLeague(leagueId).stream().map(participant -> new ChampionshipPredictionStatus(
                participant.id(), participant.displayName(), preSeasonParticipants.contains(participant.id()),
                midSeasonSources.get(participant.id())
        )).toList();
    }

    private ChampionshipSeason getSeason(int season) {
        return seasons.findBySeason(season).orElseThrow(() -> new ChampionshipSeasonNotFoundException(season));
    }

    private void copyPreSeasonPredictions(int season) {
        var eligibleLeagues = leagues.findAll().stream()
                .filter(league -> season >= league.settings().firstSeason())
                .map(League::id).collect(Collectors.toSet());
        var seasonPredictions = predictions.findAllBySeason(season);
        var midSeasonParticipants = seasonPredictions.stream().filter(prediction -> prediction.stage() == MID_SEASON)
                .map(ChampionshipPrediction::participantId).collect(Collectors.toSet());
        for (var preSeason : seasonPredictions) {
            if (preSeason.stage() != PRE_SEASON || preSeason.orderedDrivers().isEmpty()
                    || preSeason.predictionSource() != USER
                    || !eligibleLeagues.contains(preSeason.leagueId())
                    || midSeasonParticipants.contains(preSeason.participantId())) {
                continue;
            }
            predictions.save(new ChampionshipPrediction(preSeason.leagueId(), season, preSeason.participantId(),
                    MID_SEASON, preSeason.orderedDrivers(), PRE_SEASON_COPY));
        }
    }
}
