# E3 — Editor code API: bootstrap, save, hot-reload, `reloaded` event

> Design note: §1 (loop), §6 (limits), §7 (reload), §10 *Registry — editor* + hot-reload endpoint, §11 `fstick.plugin.reloaded`. Read [00-overview.md](00-overview.md) first.

## Goal
The editor can load both sources of the dev branch, save either side with optimistic concurrency, trigger a Runtime reload only when the server side changed, retry a throttled reload explicitly, and every client running the dev branch learns when the client side changed.

## Depends on / blocks
- Depends on: E2 (branches, BlobStore, AccessGuard, Installation/Integration clients), E4 (Runtime `/internal/plugins/{id}/reload`), E6 (`GET /internal/installations?branch_id=`), E1.
- Blocks: E9 editor.

## Endpoints (all author-only, dev branch only)

### 3.1 `GET /api/v1/plugins/{id}/branches/{bid}/edit?chat_id=`
```jsonc
// 200
{ "plugin":  { "id", "name", "author_id" },
  "branch":  { "id", "status": "WORKING", "runtime": { "client": "cl.js@1.0.0", "server": "sv.lua@1.0.0" } },
  "source":  { "client": { "text", "sha" }, "server": { "text", "sha" } },
  "chat_id": "…" }
```
- Not author → 404 if the branch isn't visible to them, else 403 `not_author` (author check first on the plugin; a non-author can't see WORKING → 404).
- Branch not `WORKING` → `409 not_dev_branch`.
- `chat_id` is echoed; it is not validated against installations (the editor may be opened after an uninstall).

### 3.2 `PUT /api/v1/plugins/{id}/branches/{bid}/code?chat_id=`
```jsonc
// request — only the side(s) that changed; at least one of client/server/runtime
{ "client":  { "text": "…", "base_sha": "sha256:…" },
  "server":  { "text": "…", "base_sha": "sha256:…" },
  "runtime": { "server": "sv.lua@2.0.0" } }
// 200
{ "warnings": [ { "side": "server", "message": "syntax error at line 12", "line": 12 } ],
  "sha": { "client": "sha256:…", "server": "sha256:…" },
  "reloaded": true,
  "retry_after_ms": null,
  "client_changed": false }
// 409
{ "error": "stale_code", "message": "…", "current": { "client": "sha256:…", "server": "sha256:…" } }
// 413
{ "error": "file_too_large", "message": "…", "side": "server", "limit": 500000, "actual": 812345 }
// 422
{ "error": "runtime_change_unsupported", "message": "…" }   // any "runtime" key, until stdlib versioning exists
```

