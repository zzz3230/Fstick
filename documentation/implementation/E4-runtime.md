# E4 — Runtime: branch resolution, engine cache, reload, state migrator, state PUT

> Design note: §7 (Runtime), §10 *Runtime*. Read [00-overview.md](00-overview.md) first.

## Goal
Runtime runs the code of the **branch installed in the chat**, keeps one compiled engine per `(pluginId, branchId)`, rebuilds it on demand through a single load path, preserves and normalises Redis state across reloads, and lets the author overwrite state from the editor.

## Depends on / blocks
- Depends on: E2 (`/internal/plugins/{id}`, `/internal/plugins/{id}/code/server`), E6 (`/internal/installations/resolve`), E1 (`X-User-Id` uuid).
- Blocks: E3 (reload endpoint), E5 (console hooks live in the engine), E9 (state panel).

## Current state
- `PluginRuntimeService.executeCommand`: `Map<UUID, LuaPluginEngine>` keyed by pluginId, created lazily, never invalidated; source from `PluginRegistryService.getPluginSource` (`versions.get(0)` → presigned `code/server` → `Downloader`).
- `LuaPluginEngine`: one `Globals` per engine; `changeContext` mutates globals per command (**not thread-safe**); `initPlugin` swallows `LuaError`.
- `StateProviderService`: Redis key `state:{pluginId}:{chatId}`.
- `backend_stdlib.lua`: `SetStateSchema` / global `state_schema`, `Types.*`, `generate_minimal_data`, `ValidateTableSchema`, `ExecuteCommandHandler`. **No migrator.**
- Feign `PluginRegistryClient` (registry base url `${registry.service.url}`); RestClient `IntegrationServiceClient`.

---

## Feature 4.1 — Branch resolution

- New Feign client `InstallationClient` (`url = "${installation.service.url:http://localhost:8081}"`):
  `GET /internal/installations/resolve?plugin_id=&chat_id=` → `{ "branch_id": UUID, "branch_status": "WORKING" | "RELEASED" }`, 404 when not installed.
  Add `INSTALLATION_SERVICE_URL: http://installation-service:8081` + property to runtime config.
- `service/BranchResolver`: Caffeine cache `(pluginId, chatId) → Resolution`, `expireAfterWrite(60 s)`, max 10 000. 404 → throw `ApiException(404, "plugin_not_installed")` and **do not cache** the miss.
- Add dependency `com.github.ben-manes.caffeine:caffeine`.

## Feature 4.2 — Engine cache and `loadEngine`

```java
record EngineKey(UUID pluginId, UUID branchId) {}

class EngineCache {
  Cache<EngineKey, LoadedEngine> cache = Caffeine.newBuilder()
      .expireAfterAccess(Duration.ofMinutes(30)).maximumSize(500).build();

  LoadedEngine get(EngineKey k)  { return cache.get(k, this::build); }       // cold path
  LoadedEngine rebuild(EngineKey k) { LoadedEngine e = build(k); cache.put(k, e); return e; } // reload path
  private LoadedEngine build(EngineKey k) {
      CodeDto code = registry.internalServerCode(k.pluginId(), k.branchId()); // GET /internal/plugins/{id}/code/server?branch_id=
      return LoadedEngine.compile(k, code.text(), code.sha());
  }
}
```
- `LoadedEngine` = `{ LuaPluginEngine engine (nullable), String serverSha, LuaError loadError (nullable), ReentrantLock lock }`.
  - Compile failure (`LuaError` from `globals.load(src).call()`) → `engine=null, loadError=e`. Commands on it return `RUNTIME_ERROR` with message `"plugin failed to load: <msg>"` and emit a system console line (E5). It stays cached until the next reload — no retry storm.
