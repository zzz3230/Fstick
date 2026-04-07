CREATE TABLE "categories" (
  "category_id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "name" VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE "statuses" (
  "status_id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "name" VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE "plugins" (
  "plugin_id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "name" VARCHAR(50) NOT NULL,  
  "description" VARCHAR(2000),
  "status_id" BIGINT REFERENCES statuses(status_id) ON DELETE SET NULL,
  "category_id" BIGINT REFERENCES categories(category_id) ON DELETE SET NULL,
  "author_id" VARCHAR(100) NOT NULL,
  "updated_at" TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  "created_at" TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE "tags" (
  "tag_id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "name" VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE "plugins_tags" (
  "plugin_id" BIGINT REFERENCES plugins(plugin_id) ON DELETE CASCADE, 
  "tag_id" BIGINT REFERENCES tags(tag_id) ON DELETE CASCADE, 
  PRIMARY KEY (plugin_id, tag_id)
);

CREATE TABLE "versions" (
  "version_id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "version_number" VARCHAR(50) NOT NULL,
  "changelog" VARCHAR(2000) NOT NULL,
  "s3_archive_key" VARCHAR(300) NOT NULL,
  "plugin_id" BIGINT REFERENCES plugins(plugin_id) ON DELETE CASCADE
);

CREATE TABLE "screenshots" (
  "screenshot_id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "plugin_id" BIGINT REFERENCES plugins(plugin_id) ON DELETE CASCADE,
  "s3_screenshot_key" VARCHAR(300) NOT NULL
);



