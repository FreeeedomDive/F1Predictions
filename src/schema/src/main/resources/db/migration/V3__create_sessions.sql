CREATE TABLE sessions
(
    hash text PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    last_used_at timestamptz,

    CHECK ( expires_at > created_at )
);

CREATE INDEX ix_sessions_expires_at ON sessions (expires_at);