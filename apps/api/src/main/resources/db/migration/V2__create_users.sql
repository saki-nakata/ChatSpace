CREATE TABLE users (
    id             UUID PRIMARY KEY,
    user_id        VARCHAR(32)  NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    display_name   VARCHAR(64)  NOT NULL,
    avatar_url     TEXT,
    status         VARCHAR(128),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT users_user_id_key UNIQUE (user_id)
);
