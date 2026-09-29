# E1 — Identity: `internal_uuid` everywhere

> Design note: §3 (Identity model), §9 Integration/Gateway, §10 *Integration — identity*, *Gateway `GET /api/v1/me`*. Read [00-overview.md](00-overview.md) first.

## Goal
Every Fstick service sees the user as an `internal_uuid`. The only place MXIDs live is the `uuid ↔ mxid` table in integration-service (Postgres) plus caches in the Gateway. This unblocks ownership checks (`author_id == X-User-Id`) and author-targeted pushes (console, notifications).

## Depends on / blocks
- Depends on: nothing.
- Blocks: E2 (author checks), E5 (console recipient), E6 (installed_by), E7 (moderators), E8 (`/me`, enrichment).

## Scope
**In:** mapper table + API in integration; Gateway header swap; all integration inbound APIs switched to uuid; state projection keyed by uuid; `installed_by` → UUID; remove `resolveAuthorId`.
**Out:** response enrichment (`*_mxid`) and `/api/v1/me` routing — those are E8 (but the Gateway cache built here is what E8 uses).

## Current state (what you are changing)
| Where | Today |
|---|---|
| `dendrite/fstickapi/routing/proxy_handlers.go` `proxyToGateway` | sets `X-User-Id: @user:domain` (MXID). **Stays as is.** |
| `gateway-service` `PluginMarketplaceController` | reads `X-User-Id` and forwards it verbatim via `PluginPlatformProxyService.forward(...)`. |
| `registry-service` `PluginsController.resolveAuthorId` | `UUID.nameUUIDFromBytes(mxid)`. |
| `installation-service` `installations.installed_by` | `VARCHAR(100)` holding the MXID; `IntegrationClient.isMember/isAdmin(userId, chatId)` passes it to integration. |
| `integration-service` | stateless; `FstickProxyService.getChatMember(chatId, userId)` / `pushEvent(user_id)` / `broadcastPluginState` all use MXIDs (Dendrite's ids). `prepareStateForUser` keys `user_scoped` maps by member MXID. |
| `runtime-service` | `X-User-Id` → `PluginRuntimeContext.senderId` → Lua global `_user_id`; `user_scoped` state maps are keyed by it. |

## Rule after this epic
> **Every integration-service endpoint takes `internal_uuid`s**, except `POST /api/v1/identity/resolve` and `resolve-batch` (which take MXIDs). integration converts to MXID only at the moment it calls Dendrite, and converts Dendrite member lists back to uuids before returning them.

---

## Feature 1.1 — Identity store in integration-service

### Dependencies (`integration-service/build.gradle`)
```gradle
implementation 'org.springframework.boot:spring-boot-starter-jdbc'
runtimeOnly 'org.postgresql:postgresql'
```

### Config (`application.properties` + compose env already present)
```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5436/integration_db}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:postgres}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:postgres}
spring.sql.init.mode=always
```
Remove `SPRING_JPA_HIBERNATE_DDL_AUTO` from the integration block in `docker-compose.yml` (no JPA). Add `depends_on: integration-db: condition: service_healthy` (already present).

### Schema — `src/main/resources/schema.sql`
```sql
CREATE TABLE IF NOT EXISTS identities (
    internal_uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mxid          VARCHAR(255) NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```
(`gen_random_uuid()` is built into Postgres 13+; no extension needed.)

### Code
- `repository/IdentityRepository` (JdbcTemplate):
  - `UUID resolve(String mxid)` — one statement, race-safe:
    ```sql
    WITH ins AS (
      INSERT INTO identities (mxid) VALUES (?) ON CONFLICT (mxid) DO NOTHING
      RETURNING internal_uuid, true AS created)
    SELECT internal_uuid, created FROM ins
    UNION ALL
    SELECT internal_uuid, false FROM identities WHERE mxid = ?
    LIMIT 1;
    ```
  - `Map<String, UUID> resolveBatch(Collection<String> mxids)` — insert missing with `INSERT … SELECT unnest(?::text[]) ON CONFLICT DO NOTHING`, then `SELECT … WHERE mxid = ANY(?)`.
  - `Optional<String> lookup(UUID)`, `Map<UUID,String> lookupBatch(Collection<UUID>)`.
- `service/IdentityService` with an in-process cache in both directions (the mapping is immutable — `ConcurrentHashMap`, no eviction needed at this scale; cap with a simple size check if you want).
- `controller/IdentityController` (`/api/v1/identity`):

| Method | Path | Body → Response |
|---|---|---|
| POST | `/resolve` | `{ "mxid": "@ilya:fstick.local" }` → `200 { "internal_uuid", "created": bool }` |
| POST | `/resolve-batch` | `{ "mxids": [...] }` → `200 { "map": { mxid: uuid } }` |
| GET | `/{internal_uuid}` | → `200 { "mxid" }` · `404 unknown_user` |
| POST | `/lookup-batch` | `{ "uuids": [...] }` → `200 { "map": { uuid: mxid } }` (unknown omitted) |

Validation: `mxid` must match `^@[^:]+:.+$` → else `400 invalid_mxid`.

### Tests
- `IdentityRepository` against Postgres Testcontainers **or** an H2 in PG mode (prefer Testcontainers if the team already uses Docker for tests): resolve twice → same uuid, `created` true then false; 20 parallel resolves of a new mxid → exactly one row.
- Controller: 400 on bad mxid, 404 on unknown uuid.

---

## Feature 1.2 — integration APIs take uuids

Change `FstickProxyService` / controllers so callers pass uuids:

| Endpoint (integration) | Change |
|---|---|
| `GET /api/v1/chats/{chatId}/members/{userId}` | `userId` is a uuid → `lookup` → MXID → Dendrite. Response `user_id` = the uuid. Unknown uuid → `404`. |
| `GET /api/v1/chats/{chatId}/members` | Dendrite returns MXIDs → `resolveBatch` → return uuids in `user_id`. |
| `POST /api/v1/events/push` (`PushEventRequest.user_id`) | uuid → MXID before calling Dendrite. |
| `POST /api/v1/plugin-state/broadcast` | Members are resolved to uuids first; `prepareStateForUser(baseState, memberUuid, …)` so `user_scoped` maps keyed by uuid (Runtime writes uuids, see 1.4) project correctly. Push uses the member's MXID. Optional `user_id` in the request is a uuid. |
| `POST /api/v1/chats/{chatId}/messages` | unchanged (plugin sender). |

Add a private helper `String mxidOf(UUID)` that throws `404 unknown_user` if missing.

### Tests
- `FstickProxyServiceTest`: broadcast with a state `{ "votes": { "<uuidA>": 1, "<uuidB>": 2 } }`, `user_scoped_fields=["votes"]`, members `@a`,`@b` → push to `@a` carries `votes: 1`.
- isMember with unknown uuid → 404 surfaced as "not a member" by installation (see 1.5).

---

## Feature 1.3 — Gateway swaps `X-User-Id`

- Add `gateway-service/src/main/java/ru/gatewayservice/identity/IdentityClient.java` (RestClient to `${INTEGRATION_SERVICE_URL}` — env var already set in compose; add `services.integration.base-url=${INTEGRATION_SERVICE_URL:http://localhost:8080}` to `application.properties`).
- `identity/IdentityCache` — two `ConcurrentHashMap`s (`mxid→uuid`, `uuid→mxid`), filled by `resolve` and `lookup-batch`. No invalidation (mapping is immutable).
- `identity/UserIdSwapFilter extends OncePerRequestFilter`, ordered first:
  1. Read `X-User-Id`. If absent → pass through (public GETs).
  2. If it parses as a UUID → **reject with 400** (a client must not be able to spoof a uuid; Dendrite always sends an MXID). 
  3. Resolve MXID → uuid (cache, else `POST /identity/resolve`). If integration is unreachable → `503 identity_unavailable`.
  4. Wrap the request (`HttpServletRequestWrapper`) so `getHeader("X-User-Id")` / `getHeaders` return the uuid. Controllers stay unchanged and keep forwarding the header.
- Do **not** forward any `X-User-Matrix-Id`.

### Tests
- Filter unit test with a mocked `IdentityClient`: MXID in → uuid out; second call hits cache (verify one client call); uuid-shaped header → 400; client throws → 503.
- Update `PluginPlatformProxyServiceTest` only if signatures change (they shouldn't).

---

## Feature 1.4 — Registry & Runtime accept the uuid as-is

- `registry-service/.../PluginsController`: delete `resolveAuthorId`; `@RequestHeader("X-User-Id") UUID userId` (required) on `POST /plugins`. Remove `author_id` from `AddPluginRequest` (the author is always the caller). Missing/invalid header → `400`.
- `runtime-service`: no code change needed for the header, but:
  - `PluginRuntimeContext.senderId` now holds a uuid string → Lua `_user_id` is a uuid. `Types.user_id` values in plugin state are uuids from now on.
  - Update test fixtures (`USER_ID = "@user:homeserver.org"` in `PluginRuntimeServiceTest`) to uuid strings.
  - Update sample plugins in `runtime-service/src/main/resources/lua_source/*.lua` and repo-root `vote-plugin/`, `ttt-plugin/` if they compare against MXIDs or format them for display.

## Feature 1.5 — Installation stores uuids

- Init SQL (`installation service database.sql`): `installed_by UUID NOT NULL` (was `VARCHAR(100)`). E6 rewrites this file anyway — coordinate; if E6 lands first, just ensure the column is `UUID`.
- `Installation.installedBy`, DTOs: `UUID`. `@RequestHeader("X-User-Id") UUID userId` in `InstallationController`.
- `IntegrationClient.isMember/isAdmin` pass the uuid (integration now expects it). Treat integration `404 unknown_user` as "not a member" (already handled: 404 → null).
- `PendingInstallStore` stores the uuid string — no change beyond types.

---

## Acceptance criteria
- [ ] Log in as a brand-new Matrix user and call any authenticated route → a row appears in `integration_db.identities`; registry/installation logs show a uuid in `X-User-Id`.
- [ ] Two parallel first requests of a new user create exactly one identity row.
- [ ] `plugins.author_id` for a newly created plugin equals the caller's `internal_uuid`; `resolveAuthorId`/`nameUUIDFromBytes` no longer exist (`grep` clean).
- [ ] `installations.installed_by` is a UUID column.
- [ ] A `user_scoped` field in a plugin's state is projected correctly to each chat member after a command.
- [ ] Sending `X-User-Id: <uuid>` directly to the Gateway returns 400.
- [ ] integration down → cached users keep working, a new user gets 503.

## Pitfalls
- Chat ids stay MXID-domain strings (`!room:domain`) — do not run them through the mapper.
- Dendrite's member endpoint returns `user_id` as MXID; resolve in **batch**, never one call per member.
- Keep the Gateway cache process-local; don't introduce Redis just for this.