- **Thread safety:** every command, reload-migration and state PUT takes `loadedEngine.lock` for the whole `changeContext → execute → read state` sequence. Different engines run in parallel.
- `rebuild` compiles **before** `put`: the old engine keeps serving (under its own lock) until the swap. Concurrent cold `get` for the same key is de-duplicated by Caffeine.
- Registry Feign additions: `GET /internal/plugins/{id}` → `{ id, status, author_id, branches[] }`; `GET /internal/plugins/{id}/code/server?branch_id=` → `{ text, sha }`.
- Delete: `PluginRegistryService.getPluginSource`, `Downloader`, `CodeLinksResponse`, `FileDownloadData`, `VersionDto`, the old Feign methods, and `luaPluginEngines` map.

## Feature 4.3 — Command execution (rewrite `PluginRuntimeService.executeCommand`)
```
res     = branchResolver.resolve(pluginId, chatId)                  // 404 if not installed
loaded  = engineCache.get(new EngineKey(pluginId, res.branchId))
lock(loaded):
   if loaded.loadError → emit system console line (E5, dev branch only); return RUNTIME_ERROR
   state = stateProvider.getState(pluginId, chatId)
   engine.changeContext(new PluginRuntimeContext(pluginId, res.branchId, res.status, state, chatId, userId, name, trackId))
   result = engine.executeCommand(name, args)     // stdlib migrates state first (4.5)
   state.setValue(engine.getStateValue()); stateProvider.commitState(state)
   userScoped = engine.extractUserScopedFields()
broadcast (outside the lock)
```
`PluginRuntimeContext` gains `branchId`, `branchStatus`, `commandName`, `trackId` (E5 needs them). The public `POST /command` body is unchanged (`{ name, plugin_id, chat_id, track_id, args }`) — **the client never sends a branch id**.

## Feature 4.4 — Reload endpoint

`controller/InternalRuntimeController`:
```
POST /internal/plugins/{pluginId}/reload      body { "chat_id": "…", "branch_id": "…" }
200 { "reloaded": true }
429 { "error": "reload_throttled", "message": "…", "retry_after_ms": 2100 }
```
- Throttle: `ConcurrentHashMap<EngineKey, Long lastReloadAt>`; if `now - last < 3000` → 429 with `retry_after_ms = 3000 - (now - last)`. Update `last` only on an accepted reload (use `compute` for atomicity).
- `engineCache.rebuild(key)`. A compile error is still `200 reloaded:true` — the engine is current (broken); the error reaches the author via the console (E5) and Registry's syntax warning (E3).
- Eager migration for `chat_id`: if `branchResolver.resolveFresh(pluginId, chatId)` (bypass cache) returns this `branch_id`, lock the new engine, load state, run `MigrateState` (4.5), commit, broadcast. If the chat no longer runs this branch → skip silently.
- Invalidate nothing else: branch-resolution cache is independent (§7).

## Feature 4.5 — State migrator (Lua stdlib)

Add to `backend_stdlib.lua`:
```lua
-- returns migrated_data, warnings (array of { path = "a.b", message = "…" })
function MigrateState(schema, data)
```
Rules (lenient, matches "save broken code"):
| Situation | Action | Warning |
|---|---|---|
| `data == nil` / not a table at top | `generate_minimal_data(schema, true)` | `"state reset to defaults"` (only if data was non-nil) |
| field missing | default via `generate_minimal_data(fieldSchema)` | `"missing field defaulted"` |
| key not in schema (object/top level) | dropped | `"unknown key dropped"` |
| `int` given float | `math.floor` | `"coerced float to int"` |
| `int`/`float` given numeric string | `tonumber` | `"coerced string to number"` |
| `string` given number/boolean | `tostring` | `"coerced to string"` |
| `enum` value not in `values` | first value | `"invalid enum value replaced"` |
| other type mismatch | default | `"type mismatch, defaulted"` |
| `list`/`map` | recurse per element with inner/value type; invalid elements dropped | per element |
| `user_scoped` | map of userId → object; recurse on each value with `fields` | per user |

