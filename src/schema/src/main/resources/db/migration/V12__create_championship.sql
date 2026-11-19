CREATE TABLE championship_seasons
(
    season            integer PRIMARY KEY,
    rules_version     text    NOT NULL,
    stage             text    NOT NULL CHECK (stage IN ('PRE_SEASON', 'MID_SEASON')),
    stage_started     boolean NOT NULL,
    is_open           boolean NOT NULL,
    pre_season_roster jsonb   NOT NULL,
    mid_season_roster jsonb   NOT NULL,
    version           bigint  NOT NULL,

    CONSTRAINT ck_championship_stage_started CHECK (stage_started OR (stage = 'PRE_SEASON' AND NOT is_open))
);

CREATE TABLE championship_driver_standings
(
    season            integer PRIMARY KEY REFERENCES championship_seasons (season) ON DELETE CASCADE,
    ordered_drivers   jsonb       NOT NULL,
    last_synced_round integer,
    updated_at        timestamptz NOT NULL,
    version           bigint      NOT NULL
);

CREATE TABLE championship_predictions
(
    league_id         uuid    NOT NULL,
    season            integer NOT NULL REFERENCES championship_seasons (season) ON DELETE CASCADE,
    participant_id    uuid    NOT NULL,
    stage             text    NOT NULL CHECK (stage IN ('PRE_SEASON', 'MID_SEASON')),
    ordered_drivers   jsonb   NOT NULL,
    submission_source text    NOT NULL CHECK (submission_source IN ('USER', 'PRE_SEASON_FALLBACK', 'LEGACY_UNKNOWN')),
    version           bigint  NOT NULL,

    CONSTRAINT pk_championship_predictions PRIMARY KEY (league_id, season, participant_id, stage),
    CONSTRAINT fk_championship_predictions_league_participant FOREIGN KEY (league_id, participant_id)
        REFERENCES league_participants (league_id, id) ON DELETE CASCADE
);

CREATE INDEX ix_championship_predictions_league_participant ON championship_predictions (league_id, participant_id);
