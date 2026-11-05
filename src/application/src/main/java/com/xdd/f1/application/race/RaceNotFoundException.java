package com.xdd.f1.application.race;

import com.xdd.f1.domain.race.RaceId;

public class RaceNotFoundException extends RuntimeException{

    public RaceNotFoundException(RaceId raceId){
        super("Race with id " + raceId + " not found");
    }
}
