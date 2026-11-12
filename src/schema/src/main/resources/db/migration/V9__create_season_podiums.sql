CREATE TABLE season_podiums
(
    league_id             uuid    NOT NULL REFERENCES leagues (id) ON DELETE CASCADE,
    season                integer NOT NULL,
    first_place_participant_id  uuid    NOT NULL REFERENCES league_participants (id) ON DELETE CASCADE,
    second_place_participant_id uuid REFERENCES league_participants (id) ON DELETE CASCADE,
    third_place_participant_id  uuid REFERENCES league_participants (id) ON DELETE CASCADE,

    CONSTRAINT pk_season_podiums PRIMARY KEY (league_id, season)
);
