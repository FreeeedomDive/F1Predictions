package com.xdd.f1.infrastructure.persistence.race;

import java.time.Instant;
import java.util.UUID;

public interface RaceProjection {
    UUID getId();
    int getSeason();
    int getRound();
    String getType();
    String getName();
    String getStatus();
    Instant getOpenAt();
    Instant getCloseAt();
}
