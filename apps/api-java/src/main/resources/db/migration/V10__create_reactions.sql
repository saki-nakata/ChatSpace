CREATE TABLE reactions (
    id         UUID PRIMARY KEY,
    message_id UUID        NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    emoji      VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT reactions_message_user_emoji_key UNIQUE (message_id, user_id, emoji)
);

CREATE INDEX reactions_message_id_idx ON reactions (message_id);
