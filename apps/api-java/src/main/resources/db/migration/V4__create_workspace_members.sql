CREATE TABLE workspace_members (
    id           UUID PRIMARY KEY,
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role         VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT workspace_members_workspace_user_key UNIQUE (workspace_id, user_id),
    CONSTRAINT workspace_members_role_check CHECK (role IN ('OWNER', 'MEMBER'))
);

CREATE INDEX workspace_members_user_id_idx ON workspace_members (user_id);
