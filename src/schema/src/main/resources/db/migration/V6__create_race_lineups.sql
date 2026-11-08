CREATE TABLE race_lineups
(
    race_id uuid PRIMARY KEY REFERENCES races (id) ON DELETE CASCADE,
    teams   jsonb  NOT NULL,
    version bigint NOT NULL
);
