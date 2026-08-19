CREATE TABLE workspaces (
    id         UUID PRIMARY KEY,
    name       VARCHAR(64) NOT NULL,
    owner_id   UUID        NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX workspaces_owner_id_idx ON workspaces (owner_id);
