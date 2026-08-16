CREATE TABLE mentions (
    id                 UUID PRIMARY KEY,
    message_id         UUID NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    mentioned_user_id  UUID NOT NULL REFERENCES users (id)
);

CREATE INDEX mentions_mentioned_user_id_idx ON mentions (mentioned_user_id);
CREATE INDEX mentions_message_id_idx ON mentions (message_id);
