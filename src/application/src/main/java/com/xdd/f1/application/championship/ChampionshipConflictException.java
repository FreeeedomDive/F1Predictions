package com.xdd.f1.application.championship;

public class ChampionshipConflictException extends RuntimeException {
    public ChampionshipConflictException(int season, String reason) {
        super("Championship season " + season + ": " + reason);
    }
}
