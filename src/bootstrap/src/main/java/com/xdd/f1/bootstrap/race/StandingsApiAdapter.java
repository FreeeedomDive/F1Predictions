package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.standings.SeasonStandingsService;
import com.xdd.f1.contract.standings.SeasonStandingsDto;
import com.xdd.f1.contract.standings.StandingsApi;
import com.xdd.f1.domain.league.LeagueId;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StandingsApiAdapter implements StandingsApi {
    private final SeasonStandingsService service;

    public StandingsApiAdapter(SeasonStandingsService service) {
        this.service = service;
    }

    @Override
    public SeasonStandingsDto getStandings(UUID leagueId, int season) {
        return SeasonStandingsDtoMapper.toDto(service.getStandings(new LeagueId(leagueId), season));
    }
}
