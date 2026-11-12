package com.xdd.f1.infrastructure.persistence.standings;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "season_podiums")
public class SeasonPodiumEntity {
    @EmbeddedId
    SeasonPodiumEntityId id;

    @Column(name = "first_place_participant_id", nullable = false)
    UUID firstPlaceParticipantId;

    @Column(name = "second_place_participant_id")
    UUID secondPlaceParticipantId;

    @Column(name = "third_place_participant_id")
    UUID thirdPlaceParticipantId;

    protected SeasonPodiumEntity() {
    }
}
