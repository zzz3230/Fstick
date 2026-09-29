# E6 — Installation: branch-based installs, debug-install rule, PATCH, internal lookups

> Design note: §4 (installs pinned to a branch), §9 Installation, §10 *Installation*, *Installation — internal*. Read [00-overview.md](00-overview.md) first.

## Goal
An installation points at a **branch** (not a version). Anyone allowed may install a `RELEASED` branch; only the author may install the `WORKING` (dev) branch. Installs are pinned; upgrades are explicit (`PATCH`). Runtime and Registry can ask which branch a chat runs and which chats run a branch.

## Depends on / blocks
- Depends on: E1 (uuid `X-User-Id`, integration takes uuids), E2 (`GET /internal/plugins/{id}` in Registry).
- Blocks: E4 (resolve), E3 (chats for branch), E2 chat-visibility, E9.

## Current state
- `installations(installation_id, plugin_id, version_id, chat_id, installed_by VARCHAR, installed_at, updated_at, UNIQUE(plugin_id, chat_id))`.
- `InstallationService.install` → `checkChatAdmin` → `checkPluginAndVersion` (public registry `GET /api/v1/plugins/{id}`, `versions.get(0)` = latest) → warning `VERSION_OUTDATED` → pending token in Redis → `POST /confirm`.
- JSON is camelCase (no naming strategy); Maven build.

---

## Feature 6.1 — Schema & JSON

Rewrite `installation service database/installation service database.sql`:
```sql
CREATE TABLE installations (
    installation_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plugin_id        UUID NOT NULL,
    branch_id        UUID NOT NULL,
    branch_status    VARCHAR(20) NOT NULL CHECK (branch_status IN ('WORKING','RELEASED')),
    plugin_author_id UUID NOT NULL,          -- copied from Registry at install time; the author never changes
    chat_id          VARCHAR(255) NOT NULL,
    installed_by     UUID NOT NULL,
    installed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_plugin_chat UNIQUE (plugin_id, chat_id)
);
CREATE INDEX idx_installations_chat_id   ON installations (chat_id);
CREATE INDEX idx_installations_branch_id ON installations (branch_id);
```
`branch_status` is stored because a RELEASED branch never changes status and a WORKING branch stays WORKING — no Registry call is needed to resolve.

Set `spring.jackson.property-naming-strategy=SNAKE_CASE` in `application.properties`. Remove any `@JsonProperty` that only existed to force snake_case. (E9 updates `loader.ts`, which reads `installationId`/`pluginId` today.)

Rename `version_id` → `branch_id` across `Installation`, row mapper, repository, DTOs, `InstallRequest`, `PendingInstallStore`. Delete `VersionNotFoundException`.

## Feature 6.2 — Install rules (`POST /api/v1/installations?chat_id=`)
```jsonc
// request
{ "plugin_id": "…", "branch_id": "…" }        // branch_id optional → highest RELEASED semver, pinned
// 201
{ "installation_id", "plugin_id", "branch_id", "branch_status", "installed_by", "installed_at" }
// 200 pending (older release chosen) — existing confirm flow kept
{ "confirmation_token", "warnings": [ { "code": "VERSION_OUTDATED", "message": "…" } ] }
// 403 { "error": "not_chat_admin" } · 403 { "error": "debug_install_forbidden" }
// 404 { "error": "plugin_not_found" | "branch_not_found" } · 409 { "error": "already_installed" } · 422 { "error": "no_release" }
```
Algorithm:
1. `checkChatAdmin(userId, chatId)` (unchanged rule — admins install).
2. `plugin = registry.internalPlugin(pluginId)` → `GET /internal/plugins/{id}` (replace the public call in `RegistryClient`). Missing or `status != ACTIVE` → 404 `plugin_not_found`.
3. Pick the branch:
   - `branch_id` given → must belong to the plugin, else 404 `branch_not_found`.
   - absent → highest-semver `RELEASED` branch; none → 422 `no_release`.
4. By status:
   - `RELEASED` → allowed. If it isn't the highest RELEASED semver → pending + `VERSION_OUTDATED` warning (existing flow).
   - `WORKING` → `userId == plugin.author_id` else **403 `debug_install_forbidden`**.
   - anything else → **404 `branch_not_found`** (candidates/rejected/cancelled are not installable and not revealed).
5. Unique `(plugin_id, chat_id)` → 409 `already_installed` (a chat runs one branch of a plugin; switching is PATCH).
6. Insert with `branch_status`, `plugin_author_id = plugin.author_id`; notify (existing `notifyPluginInstalled`, now with `branch_id`).
7. Also push `fstick.plugin.installation_changed` to the chat (6.5).

## Feature 6.3 — `PATCH /api/v1/installations/{id}`
```jsonc
{ "branch_id": "…" }
// 200 { "installation_id", "branch_id", "branch_status" }
// 403 not_chat_admin | debug_install_forbidden · 404 installation_not_found | branch_not_found · 422 branch_of_other_plugin
```
Same checks as install steps 1–4 against the installation's plugin; no pending/confirm (the admin chose explicitly). Update `branch_id`, `branch_status`, `updated_at`. Push `fstick.plugin.installation_changed` (6.5). Runtime picks the new branch up within its 60 s cache TTL (documented behaviour).

## Feature 6.4 — Internal endpoints (`controller/InternalInstallationController`)
| Method | Path | Response |
|---|---|---|
| GET | `/internal/installations/resolve?plugin_id=&chat_id=` | `200 { "branch_id", "branch_status" }` · `404 not_installed` |
| GET | `/internal/installations?branch_id=` | `200 { "chat_ids": [ … ] }` (empty array if none) |

No `X-User-Id`, no membership checks.

## Feature 6.5 — List response and change notifications
- `GET /api/v1/installations?chat_id=` items gain `branch_id`, `branch_status`, `author_id` (= `plugin_author_id`), keep `installed_by`, `installed_at`. The frontend shows **Edit** when `branch_status == "WORKING" && author_id == me.internal_uuid` (E9). The Gateway adds `author_mxid` / `installed_by_mxid` (E8).
- `fstick.plugin.installation_changed`: after install, PATCH and uninstall call integration `POST /api/v1/plugin-events/push` (built in E3) with `{ type: "fstick.plugin.installation_changed", chat_ids: [chatId], content: { plugin_id, installation_id, branch_id | null } }` so every member's client reloads its plugin list (today only the installing client dispatches `fstick:plugins-changed` locally).

## Tests (extend `InstallationServiceTest`)
- Install RELEASED latest → 201; older RELEASED → pending with `VERSION_OUTDATED`; confirm → 201.
- Install WORKING by author → 201; by non-author admin → 403 `debug_install_forbidden`.
- Install candidate/rejected id → 404; branch of another plugin → 404.
- No `branch_id` and no release → 422.
- PATCH RELEASED → WORKING by non-author → 403; to a branch of another plugin → 422.
- Resolve → correct branch; unknown → 404. `chats for branch` → all chats.
- JSON contract test: response keys are snake_case.

## Acceptance criteria
- [ ] Author installs the dev branch of a new (unreleased) plugin in their room; another admin can't.
- [ ] `GET /internal/installations/resolve` answers without calling Registry.
- [ ] PATCH moves a chat from `1.0.0` to `1.1.0`; every open client in that chat reloads the plugin.
