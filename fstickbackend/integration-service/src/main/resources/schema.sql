CREATE TABLE IF NOT EXISTS identities (
    internal_uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mxid          VARCHAR(255) NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
