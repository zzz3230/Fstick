# E2 — Registry: branches, content-addressed blobs, catalog

> Design note: §4 (Versioning), §6 (S3 storage), §9 Registry, §10 *Registry — catalog*, `code/server`, `code/client`, `/internal/plugins/*`. Read [00-overview.md](00-overview.md) first.

## Goal
Replace `versions` + `files` + presigned code links with **branches** that point at exactly two content-addressed blobs (client JS, server Lua). Code is always served **inline** through Registry. Plugins are created empty (template code, template icon) with one `WORKING` dev branch.

## Depends on / blocks
- Depends on: E1 (`X-User-Id` is a uuid; `author_id` = caller).
- Blocks: E3 (edit/save), E4 (Runtime loads via `/internal`), E6 (installs reference `branch_id`), E7 (candidate branches).

## Scope
**In:** schema rewrite; `BlobStore`; plugin creation; catalog list/get with visibility rules; ownership guard on existing mutating endpoints; public `code/server` + `code/client`; `/internal/plugins/{id}` and `/internal/plugins/{id}/code/server`; templates.
**Out:** editor save (E3), moderation transitions and their tables (E7), GC (E10).

## Current state
- `database/db/init.sql`: `plugins`, `versions(version_number, runtime, changelog)`, `files(s3_file_key)`, `screenshots`, `tags`, `categories`, `statuses`; seed data with random authors.
- `PluginsService.initPluginUpload` builds keys `plugins/{id}/versions/{v}/{sv|cl}/{lang}/{lver}/{fileName}` and returns presigned PUTs; `commitPlugin` records keys; `getPluginCodeServer/Client` return presigned GETs (`files[0]`).
- `S3Service` (MinIO SDK) has presign + `getObject` + `deleteScreenshot`.
- No ownership checks anywhere.

---

## Feature 2.1 — Schema (edit `registry-service/database/db/init.sql`)

Delete `versions`, `files` and all seed rows that reference them (keep categories, statuses, tags seeds; **remove the seed plugins** — they have random authors and no code). Add:

```sql
CREATE TABLE branches (
    branch_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plugin_id        UUID NOT NULL REFERENCES plugins(plugin_id) ON DELETE CASCADE,
    status           VARCHAR(20) NOT NULL
                     CHECK (status IN ('WORKING','WAITING_APPROVE','APPROVING','RELEASED','REJECTED','CANCELLED')),
    semver           VARCHAR(50),                      -- NULL only for WORKING
    client_blob_sha  CHAR(64) NOT NULL,                -- bare hex
    server_blob_sha  CHAR(64) NOT NULL,
    runtime_client   VARCHAR(50) NOT NULL DEFAULT 'cl.js@1.0.0',
    runtime_server   VARCHAR(50) NOT NULL DEFAULT 'sv.lua@1.0.0',
    base_branch_id   UUID REFERENCES branches(branch_id),   -- dev branch a candidate was cut from
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((status = 'WORKING') = (semver IS NULL))
);
CREATE UNIQUE INDEX uq_one_dev_branch       ON branches(plugin_id) WHERE status = 'WORKING';
CREATE UNIQUE INDEX uq_semver_per_plugin    ON branches(plugin_id, semver) WHERE semver IS NOT NULL AND status IN ('WAITING_APPROVE','APPROVING','RELEASED');
CREATE INDEX        idx_branches_status     ON branches(status);

CREATE TRIGGER update_branches_updated_at BEFORE UPDATE ON branches
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```
`plugins.s3_icon_key` stays nullable (null → template icon). E7 adds `changelog`, `submitted_at`, `claimed_by`, `reject_reason`, `plugins.last_rejection`, `plugin_moderation_log`, `moderators` to this same file.

SemVer: store as text, compare in Java (`io.github.g00fy2:versioncompare` or a 20-line comparator for `MAJOR.MINOR.PATCH`); validate with `^\d+\.\d+\.\d+$` (no pre-release in phase 1).

## Feature 2.2 — `BlobStore`

New `service/BlobStore` on top of the existing `MinioClient`:
```java
public record Blob(String hex, byte[] bytes) { String sha() { return "sha256:" + hex; } }
String put(String pluginId, String text);      // returns hex; SHA-256 of UTF-8 bytes; statObject → skip upload if exists
String get(String pluginId, String hex);       // UTF-8 text; 404 → IllegalStateException (data corruption)
static String hex(String text);                // MessageDigest SHA-256, lowercase hex
```
Key: `plugins/{pluginId}/blobs/{hex}`, content type `text/plain; charset=utf-8`. Blobs are never overwritten or deleted here (E10 handles GC).

