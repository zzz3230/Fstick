# Plugin Editor — Implementation Guide (shared)

This folder turns the design note [`../plugin-editor-integration.html`](../plugin-editor-integration.html) (revision 2026-09-27) into implementation tasks. Read this file first; every epic file assumes it.

| File | Epic | Services touched |
|---|---|---|
| [E1-identity.md](E1-identity.md) | Identity: `internal_uuid` everywhere | integration, gateway, registry, installation, runtime |
| [E2-registry-branches.md](E2-registry-branches.md) | Branches, content-addressed blobs, catalog | registry |
| [E3-editor-code-api.md](E3-editor-code-api.md) | Editor bootstrap, save, hot-reload, `reloaded` event | registry (+ runtime, installation, integration calls) |
| [E4-runtime.md](E4-runtime.md) | Branch resolution, engine cache, reload, state migrator, state PUT | runtime |
| [E5-console.md](E5-console.md) | Console streaming (`print`/`warn` → author) | runtime, integration |
| [E6-installation.md](E6-installation.md) | Branch-based installs, debug-install rule, PATCH, internal resolve | installation |
| [E7-moderation.md](E7-moderation.md) | Publish, review queue, claim/approve/reject/cancel | registry, integration |
| [E8-routing.md](E8-routing.md) | Dendrite `/fstick` proxy + Gateway routes, `/me`, enrichment, compression | dendrite, gateway |
| [E9-frontend.md](E9-frontend.md) | Editor UI, console, state panel, sync handlers, management + review views | element-web |
| [E10-blob-gc.md](E10-blob-gc.md) | Deferred: garbage collection of unreferenced blobs | registry |

---

## 1. Ground rules for this work

- **No backward compatibility.** There is no production database and no legacy plugin. Change schemas in place, delete obsolete tables/endpoints/DTOs, and recreate local volumes (`docker compose down -v && docker compose up --build`). Do **not** write data migrations, fallbacks for old S3 keys, or `files[0]` compatibility paths.
- **The design note is the contract.** Endpoint shapes, status codes and event payloads in the epic files are copied from §10–§11 of the note. If an epic file and the note disagree, raise it — don't pick silently.
- **Delete what you replace.** When an epic replaces an endpoint (e.g. presigned `code/server`), remove the old controller method, service method, DTO, Feign/RestClient method and test in the same PR.
- One epic ≈ one PR (large epics may split by feature, listed in each file). Every PR updates the service's `openapi/*.yaml` when it has one.

---

## 2. Architecture as it exists today

### 2.1 Request path (browser → service)

```
element-web ──HTTP (Bearer matrix token)──▶ Dendrite  /fstick/api/v1/...        (Go, fstickapi/routing)
                                              │ authenticateAndGetUserID → MXID
                                              │ proxyToGateway(..., X-User-Id: @user:domain)
                                              ▼
                                           gateway-service :8085                (explicit @RestController per route)
                                              │ PluginPlatformProxyService.forward(...)
                                              ▼
                     registry :8082 · installation :8081 (host 8083) · runtime :8084
```

- **Every browser-facing route must be registered twice**: in Dendrite (`fstickbackend/dendrite/fstickapi/routing/routing.go` + a handler in `proxy_handlers.go`) *and* in Gateway (`PluginMarketplaceController` + `PluginPlatformProxyService`). Forgetting Dendrite gives a 404 from the homeserver. E8 owns this wiring; the other epics list the routes they need.
- The frontend base URL is `SdkConfig.get("fstick_marketplace_api_url")` with the `/plugins|/registry...` suffix stripped (see `resolveApiBase()` in `src/fstick/DslTopBarSlot.tsx`). Registry routes are exposed under `/registry/plugins/...` by Dendrite; runtime/installation routes without the prefix.

### 2.2 Event path (service → browser)

```
service ─▶ integration-service  POST /api/v1/events/push | /plugin-state/broadcast
             │ FstickProxyService → Dendrite POST /fstick/api/v1/events/push { user_id: MXID, type, content }
             ▼
         Dendrite FstickEventStore → Matrix /sync response field `fstick_events`
             ▼
         element-web src/fstick/syncInterceptor.ts → window CustomEvent("fstick:sync-event", {type, content})
```

New event types (`fstick.plugin.console`, `fstick.plugin.reloaded`, `fstick.dev.notification`) reuse this path unchanged.

### 2.3 Services

| Service | Path | Build | Java | Persistence | JSON naming |
|---|---|---|---|---|---|
| registry | `fstickbackend/registry-service` | Gradle | 17 | Postgres `registry-db` (JdbcTemplate, schema in `database/db/init.sql`), MinIO | `SNAKE_CASE` (global) |
| installation | `fstickbackend/installation-service` | **Maven** (`pom.xml`) | — | Postgres `installation-db` (init script `installation service database/installation service database.sql`), Redis | **camelCase** today → switch to `SNAKE_CASE` (E6) |
| runtime | `fstickbackend/runtime-service` | Gradle | 21 | Redis `runtime-redis` (state), luaj 3.0.1 | Jackson default |
| integration | `fstickbackend/integration-service` | Gradle | 21 | none today; `integration-db` container already exists in compose → used by E1 | `SNAKE_CASE` |
| gateway | `fstickbackend/gateway-service` | Gradle | 17 | none (`gateway-db` exists, unused) | pass-through strings |
| Dendrite | `fstickbackend/dendrite/fstickapi` | Go | — | — | — |
| frontend | `fstickfrontend/element-web/apps/web/src/fstick` | pnpm/TS | — | — | — |

