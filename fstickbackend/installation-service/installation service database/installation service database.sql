CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE installations (
    installation_id  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    plugin_id        UUID         NOT NULL,
    branch_id        UUID         NOT NULL,
    branch_status    VARCHAR(20)  NOT NULL CHECK (branch_status IN ('WORKING', 'RELEASED')),
    plugin_author_id UUID         NOT NULL,
    chat_id          VARCHAR(255) NOT NULL,
    installed_by     UUID         NOT NULL,
    installed_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_plugin_chat UNIQUE (plugin_id, chat_id)
);

CREATE INDEX idx_installations_chat_id   ON installations (chat_id);
CREATE INDEX idx_installations_branch_id ON installations (branch_id);
