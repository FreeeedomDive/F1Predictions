CREATE TABLE races
(
    id       uuid PRIMARY KEY,
    season   integer     NOT NULL,
    round    integer     NOT NULL,
    type     varchar(16) NOT NULL,
    status   varchar(16) NOT NULL,
    open_at  timestamptz NOT NULL,
    close_at timestamptz NOT NULL,
    version  bigint      NOT NULL,
    CONSTRAINT uq_races_season_round_type UNIQUE (season, round, type)
);

CREATE INDEX ix_races_season_status ON races (season, status);
CREATE INDEX ix_races_open_at ON races (open_at);