Also add `ShaFormat.parse("sha256:…") → hex` and `format(hex)`; reject anything else with `400 invalid_sha`.

Remove from `S3Service`: `getOnlyServerKeys`, `getOnlyClientKeys`; remove `MinioKeyParser.getAllFiles`, `RuntimeParser` (if unused after this epic), `KeyType` code entries, `CodeLinksResponse`, `FileDownloadData` (if only used by code links).

## Feature 2.3 — Templates

`src/main/resources/templates/server.lua`:
```lua
require("backend_stdlib")

SetStateSchema({
    counter = Types.int,
})

RegisterCommand({
    name = "counter.increment",
    in_schema = { by = Types.int },
    out_schema = { value = Types.int },
    handler = function(ctx, payload)
        local v = ctx.state.counter:get() + payload.by
        ctx.state.counter:set(v)
        print("counter is now " .. v)
        return { value = v }
    end,
})
```
`src/main/resources/templates/client.js`:
```js
const increment = backend.makeCommand('counter.increment');

DSL_CONTEXT.exports.__app = Column({ gap: 8 }, [
    Text(state.counter, { content_align: 'center', font_size: 'title' }),
    Button('+1', async () => {
        await increment({ by: 1 }).before(() => state.counter.set((Number(state.counter.value) || 0) + 1));
    }),
]);
```
`templates/icon.png` — any neutral 128×128 PNG. Load all three once at startup (`TemplateProvider` bean).

## Feature 2.4 — Create plugin

`POST /api/v1/plugins` (author = `X-User-Id`, required):
```jsonc
// request
{ "name": "…", "description": "…", "category": "Productivity", "tags": ["…"],
  "icon": { "file_name": "icon.png", "type": "image/png" } | null }
// 201
{ "plugin_id": "…", "dev_branch_id": "…",
  "icon_upload": { "upload_url": "…", "key": "plugins/{id}/icon" } | null }
```
Steps (one `@Transactional`): insert plugin (status `ACTIVE`, `s3_icon_key` = null) → `BlobStore.put` both templates → insert `WORKING` branch → if `icon` present, presign PUT for `plugins/{id}/icon` and return it.

Icon/screenshots keep the two-phase presigned flow. Replace `POST /{id}/commit` by `POST /{id}/assets/commit { "keys": [...] }` that only accepts `plugins/{id}/icon` and `plugins/{id}/screenshots/*` keys (author only), verifies each object exists (`statObject`), sets `s3_icon_key` / inserts screenshots. Delete `initVersionUpload`, `commitVersion`, `AddPluginVersionRequest`, `CommitVersionRequest`, `AddVersionResponse`, and version fields from `AddPluginRequest`/`AddPluginResponse`.

Icon URL in views: on startup, `TemplateProvider` uploads `templates/icon.png` to MinIO key `templates/icon.png` if missing. `s3_icon_key == null` → `icon_url` = presigned GET of `templates/icon.png`; otherwise presigned GET of the plugin's key, as today. (Don't serve the icon through Dendrite: `<img>` tags can't send the Bearer token that every `/fstick` route requires.)

## Feature 2.5 — Ownership guard

`service/AccessGuard`:
```java
PluginRow requireAuthor(UUID pluginId, UUID caller);  // 404 plugin_not_found if missing, 403 not_author otherwise
boolean isAuthor(PluginRow p, UUID caller);
```
Apply to: `PUT /{id}`, `DELETE /{id}`, `PUT /{id}/status`, `POST /{id}/screenshots`, `POST /{id}/assets/commit`, `DELETE /{id}/screenshots/{sid}`. Each of these now takes `@RequestHeader("X-User-Id") UUID`.

`PUT /{id}/status` should become moderator/admin-only later; for now author-only, and it must reject setting anything but `ACTIVE`/`HIDDEN`/`DELETED`.

## Feature 2.6 — Catalog visibility

### `GET /api/v1/plugins` (public list) and `?owned=true`
- Without `owned`: `status = 'ACTIVE' AND EXISTS (SELECT 1 FROM branches b WHERE b.plugin_id = p.plugin_id AND b.status = 'RELEASED')`. Keep existing filters/sort/pagination. Add `released_semver` (max RELEASED semver — compute in Java or with `ORDER BY string_to_array(semver,'.')::int[] DESC LIMIT 1`).
- `owned=true`: requires `X-User-Id` (else 401 from Gateway; Registry returns `400 missing_user`), `WHERE author_id = ?`, no status/release filter except `DELETED` excluded. Item shape:
```jsonc
{ "id", "name", "icon_url", "category", "tags", "author_id",
  "dev_branch_id",
  "candidate": { "branch_id", "semver", "status" } | null,   // WAITING_APPROVE or APPROVING (E7 fills it)
  "released_semver": "1.1.0" | null,
  "last_rejection": { "reason", "at", "semver" } | null }     // E7
```

