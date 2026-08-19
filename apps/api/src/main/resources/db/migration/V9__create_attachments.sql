CREATE TABLE attachments (
    id           UUID PRIMARY KEY,
    message_id   UUID REFERENCES messages (id) ON DELETE CASCADE,
    uploader_id  UUID         NOT NULL REFERENCES users (id),
    storage_key  VARCHAR(255) NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    mime_type    VARCHAR(64)  NOT NULL,
    size_bytes   INTEGER      NOT NULL,
    kind         VARCHAR(16)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT attachments_storage_key_key UNIQUE (storage_key),
    CONSTRAINT attachments_kind_check CHECK (kind IN ('IMAGE', 'VIDEO'))
);

CREATE INDEX attachments_message_id_idx ON attachments (message_id);
