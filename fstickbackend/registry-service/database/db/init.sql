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
    "status_id" UUID REFERENCES statuses(status_id) ON DELETE SET NULL,
    "category_id" UUID REFERENCES categories(category_id) ON DELETE SET NULL,
    "author_id" UUID NOT NULL,
    "s3_icon_key" VARCHAR(300) NOT NULL,
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

CREATE TABLE "versions" (
    "version_id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "version_number" VARCHAR(50) NOT NULL,
    "changelog" VARCHAR(2000) NOT NULL,
    "created_at" TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    "plugin_id" UUID REFERENCES plugins(plugin_id) ON DELETE CASCADE,
    UNIQUE (plugin_id, version_number)
);

CREATE TABLE "screenshots" (
    "screenshot_id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "plugin_id" UUID REFERENCES plugins(plugin_id) ON DELETE CASCADE,
    "s3_screenshot_key" VARCHAR(300) NOT NULL
);

CREATE TABLE "files" (
    "file_id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "version_id" UUID REFERENCES versions(version_id) ON DELETE CASCADE,
    "s3_file_key" VARCHAR(300) NOT NULL
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
                                ('active'),
                                ('deprecated'),
                                ('beta');

-- =========================
-- TAGS
-- =========================
INSERT INTO tags (name) VALUES
                            ('AI'),
                            ('dashboard'),
                            ('automation'),
                            ('auth'),
                            ('logging');

-- =========================
-- PLUGINS
-- =========================
INSERT INTO plugins (
    name,
    description,
    status_id,
    category_id,
    author_id,
    s3_icon_key
)
VALUES
    (
        'Smart Analytics',
        'Advanced analytics plugin with AI insights.',
        (SELECT status_id FROM statuses WHERE name = 'active'),
        (SELECT category_id FROM categories WHERE name = 'Analytics'),
        gen_random_uuid(),
        'icons/smart-analytics.png'
    ),
    (
        'Task Automator',
        'Automates repetitive workflows and tasks.',
        (SELECT status_id FROM statuses WHERE name = 'beta'),
        (SELECT category_id FROM categories WHERE name = 'Productivity'),
        gen_random_uuid(),
        'icons/task-automator.png'
    ),
    (
        'Secure Auth',
        'Adds authentication and security layers.',
        (SELECT status_id FROM statuses WHERE name = 'active'),
        (SELECT category_id FROM categories WHERE name = 'Security'),
        gen_random_uuid(),
        'icons/secure-auth.png'
    );

-- =========================
-- PLUGIN - TAG RELATIONS
-- =========================
INSERT INTO plugins_tags (plugin_id, tag_id)
SELECT p.plugin_id, t.tag_id
FROM plugins p, tags t
WHERE (p.name = 'Smart Analytics' AND t.name IN ('AI', 'dashboard'))
   OR (p.name = 'Task Automator' AND t.name IN ('automation', 'AI'))
   OR (p.name = 'Secure Auth' AND t.name IN ('auth', 'logging'));

-- =========================
-- VERSIONS
-- =========================
INSERT INTO versions (
    version_number,
    changelog,
    s3_archive_key,
    plugin_id
)
VALUES
    (
        '1.0.0',
        'Initial release with core features.',
        'archives/smart-analytics/v1.0.0.zip',
        (SELECT plugin_id FROM plugins WHERE name = 'Smart Analytics')
    ),
    (
        '1.1.0',
        'Added predictive models.',
        'archives/smart-analytics/v1.1.0.zip',
        (SELECT plugin_id FROM plugins WHERE name = 'Smart Analytics')
    ),
    (
        '0.1.0',
        'Beta release of automation engine.',
        'archives/task-automator/v0.1.0.zip',
        (SELECT plugin_id FROM plugins WHERE name = 'Task Automator')
    ),
    (
        '2.0.0',
        'Major security overhaul.',
        'archives/secure-auth/v2.0.0.zip',
        (SELECT plugin_id FROM plugins WHERE name = 'Secure Auth')
    );

-- =========================
-- SCREENSHOTS
-- =========================
INSERT INTO screenshots (plugin_id, s3_screenshot_key)
VALUES
    (
        (SELECT plugin_id FROM plugins WHERE name = 'Smart Analytics'),
        'screenshots/smart-analytics-1.png'
    ),
    (
        (SELECT plugin_id FROM plugins WHERE name = 'Task Automator'),
        'screenshots/task-automator-1.png'
    ),
    (
        (SELECT plugin_id FROM plugins WHERE name = 'Secure Auth'),
        'screenshots/secure-auth-1.png'
    );

-- =========================
-- FILES (for versions)
-- =========================
INSERT INTO files (version_id, s3_file_key)
SELECT v.version_id, 'files/' || p.name || '/' || v.version_number || '/main.tar.gz'
FROM versions v
         JOIN plugins p ON p.plugin_id = v.plugin_id;