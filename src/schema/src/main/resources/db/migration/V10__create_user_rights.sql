CREATE TABLE user_rights
(
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    right_name text NOT NULL,

    CONSTRAINT pk_user_rights PRIMARY KEY (user_id, right_name)
);
