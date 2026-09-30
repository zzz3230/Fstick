CREATE EXTENSION IF NOT EXISTS pgcrypto;


CREATE TABLE "categories" (
    "category_id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "name" VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE "statuses" (
    "status_id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "name" VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE "plugins" (
    "plugin_id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "name" VARCHAR(50) NOT NULL,
    "description" VARCHAR(2000),
    "status_id" UUID NOT NULL REFERENCES statuses(status_id),
    "category_id" UUID NOT NULL REFERENCES categories(category_id),
    "author_id" UUID NOT NULL,
    "s3_icon_key" VARCHAR(300) UNIQUE,
    "last_rejection" JSONB,
    "updated_at" TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    "created_at" TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE "tags" (
    "tag_id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "name" VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE "plugins_tags" (
    "plugin_id" UUID REFERENCES plugins(plugin_id) ON DELETE CASCADE,
    "tag_id" UUID REFERENCES tags(tag_id) ON DELETE CASCADE,
    PRIMARY KEY (plugin_id, tag_id)
);

CREATE TABLE "screenshots" (
    "screenshot_id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "plugin_id" UUID REFERENCES plugins(plugin_id) ON DELETE CASCADE,
    "s3_screenshot_key" VARCHAR(300) NOT NULL
);

CREATE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_plugins_updated_at
    BEFORE UPDATE ON plugins
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE "branches" (
    "branch_id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "plugin_id" UUID NOT NULL REFERENCES plugins(plugin_id) ON DELETE CASCADE,
    "status" VARCHAR(20) NOT NULL
        CHECK (status IN ('WORKING', 'WAITING_APPROVE', 'APPROVING', 'RELEASED', 'REJECTED', 'CANCELLED')),
    "semver" VARCHAR(50),
    "client_blob_sha" CHAR(64) NOT NULL,
    "server_blob_sha" CHAR(64) NOT NULL,
    "runtime_client" VARCHAR(50) NOT NULL DEFAULT 'cl.js@1.0.0',
    "runtime_server" VARCHAR(50) NOT NULL DEFAULT 'sv.lua@1.0.0',
    "base_branch_id" UUID REFERENCES branches(branch_id),
    "changelog" VARCHAR(2000),
    "submitted_at" TIMESTAMP WITH TIME ZONE,
    "claimed_by" UUID,
    "reject_reason" VARCHAR(2000),
    "created_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "updated_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CHECK ((status = 'WORKING') = (semver IS NULL))
);

CREATE UNIQUE INDEX uq_one_dev_branch ON branches(plugin_id) WHERE status = 'WORKING';
CREATE UNIQUE INDEX uq_semver_per_plugin ON branches(plugin_id, semver)
    WHERE semver IS NOT NULL AND status IN ('WAITING_APPROVE', 'APPROVING', 'RELEASED');
CREATE UNIQUE INDEX uq_one_open_candidate ON branches(plugin_id)
    WHERE status IN ('WAITING_APPROVE', 'APPROVING');
CREATE INDEX idx_branches_status ON branches(status);

CREATE TRIGGER update_branches_updated_at
    BEFORE UPDATE ON branches
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE "plugin_moderation_log" (
    "id" BIGSERIAL PRIMARY KEY,
    "plugin_id" UUID NOT NULL REFERENCES plugins(plugin_id) ON DELETE CASCADE,
    "branch_id" UUID NOT NULL REFERENCES branches(branch_id) ON DELETE CASCADE,
    "action" VARCHAR(20) NOT NULL CHECK (action IN ('PUBLISH', 'CLAIM', 'APPROVE', 'REJECT', 'CANCEL')),
    "actor_id" UUID NOT NULL,
    "reason" VARCHAR(2000),
    "at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_moderation_log_plugin ON plugin_moderation_log(plugin_id, at);

CREATE TABLE "moderators" (
    "internal_uuid" UUID PRIMARY KEY,
    "granted_by" UUID,
    "granted_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);


-- =========================
-- CATEGORIES
-- =========================
INSERT INTO categories (name) VALUES
                                  ('Analytics'),
                                  ('Productivity'),
                                  ('Security');

-- =========================
-- STATUSES
-- =========================
INSERT INTO statuses (name) VALUES
                                ('ACTIVE'),
                                ('DELETED'),
                                ('HIDDEN'),
                                ('ARCHIVED');

-- =========================
-- TAGS
-- =========================
INSERT INTO tags (name) VALUES
                            ('AI'),
                            ('dashboard'),
                            ('automation'),
                            ('auth'),
                            ('logging');