Algorithm (`EditorService.save`):
1. Load plugin + branch **with `SELECT … FOR UPDATE`** on the branch row (serialises concurrent saves). Author + `WORKING` checks as in 3.1.
2. Reject `runtime` → 422 (see pitfalls).
3. For each present side: `bytes = text.getBytes(UTF_8).length`; `> 500_000` → 413 with that side. Compare `base_sha` to the current sha of that side; mismatch → 409 with both current shas. **Nothing is written on any 4xx.**
4. For each present side whose new hex ≠ current hex: `BlobStore.put`. Update the branch row (`client_blob_sha`/`server_blob_sha`) in the same transaction; commit.
5. `serverChanged` / `clientChanged` = hex differs (a side sent with identical text is "unchanged").
6. Warnings: for the server side, compile-only check with luaj — `LoadState.load`/`LuaC.instance.compile(new ByteArrayInputStream(bytes), "sv.lua")` catching `LuaError`; parse the line from the message (`sv.lua:12: …`). Add `org.luaj:luaj-jse:3.0.1` to registry's `build.gradle`. **Never execute** the code in Registry. No JS check server-side (the editor lints).
7. After commit:
   - `serverChanged` → `RuntimeClient.reload(pluginId, chatId, branchId)`:
     - 200 → `reloaded=true`
     - 429 → `reloaded=false`, `retry_after_ms` from the body
     - other error / timeout (2 s) → `reloaded=false`, `retry_after_ms=0`, add warning `{ side: "server", message: "runtime unavailable, retry hot-reload" }`. The save itself still returns 200.
   - not `serverChanged` → `reloaded=true` (engine already current).
   - `clientChanged` → `ReloadNotifier.clientChanged(pluginId, branchId)` (3.4), async (fire-and-forget on a small executor so the save response isn't delayed).
8. Return shas of **both** sides (formatted `sha256:`).

### 3.3 `POST /api/v1/plugins/{id}/branches/{bid}/reload?chat_id=` — Hot-reload button
- No body. Author + `WORKING`.
- Always calls `RuntimeClient.reload(...)`; maps 200 → `200 { "reloaded": true }`, 429 → `429 { "error": "reload_throttled", "retry_after_ms" }`, Runtime down → `503 runtime_unavailable`.

### 3.4 `fstick.plugin.reloaded` emission
`ReloadNotifier.clientChanged(pluginId, branchId)`:
1. `InstallationClient.chatsForBranch(branchId)` → installation `GET /internal/installations?branch_id=` → `{ chat_ids: [...] }` (E6).
2. If empty → done.
3. `IntegrationClient.pushPluginEvent(...)` → integration `POST /api/v1/plugin-events/push`:
```jsonc
{ "type": "fstick.plugin.reloaded",
  "chat_ids": ["!a:x", "!b:x"],
  "content": { "plugin_id": "…", "branch_id": "…", "client_changed": true } }
```
Failures are logged, never surfaced to the editor.

### 3.5 integration: `POST /api/v1/plugin-events/push` (implement in integration-service as part of this epic)
- Body as above; `type` must start with `fstick.plugin.` → else 400.
- For each chat: `getChatMembers(chatId)` (members as MXIDs from Dendrite), push `{ type, content + { chat_id } }` to each member via Dendrite `/events/push`. Return `202` immediately; do the fan-out on an executor.
- Put it in `controller/PluginEventsController` + `service/PluginEventsService`.

## Clients to add in registry
| Client | Method | Target |
|---|---|---|
| `RuntimeClient` | `reload(pluginId, chatId, branchId)` | runtime `POST /internal/plugins/{id}/reload` `{ chat_id, branch_id }` (E4) |
| `InstallationClient` | `chatsForBranch(branchId)` | installation `GET /internal/installations?branch_id=` (E6) |
| `IntegrationClient` | `pushPluginEvent(type, chatIds, content)` | integration `POST /api/v1/plugin-events/push` |

Config: `services.runtime.base-url=${RUNTIME_SERVICE_URL:http://localhost:8084}`; add `RUNTIME_SERVICE_URL` to the registry block in compose. RestClient timeouts: connect 1 s, read 2 s.

## Where the code goes
- `controller/EditorController` (`/api/v1/plugins/{id}/branches/{bid}`: `GET /edit`, `PUT /code`, `POST /reload`).
- `service/EditorService`, `service/ReloadNotifier`, `service/LuaSyntaxChecker`.
- DTOs under `dto/api/editor/`.
- Repository: `BranchRepository.lockForUpdate(bid)`, `updateShas(bid, clientHex, serverHex)`.

## Request size / compression
The Gateway decodes `Content-Encoding: gzip|br` and enforces the 2.2 MB decoded cap (E8), so Registry always receives plain JSON. Registry still enforces the 500 000-byte per-side limit itself, and as a backstop rejects any body over 2.2 MB with `413 body_too_large` (check `request.getContentLengthLong()` in a small filter on `PUT …/code`).

## Tests
- Save client only → no Runtime call, `client_changed=true`, notifier invoked, `reloaded=true`.
- Save server only → Runtime called once; 429 → `reloaded=false, retry_after_ms=2100`; notifier not invoked.
- Save both with one stale `base_sha` → 409, nothing written (verify no `BlobStore.put`, no update).
- 500 001-byte server text → 413 `side=server`; 250 000 Cyrillic chars (500 000 bytes) → accepted.
- Identical text re-sent → no write, `client_changed=false`, no reload.
- Lua syntax error → saved, warning with `line`.
- Non-author → 404; RELEASED branch id → 409 `not_dev_branch`.
- `runtime` field → 422.
- Concurrency: two saves with the same `base_sha` in parallel → exactly one 200, one 409 (integration test with real DB).
- integration `plugin-events/push`: two chats, members resolved, one push per member with `chat_id` added.

## Acceptance criteria
- [ ] Editor round-trip works via curl through Dendrite (after E8 routes): GET edit → PUT code → shas change.
- [ ] Lua change triggers exactly one Runtime reload; JS change triggers none and pushes `fstick.plugin.reloaded` to every member of every chat running that dev branch.
- [ ] Two saves within 3 s with Lua changes → second returns `reloaded:false` + `retry_after_ms`; `POST …/reload` after the delay → `reloaded:true`.

## Pitfalls
- `runtime` editing: the stdlib has no versions yet (`backend_stdlib.lua` is a single file). Reject any `runtime` key with 422 until a versioned stdlib exists; when it does, treat a `runtime.server` change as a server change.
- Don't compute "changed" from `base_sha` — compute from the stored sha vs the new text's hash.
- Keep the Runtime call **outside** the DB transaction (commit first), otherwise a slow Runtime holds the row lock.
