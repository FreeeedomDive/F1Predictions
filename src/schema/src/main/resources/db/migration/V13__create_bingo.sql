CREATE TABLE bingo_cards
(
    id                uuid PRIMARY KEY,
    season            integer NOT NULL CHECK (season > 0),
    translations      jsonb   NOT NULL CHECK (jsonb_typeof(translations) = 'object'),
    probability       text    NOT NULL CHECK (probability IN ('LOW', 'MEDIUM', 'HIGH')),
    required_repeats  integer NOT NULL CHECK (required_repeats > 0),
    completed_repeats integer NOT NULL CHECK (completed_repeats BETWEEN 0 AND required_repeats),

    CONSTRAINT uq_bingo_cards_season_and_card UNIQUE (season, id)
);

CREATE TABLE bingo_boards
(
    id             uuid PRIMARY KEY,
    league_id      uuid    NOT NULL,
    season         integer NOT NULL CHECK (season > 0),
    participant_id uuid    NOT NULL,
    bingo_achieved boolean NOT NULL,

    CONSTRAINT fk_bingo_boards_participant FOREIGN KEY (league_id, participant_id)
        REFERENCES league_participants (league_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_bingo_boards_participant_season UNIQUE (league_id, season, participant_id),
    CONSTRAINT uq_bingo_boards_season_and_board UNIQUE (season, id)
);

CREATE INDEX ix_bingo_boards_league_participant ON bingo_boards (league_id, participant_id);

CREATE TABLE bingo_board_cells
(
    board_id uuid    NOT NULL,
    position integer NOT NULL CHECK (position BETWEEN 0 AND 24),
    season   integer NOT NULL,
    card_id  uuid    NOT NULL,

    CONSTRAINT pk_bingo_board_cells PRIMARY KEY (board_id, position),
    CONSTRAINT uq_bingo_board_cells_card UNIQUE (board_id, card_id),
    CONSTRAINT fk_bingo_board_cells_board FOREIGN KEY (season, board_id)
        REFERENCES bingo_boards (season, id) ON DELETE CASCADE,
    CONSTRAINT fk_bingo_board_cells_card FOREIGN KEY (season, card_id)
        REFERENCES bingo_cards (season, id)
);

CREATE INDEX ix_bingo_board_cells_card ON bingo_board_cells (card_id);
