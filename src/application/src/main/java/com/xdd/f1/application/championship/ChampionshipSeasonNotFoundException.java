package com.xdd.f1.application.championship;

public class ChampionshipSeasonNotFoundException extends RuntimeException {
    public ChampionshipSeasonNotFoundException(int season) {
        super("Championship is not configured for season " + season);
    }
}
