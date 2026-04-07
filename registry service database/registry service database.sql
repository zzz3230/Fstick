CREATE TABLE "categories" (
  "id" SERIAL PRIMARY KEY,
  "name" VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE "statuses" (
  "id" SERIAL PRIMARY KEY,
  "name" VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE "plugins" (
  "id" SERIAL PRIMARY KEY,
  "name" VARCHAR(50) NOT NULL,  
  "description" VARCHAR(2000),
  "status_id" INTEGER REFERENCES statuses(id) ON DELETE SET NULL,
  "category_id" INTEGER REFERENCES categories(id) ON DELETE SET NULL,
  "author" VARCHAR(100) NOT NULL,
  "updated_at" TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  "created_at" TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE "tags" (
  "id" SERIAL PRIMARY KEY,
  "name" VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE "plugins_tags" (
  "plugin_id" INTEGER REFERENCES plugins(id) ON DELETE CASCADE, 
  "tag_id" INTEGER REFERENCES tags(id) ON DELETE CASCADE, 
  PRIMARY KEY (plugin_id, tag_id)
);

CREATE TABLE "versions" (
  "id" SERIAL PRIMARY KEY,
  "version" VARCHAR(50) NOT NULL,
  "changelog" VARCHAR(2000) NOT NULL,
  "s3_archive_key" VARCHAR(100) NOT NULL,
  "plugin_id" INTEGER REFERENCES plugins(id) ON DELETE CASCADE
);

CREATE TABLE "screenshots" (
  "id" SERIAL PRIMARY KEY,
  "plugin_id" INTEGER REFERENCES plugins(id) ON DELETE CASCADE,
  "s3_screenshot_key" VARCHAR(100) NOT NULL
);



