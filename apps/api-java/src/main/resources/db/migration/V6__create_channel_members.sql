CREATE TABLE channel_members (
    id            UUID PRIMARY KEY,
    channel_id    UUID        NOT NULL REFERENCES channels (id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_read_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT channel_members_channel_user_key UNIQUE (channel_id, user_id)
);

CREATE INDEX channel_members_user_id_idx ON channel_members (user_id);