Schema changes for registry/installation go into their init SQL files (they run only on an empty volume → `docker compose down -v`). Integration uses `spring.sql.init` with `schema.sql` (E1) because its container has no init mount.

---

## 3. Conventions (apply to every epic)

### 3.1 Identifiers
- **`internal_uuid`** (UUID) is the only user id inside services. It arrives in `X-User-Id` (after E1). It may be returned to the client as an opaque id but is never rendered.
- **MXID** (`@user:domain`) exists only in Dendrite, integration-service and the Gateway's mapper cache. Services never store or return MXIDs; the Gateway adds `*_mxid` fields to responses (E8).
- **sha** values are written `sha256:<64 lowercase hex>` in APIs; the S3 key uses the bare hex: `plugins/{pluginId}/blobs/{hex}`.
- `chat_id` is the Matrix room id (`!abc:domain`) — a string, not a UUID.

### 3.2 HTTP
- Error body everywhere: `{ "error": "<snake_code>", "message": "<human>", ...extra }`.
  Status codes: `400` malformed · `401` bad token · `403` not the author / not a moderator (on something the caller can see) · `404` unknown **or not visible** (a hidden branch is always 404, never 403) · `409` state conflict · `413` too large · `422` semantic validation · `429` throttled.
  Each Spring service gets (or extends) a `@RestControllerAdvice` that maps a small `ApiException(status, code, message, extra)` to this body. installation-service already has `GlobalExceptionHandler` — extend it; add one to registry, runtime and integration.
- **`/internal/**` routes**: service-to-service only. Never proxied by the Gateway or Dendrite, not gated by `X-User-Id`, reachable only on `fstick-network`. Put them in separate controllers (`Internal*Controller`) so the separation is visible.
- Moderation transitions are retry-safe: repeat into the current state → `200` with the current row; any other invalid transition → `409`.
- New endpoints use `snake_case` JSON.

### 3.3 Events (sync channel)
| type | producer | recipients | epic |
|---|---|---|---|
| `fstick.plugin.state` (existing) | runtime → integration | chat members | E1 changes projection keys |
| `fstick.plugin.console` | runtime → integration | plugin author (if still a chat member) | E5 |
| `fstick.plugin.reloaded` | registry → integration | members of every chat whose installation points at the dev branch | E3 |
| `fstick.dev.notification` | registry → integration | author | E7 (MVP replacement for the Fstick Developer room) |

### 3.4 Limits (single source of truth)
| Limit | Value | Enforced by |
|---|---|---|
| Source file | 500 000 bytes UTF-8 per side | registry (413 `file_too_large`), editor (blocks Save) |
| Decoded request body for `PUT …/code` | 2.2 MB; decoded/encoded ratio > 200 → 413 | gateway decompression filter (E8) + registry |
| State JSON | 256 KB serialized | runtime (413) |
| Reload throttle | 3 s per `(plugin, branch)` | runtime (429 `reload_throttled`, `retry_after_ms`) |
| Console line | 8 KB (truncate + `…[truncated]`) | runtime |
| Console batch | 256 lines per POST | runtime + integration |
| Console rate | ≈500 lines / 10 s per `(plugin, chat)` | runtime token bucket |
| Branch-resolution cache | 60 s TTL | runtime |
| Reject reason | ≤ 2000 chars | registry (422) |

### 3.5 Defaults
- Runtime strings: `cl.js@1.0.0`, `sv.lua@1.0.0`.
- Templates for a new plugin: `registry-service/src/main/resources/templates/client.js` and `server.lua` (E2).
- Template icon: `registry-service/src/main/resources/templates/icon.png`, served when `s3_icon_key` is null (E2).

---

## 4. Epic dependency graph and order

```
E1 Identity ─┬─▶ E6 Installation ─┐
             ├─▶ E2 Registry ─────┼─▶ E3 Editor API ─▶ E9 Frontend
             │                    ├─▶ E4 Runtime ─────▶ E5 Console
             │                    └─▶ E7 Moderation
             └─▶ E8 Routing (grows with every epic; land its skeleton with E1)
E10 Blob GC: after E3/E7, optional.
```

Suggested sequence: **E1 → E2 → E6 → E4 → E3 → E5 → E7 → E9**, with E8 changes shipped inside each PR that adds a browser-facing route. E9 can start against mocked APIs once E2/E3 contracts are merged.

---

## 5. Known pitfalls in the current code (fix where your epic touches them)