### `GET /api/v1/plugins/{id}` (optional auth, optional `?chat_id=`)
Visibility of branches for caller `u`:
| Caller | Branches returned |
|---|---|
| author | all (WORKING, candidates, RELEASED, REJECTED, CANCELLED) + `last_rejection` |
| moderator (E7) | RELEASED + candidates |
| anyone else | RELEASED |
| anyone + `?chat_id=c` where installation in `c` points at the WORKING branch and `u` is a member of `c` | RELEASED + that WORKING branch |

No branch visible → `404 plugin_not_found`. Response adds `branches: [{ id, status, semver, runtime: {client, server}, created_at }]`; remove `versions`.

The chat check needs two clients (add now, E3 reuses them):
- `client/InstallationClient.resolve(pluginId, chatId) → Optional<{branch_id, branch_status}>` → installation `GET /internal/installations/resolve` (E6).
- `client/IntegrationClient.isMember(chatId, uuid)` → integration `GET /api/v1/chats/{chatId}/members/{uuid}`.

Config: `services.installation.base-url=${INSTALLATION_SERVICE_URL:http://localhost:8081}`, `services.integration.base-url=${INTEGRATION_SERVICE_URL:http://localhost:8080}`; add both env vars to the registry block in `docker-compose.yml`.

## Feature 2.7 — Code endpoints (inline)

Shared rule `canReadBranch(caller, plugin, branch, chatId)`:
- `RELEASED` → anyone.
- `WAITING_APPROVE`/`APPROVING` → author or moderator.
- `WORKING` → author, or member of `chatId` whose installation points at this branch.
- `REJECTED`/`CANCELLED` → author only.
- otherwise → **404** `branch_not_found` (never 403).

| Method | Path | Response |
|---|---|---|
| GET | `/api/v1/plugins/{id}/code/server?branch_id=` | `200 { "text", "sha" }` |
| GET | `/api/v1/plugins/{id}/code/client?branch_id=&chat_id=` | `200 { "text", "sha" }`, header `ETag: "sha256:…"`; `If-None-Match` equal → `304` |
| GET | `/internal/plugins/{id}` | `200 { "id", "status", "author_id", "branches": [{ "id", "status", "semver", "server_blob_sha", "client_blob_sha" }] }` — all branches, no gate |
| GET | `/internal/plugins/{id}/code/server?branch_id=` | `200 { "text", "sha" }` — no gate |

Put the two `/internal` routes in `controller/InternalPluginsController`. Delete old `getPluginCodeClient/Server` (version/runtime params) and `CodeLinksResponse`.

## Feature 2.8 — Error handling
Add `exception/ApiException` + `@RestControllerAdvice ApiExceptionHandler` producing `{ error, message, … }` (see 00-overview §3.2). Replace the `RuntimeException`s in `PluginsService` you touch with `ApiException`.

---

## Tests (JUnit 5 + Mockito, `PluginsServiceTest` style; add `BranchRepository` tests with Testcontainers Postgres if available)
- `BlobStore`: same text twice → one `putObject`; hex matches known SHA-256 vector; UTF-8 Cyrillic hashed as bytes.
- Create plugin: returns `dev_branch_id`; branch has template shas; icon null → no presign.
- Catalog: plugin without RELEASED branch not in public list; in `owned=true` of its author only.
- `GET /{id}` matrix from the table above, including chat-visibility true/false (mock clients) and 404 when nothing visible.
- `code/*`: each row of `canReadBranch`, hidden → 404; `If-None-Match` → 304.
- `/internal/*`: no header required.
- Ownership guard: non-author `PUT /{id}` → 403; unknown id → 404.

## Acceptance criteria
- [ ] `versions`, `files`, presigned code links and `files[0]` logic are gone.
- [ ] Creating a plugin with `icon: null` returns `dev_branch_id`; `GET /internal/plugins/{id}/code/server?branch_id=…` returns the Lua template inline.
- [ ] Public list shows only plugins with a RELEASED branch (none yet until E7 — verify with a manual `UPDATE branches SET status='RELEASED', semver='1.0.0'` on a copy).
- [ ] MinIO shows objects under `plugins/{id}/blobs/` only; identical content is stored once.
