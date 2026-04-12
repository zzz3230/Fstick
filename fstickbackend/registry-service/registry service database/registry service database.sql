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
    "author_id" VARCHAR(100) NOT NULL,
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
    "s3_archive_key" VARCHAR(300) NOT NULL,
    "plugin_id" UUID REFERENCES plugins(plugin_id) ON DELETE CASCADE
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
)