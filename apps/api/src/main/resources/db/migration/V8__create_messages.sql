CREATE TABLE messages (
    id         UUID PRIMARY KEY,
    channel_id UUID REFERENCES channels (id) ON DELETE CASCADE,
    dm_id      UUID REFERENCES dm_threads (id) ON DELETE CASCADE,
    parent_id  UUID REFERENCES messages (id) ON DELETE CASCADE,
    author_id  UUID        NOT NULL REFERENCES users (id),
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at  TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,

    -- チャンネル/DMのどちらか一方にのみ属する(排他的論理和、DB設計書レビュー指摘によりORから修正)
    CONSTRAINT messages_scope_xor_check CHECK ((channel_id IS NOT NULL) <> (dm_id IS NOT NULL))
);

CREATE INDEX messages_channel_created_id_idx ON messages (channel_id, created_at DESC, id DESC);
CREATE INDEX messages_dm_created_id_idx ON messages (dm_id, created_at DESC, id DESC);
CREATE INDEX messages_parent_id_idx ON messages (parent_id);

-- pg_trgm + ILIKE による部分一致検索用(検索クエリのみdeleted_at IS NULLで除外。一覧・スレッド・コンテキスト取得はtombstoneとして含める)
CREATE INDEX message_body_trgm_idx ON messages USING GIN (body gin_trgm_ops);
