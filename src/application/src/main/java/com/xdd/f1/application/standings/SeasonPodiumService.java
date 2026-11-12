package com.xdd.f1.application.standings;

import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.prediction.PredictionPointsRepository;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class SeasonPodiumService {
    private final LeagueRepository leagues;
    private final RaceRepository races;
    private final PredictionPointsRepository points;
    private final SeasonPodiumRepository podiums;

    public SeasonPodiumService(LeagueRepository leagues, RaceRepository races, PredictionPointsRepository points,
                               SeasonPodiumRepository podiums) {
        this.leagues = leagues;
        this.races = races;
        this.points = points;
        this.podiums = podiums;
    }

    @Transactional
    public void saveIfSeasonFinished(Race race) {
        var lastRace = races.findAllBySeason(race.season()).stream().max(SeasonStandingsService.EVENT_ORDER).orElse(null);
        if (lastRace == null || lastRace.status() != RaceStatus.FINISHED) {
            return;
        }
        leagues.findAll().forEach(league -> savePodium(league.id(), race.season()));
    }

    private void savePodium(LeagueId leagueId, int season) {
        var history = SeasonStandingsService.orderedHistory(points.findAllByLeagueAndSeason(leagueId, season));
        var rows = SeasonStandingsService.buildRows(history, Map.of());
        if (rows.isEmpty()) {
            return;
        }
        podiums.save(new SeasonPodiumEntry(
                leagueId,
                season,
                participantId(rows, 0),
                participantId(rows, 1),
                participantId(rows, 2)
        ));
    }

    private LeagueParticipantId participantId(List<SeasonStandingsRow> rows, int index) {
        return index < rows.size() ? rows.get(index).participantId() : null;
    }
}
