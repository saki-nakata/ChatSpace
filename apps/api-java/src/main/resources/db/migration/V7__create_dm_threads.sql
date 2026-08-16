-- user_a_id < user_b_id となるよう正規化して保存する(アプリ層で保証。DM機能定義書参照)
CREATE TABLE dm_threads (
    id             UUID PRIMARY KEY,
    workspace_id   UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_a_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_b_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_read_at_a TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_read_at_b TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT dm_threads_workspace_users_key UNIQUE (workspace_id, user_a_id, user_b_id)
);

CREATE INDEX dm_threads_user_a_idx ON dm_threads (user_a_id);
CREATE INDEX dm_threads_user_b_idx ON dm_threads (user_b_id);
