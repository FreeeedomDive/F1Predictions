CREATE TABLE race_results
(
    rules_version text   NOT NULL,
    race_id       uuid PRIMARY KEY REFERENCES races (id) ON DELETE CASCADE,
    version       bigint NOT NULL,
    result        jsonb,
    draft         jsonb
);

CREATE TABLE prediction_points
(
    rules_version           text    NOT NULL,
    prediction_id           uuid PRIMARY KEY REFERENCES predictions (id) ON DELETE CASCADE,
    version                 bigint  NOT NULL,
    total_points            integer NOT NULL,
    tenth_place_points      integer,
    dnfs_points             integer,
    safety_cars_points      integer,
    first_place_lead_points integer,
    team_mates_points       integer,
    driver_position_points  integer
);
