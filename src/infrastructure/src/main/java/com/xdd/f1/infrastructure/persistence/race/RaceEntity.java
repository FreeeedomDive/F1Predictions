package com.xdd.f1.infrastructure.persistence.race;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "races")
public class RaceEntity {
    @Id
    @Column(name = "id", nullable = false)
    UUID id;

    @Column(name = "season", nullable = false)
    int season;

    @Column(name = "round", nullable = false)
    int round;

    @Column(name = "type", nullable = false)
    String type;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "status", nullable = false)
    String status;

    @Column(name = "open_at", nullable = false)
    Instant openAt;

    @Column(name = "close_at", nullable = false)
    Instant closeAt;

    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
