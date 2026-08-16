CREATE TABLE notifications (
    id               UUID PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type             VARCHAR(20) NOT NULL,
    workspace_id     UUID REFERENCES workspaces (id) ON DELETE CASCADE,
    channel_id       UUID REFERENCES channels (id) ON DELETE CASCADE,
    dm_id            UUID REFERENCES dm_threads (id) ON DELETE CASCADE,
    message_id       UUID,
    thread_parent_id UUID,
    from_user_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    text             TEXT        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at          TIMESTAMPTZ,

    -- THREAD_REPLYはDB設計書レビュー指摘により追加(当初のCHECK制約から漏れていた)
    CONSTRAINT notifications_type_check
        CHECK (type IN ('MENTION', 'DM', 'CHANNEL_INVITE', 'WORKSPACE_INVITE', 'THREAD_REPLY'))
);

CREATE INDEX notifications_user_read_idx ON notifications (user_id, read_at);
CREATE INDEX notifications_user_created_id_idx ON notifications (user_id, created_at DESC, id DESC);
