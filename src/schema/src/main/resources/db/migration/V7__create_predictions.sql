CREATE TABLE predictions
(
    id                          uuid PRIMARY KEY,
    league_id                   uuid   NOT NULL,
    participant_id              uuid   NOT NULL,
    race_id                     uuid   NOT NULL REFERENCES races (id) ON DELETE CASCADE,
    version                     bigint NOT NULL,
    tenth_place_picked_driver    text,
    dnf_picked_drivers           jsonb,
    safety_cars_prediction      varchar(16),
    driver_position_prediction  integer,
    first_place_lead_prediction numeric(9, 3),
    teams_picked_drivers         jsonb,

    CONSTRAINT fk_predictions_league_participant FOREIGN KEY (league_id, participant_id)
        REFERENCES league_participants (league_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_predictions_race_participant UNIQUE (race_id, participant_id)
);

CREATE INDEX ix_predictions_league_participant ON predictions (league_id, participant_id);

CREATE TABLE race_prediction_conditions
(
    race_id                    uuid PRIMARY KEY REFERENCES races (id) ON DELETE CASCADE,
    version                    bigint NOT NULL,
    position_prediction_driver text
);
