CREATE TABLE users
(
    id         uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version    bigint      NOT NULL
);

create table user_identities
(
    user_id   uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source    text NOT NULL,
    source_id text NOT NULL,

    CONSTRAINT pk_user_identities PRIMARY KEY (source, source_id)
);

create table league_participants
(
    id           uuid PRIMARY KEY,
    league_id    uuid NOT NULL REFERENCES leagues (id) ON DELETE CASCADE,
    user_id      uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    display_name text        NOT NULL,
    joined_at    timestamptz NOT NULL,
    version      bigint      NOT NULL,

    CONSTRAINT uq_league_participants_league_and_user UNIQUE (league_id, user_id),
    CONSTRAINT uq_league_participants_league_and_participant UNIQUE (league_id, id)
);