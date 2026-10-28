CREATE TABLE leagues
(
    id         uuid PRIMARY KEY,
    name       text        NOT NULL,
    source     text        NOT NULL,
    source_id  text        NOT NULL,
    created_at timestamptz NOT NULL,
    version    bigint      NOT NULL,

    CONSTRAINT uq_leagues_source UNIQUE (source, source_id)
);

CREATE TABLE league_settings
(
    league_id  uuid PRIMARY KEY REFERENCES leagues (id) ON DELETE CASCADE,
    notifications_channel_id text,
    version    bigint NOT NULL
);
