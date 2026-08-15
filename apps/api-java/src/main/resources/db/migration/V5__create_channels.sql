CREATE TABLE channels (
    id           UUID PRIMARY KEY,
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name         VARCHAR(80) NOT NULL,
    type         VARCHAR(16) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT channels_workspace_name_key UNIQUE (workspace_id, name),
    CONSTRAINT channels_type_check CHECK (type IN ('PUBLIC', 'PRIVATE'))
);

CREATE INDEX channels_workspace_type_idx ON channels (workspace_id, type);
