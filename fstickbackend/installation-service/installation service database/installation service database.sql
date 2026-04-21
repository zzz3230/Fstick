CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE installations (
    installation_id UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plugin_id       UUID        NOT NULL,
    version_id      UUID        NOT NULL,
    chat_id         VARCHAR(100) NOT NULL,
    installed_by    VARCHAR(100) NOT NULL,
    installed_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uq_plugin_chat UNIQUE (plugin_id, chat_id)
);

CREATE INDEX idx_installations_chat_id  ON installations (chat_id);
CREATE INDEX idx_installations_plugin_id ON installations (plugin_id);