Wire it in `execute_given_command_handler`: replace
```lua
local state_data = _loadState()
if state_data == nil then state_data = generate_minimal_data(StateSchema, true) end
```
with
```lua
local state_data, _ = MigrateState(StateSchema, _loadState())
```
so every command runs on normalised state (this is the "lazy migration" for chats other than the reload's `chat_id`; no bookkeeping needed).

Expose to Java: `LuaPluginEngine.migrate(Object javaState) → MigrationResult(Object state, List<Warning> warnings)` calling `MigrateState(StateSchema-or-state_schema, javaToLua(state))`. Make sure the `state_schema` global auto-sync in `ExecuteCommandHandler` also happens here (factor out `CurrentSchema()`).

Unit-test the migrator from Java through `LuaPluginEngine` (load a tiny schema, feed states, assert result + warnings).

## Feature 4.6 — `PUT /plugins/{id}/state?chat_id=` (browser path `/fstick/api/v1/plugins/{id}/state`, see E8)
Add it to `PluginExecuteController` next to the existing `GET /plugins/{pluginId}/state`.
```jsonc
// request (full state, not a patch)
{ "state": { "counter": 5 } }
// 200
{ "state": { … }, "warnings": [ { "path": "votes.x", "message": "unknown key dropped" } ] }
// 400 { "error": "invalid_json" } · 403 { "error": "not_author" | "not_dev_install" } · 404 plugin_not_installed · 413 state_too_large
```
Steps: size check on raw body (`> 256 * 1024` → 413; read body as `String` then parse with Jackson so malformed JSON maps to `invalid_json`) → `res = branchResolver.resolveFresh(...)`; `res.status != WORKING` → 403 `not_dev_install` → author check: `AuthorCache.authorOf(pluginId)` (E5 creates it; if E5 isn't merged yet, create it here: Caffeine `pluginId → author_id` from `/internal/plugins/{id}`, no expiry) `!= X-User-Id` → 403 `not_author` → lock engine → `migrate(state)` → commit to Redis → broadcast (`IntegrationServiceClient.broadcastPluginState`) → return. No optimistic lock (last write wins).

`GET /plugins/{id}/state?chat_id=` stays as is.

## Config summary (`application.yml` / `.properties` + compose)
```properties
installation.service.url=${INSTALLATION_SERVICE_URL:http://localhost:8081}
registry.service.url=${REGISTRY_SERVICE_URL:http://localhost:8082}
runtime.reload.min-interval-ms=3000
runtime.branch-cache.ttl-seconds=60
runtime.engine-cache.max-size=500
runtime.engine-cache.idle-minutes=30
runtime.state.max-bytes=262144
```

## Tests
- BranchResolver: cached for 60 s; 404 not cached.
- EngineCache: two branches of one plugin → two engines; `rebuild` replaces; concurrent `get` compiles once (count Feign calls).
- Command on a broken engine → `RUNTIME_ERROR`, no Redis write.
- Two threads executing commands on the same engine with different chats → states don't leak between chats (regression test for the thread-safety bug).
- Reload: second call within 3 s → 429 with `retry_after_ms` in (0, 3000]; after 3 s → 200. Eager migration only when the chat runs that branch.
- Migrator table: one test per row.
- State PUT: 400 invalid JSON, 413 size, 403 not author, 403 RELEASED install, 200 with warnings + Redis written + broadcast called.
- Update existing `PluginRuntimeServiceTest` / `PluginRegistryServiceTest` (delete the latter with the class).

## Acceptance criteria
- [ ] A RELEASED install in chat X and the dev install in chat Y of the same plugin run different code simultaneously.
- [ ] Reload swaps the engine without a request failing mid-way; state survives and gets normalised to the new schema.
- [ ] Removing a field from `SetStateSchema` + reload → the field disappears from Redis state for the reload's chat immediately and for other chats on their next command.
- [ ] No `versions.get(0)` / `files.get(0)` / `Downloader` left.