1. **Dendrite proxy loses the body on retry** (`proxy_handlers.go`, `proxyToGateway`): the retry builds `http.NewRequest(r.Method, target, nil)`. Any retried POST/PUT is sent empty. E8 buffers the body before the first attempt.
2. **Dendrite `/fstick/api/v1/events/push`, `/chats/...` are unauthenticated** and reachable from outside. They are service-facing. Not in scope to fix, but don't add more unauthenticated routes there; new service-facing logic lives in integration-service.
3. **`LuaPluginEngine` is not thread-safe.** `changeContext()` mutates shared `globals`; two chats executing on the same cached engine interleave. E4 serialises per engine.
4. **Runtime swallows Lua load errors** (`initPlugin` logs and continues) — E4/E5 turn them into a broken-engine state plus a system console line.
5. **`IntegrationServiceClient.instance`** is a static singleton used from inside Lua callbacks. Keep the pattern for `_sendMessage`; for console, pass a sink object into the engine instead of adding another static.
6. **installation-service requires chat _admin_** to install/uninstall (`checkChatAdmin`). The design note's "chat member" wording is loose; keep admin for install/PATCH/uninstall, member for reads.
7. **Frontend reads installations in camelCase and registry in snake_case** (`src/fstick/dsl/loader.ts`). E6 moves installation to snake_case; E9 updates the loader in the same release.

---

## 6. Definition of done (every epic)

- [ ] Code + unit tests (JUnit 5 + Mockito, same style as existing `*ServiceTest`), including the failure cases listed in the epic.
- [ ] OpenAPI yaml of the touched service updated.
- [ ] Obsolete endpoints/DTOs/tests removed.
- [ ] `docker compose down -v && docker compose up --build` starts cleanly; the epic's manual smoke test passes.
- [ ] Browser-facing routes wired in Dendrite **and** Gateway (E8 checklist).
- [ ] No MXID stored or returned by registry/installation/runtime.

## 7. Decisions made while planning (not in the design note yet)

| # | Decision | Where |
|---|---|---|
| D1 | No migration/backfill of any kind; schemas are rewritten and volumes recreated. Seed plugins in `registry init.sql` are removed. | all |
| D2 | The state schema already exists (`SetStateSchema` / `state_schema` in `backend_stdlib.lua`). The migrator is a new stdlib function `MigrateState` and runs **before every command**, which is the lazy migration for chats other than the reload's `chat_id`. | E4 |
| D3 | Runtime state PUT lives at runtime `/plugins/{id}/state` (next to the GET); browser path `/fstick/api/v1/plugins/{id}/state`. | E4, E8 |
| D4 | Registry does a compile-only Lua syntax check (luaj) to produce save warnings; it never executes plugin code. | E3 |
| D5 | `runtime` changes in `PUT …/code` are rejected with `422 runtime_change_unsupported` until a versioned stdlib exists. | E3 |
| D6 | New sync event `fstick.plugin.installation_changed` (install / PATCH / uninstall) so every client in the chat reloads its plugin list. | E6, E9 |
| D7 | Author notifications ship as the sync event `fstick.dev.notification`; the real "Fstick Developer" room needs a new Dendrite endpoint and is a follow-up. The integration API `POST /api/v1/dev-room/notify` is final either way. | E7 |
| D8 | Moderators are bootstrapped from `registry.moderators.bootstrap` (uuids) plus internal grant/revoke endpoints. The UI detects moderators by probing the queue (403 → no tab). | E7, E9 |
| D9 | `plugins.last_rejection` is cleared by the next successful approve. | E7 |
| D10 | Separate editor window = `window.open("")` + React portal from the opener (shares the Matrix client; no token handed over at all). `postMessage` stays the fallback for a standalone page. | E9 |
| D11 | The template icon is a presigned MinIO URL of `templates/icon.png` (uploaded on Registry startup), because `<img>` can't send the Bearer token that `/fstick` routes require. | E2 |
| D12 | Installations store `branch_status` and `plugin_author_id` at install time (both immutable for a given branch/plugin), so `/internal/installations/resolve` never calls Registry. | E6 |
| D13 | Install/PATCH/uninstall keep requiring chat **admin** (current behaviour); reads require membership. | E6 |

## 8. End-to-end smoke test (after all epics)

1. Log in as A, open a room where A is admin. Marketplace → *Create plugin* (no icon) → plugin appears under *My plugins* with template code.
2. Install its dev branch into the room → the top-bar slot renders the template; **Edit** is visible to A only.
3. Open the editor (right panel), change Lua `print("hi")` in a command, Save → `reloaded: true`; run the command from the slot → console shows `hi`.
4. Change JS only, Save → no Runtime call; every open client in that room re-renders with the new `cl.js`.
5. Save twice within 3 s with Lua changes → second save returns `reloaded: false`; editor auto-retries the hot-reload after `retry_after_ms`.
6. Edit state in the state panel, submit → slot updates via `fstick.plugin.state`.
7. Publish `1.0.0` → moderator M (row in `registry.moderators`) sees it in the queue, claims, approves → plugin appears in the public marketplace; B installs `1.0.0` in another room.
8. Publish `1.1.0`, M rejects with a reason → A sees *needs changes* in *My plugins*; publish again works.
