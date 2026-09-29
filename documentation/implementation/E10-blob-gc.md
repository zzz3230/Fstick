# E10 — Blob garbage collection (deferred)

> Design note: §4, §13 open question #4. Read [00-overview.md](00-overview.md) first. **Not required for the editor to work** — schedule after E3/E7 are stable.

## Problem
Every save of the dev branch writes new content-addressed blobs (`plugins/{pluginId}/blobs/{hex}`) and nothing deletes the superseded ones. Candidate branches share blobs with the dev branch; RELEASED branches must keep theirs forever.

## Rule
A blob is **live** if any branch row references it (`client_blob_sha` or `server_blob_sha`) — *any* status, including REJECTED/CANCELLED (kept for the moderation history). Everything else under `plugins/{pluginId}/blobs/` is garbage once it is older than a grace period.

## Implementation (registry)
- `@Scheduled(cron = "${registry.gc.cron:0 30 3 * * *}")` job `BlobGcJob`, guarded by a Postgres advisory lock (`pg_try_advisory_lock(4242)`) so only one instance runs.
- For each plugin (page through `plugins`):
  1. `live = SELECT client_blob_sha FROM branches WHERE plugin_id=? UNION SELECT server_blob_sha …`.
  2. List objects with prefix `plugins/{id}/blobs/` (MinIO `listObjects`).
  3. Delete objects whose name ∉ `live` **and** `lastModified < now - grace` (default 24 h — protects a save whose DB commit hasn't happened yet, and a blob that is being re-used by a concurrent save).
- Plugins with status `DELETED`: optionally delete the whole `plugins/{id}/` prefix after 30 days (separate flag, off by default).
- Metrics/log: objects scanned, deleted, bytes freed.

## Config
```properties
registry.gc.enabled=false
registry.gc.cron=0 30 3 * * *
registry.gc.grace-hours=24
```

## Tests
- Blob referenced only by a REJECTED branch survives.
- Unreferenced blob younger than grace survives; older is deleted.
- Two job instances → only one runs.

## Acceptance criteria
- [ ] After 50 saves of a dev branch and one GC run past the grace period, only the blobs referenced by branches remain.
