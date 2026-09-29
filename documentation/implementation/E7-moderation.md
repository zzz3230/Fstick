# E7 — Moderation: publish, review queue, claim / approve / reject / cancel

> Design note: §5 (Moderation lifecycle), §9 Registry, §10 *Registry — moderation*, `dev-room/notify`. Read [00-overview.md](00-overview.md) first.

## Goal
An author snapshots the dev branch into a candidate branch; a moderator claims and approves or rejects it; the author can withdraw it. Only `RELEASED` branches become public. Every transition is logged, retry-safe, and the author is notified of the outcome.

## Depends on / blocks
- Depends on: E2 (branches, BlobStore, AccessGuard, catalog), E1.
- Blocks: public marketplace content, E9 management + review views.

## State machine
```
WORKING ──publish──▶ WAITING_APPROVE ──claim──▶ APPROVING ──approve──▶ RELEASED
                         │   │                      │  │
                         │   └──reject (any mod)──▶ REJECTED ◀──reject (claimer)──┘  │
                         └──cancel (author)──▶ CANCELLED ◀──cancel (author)──────────┘
```
`RELEASED`, `REJECTED`, `CANCELLED` are terminal and retained. At most one open candidate (`WAITING_APPROVE`/`APPROVING`) per plugin.

---

## Feature 7.1 — Schema (append to `registry-service/database/db/init.sql`)
```sql
ALTER TABLE branches
    ADD COLUMN changelog     VARCHAR(2000),
    ADD COLUMN submitted_at  TIMESTAMPTZ,
    ADD COLUMN claimed_by    UUID,
    ADD COLUMN reject_reason VARCHAR(2000);
CREATE UNIQUE INDEX uq_one_open_candidate ON branches(plugin_id) WHERE status IN ('WAITING_APPROVE','APPROVING');

ALTER TABLE plugins ADD COLUMN last_rejection JSONB;   -- { reason, at, semver, branch_id } | NULL

CREATE TABLE plugin_moderation_log (
    id         BIGSERIAL PRIMARY KEY,
    plugin_id  UUID NOT NULL REFERENCES plugins(plugin_id) ON DELETE CASCADE,
    branch_id  UUID NOT NULL REFERENCES branches(branch_id) ON DELETE CASCADE,
    action     VARCHAR(20) NOT NULL CHECK (action IN ('PUBLISH','CLAIM','APPROVE','REJECT','CANCEL')),
    actor_id   UUID NOT NULL,
    reason     VARCHAR(2000),
    at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE moderators (
    internal_uuid UUID PRIMARY KEY,
    granted_by    UUID,
    granted_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```
(Since there is no data to preserve, you may instead fold these columns straight into the `CREATE TABLE` statements from E2 — preferred.)

## Feature 7.2 — Moderator role
- `service/ModeratorService.isModerator(uuid)` (cached 60 s).
- Bootstrap: property `registry.moderators.bootstrap=` (comma-separated uuids) inserted on startup with `ON CONFLICT DO NOTHING`. Workflow for a dev env: log in once → `GET /api/v1/me` (E8) → put the uuid in `REGISTRY_MODERATORS_BOOTSTRAP` → restart registry.
- `POST /internal/moderators { "internal_uuid", "granted_by" }` and `DELETE /internal/moderators/{uuid}` for ops scripts (internal only).
- Every moderation endpoint: not a moderator → `403 not_moderator`.
- E2's visibility rules use `isModerator` for candidates.

## Feature 7.3 — Endpoints (`controller/ModerationController`, `service/ModerationService`)

All mutations run in one transaction with `SELECT … FOR UPDATE` on the branch row, append a log row, and apply the **retry rule**: repeat into the current state → `200` with the current row; other invalid transition → `409 invalid_transition`.

### `POST /api/v1/plugins/{id}/publish` — author
```jsonc
{ "source_branch_id": "<dev branch>", "semver": "1.2.0", "changelog": "…" }
// 201 { "branch_id", "semver", "status": "WAITING_APPROVE" }
// 409 open_candidate_exists · 422 invalid_semver | semver_not_greater | changelog_too_long · 404 · 403 not_author
```
- `source_branch_id` must be this plugin's `WORKING` branch (else 422 `not_dev_branch`).
- `semver` matches `^\d+\.\d+\.\d+$` and is **strictly greater** than the highest `RELEASED` semver (none → any).
- Insert a new branch: `status=WAITING_APPROVE`, copy both shas + runtime strings, `base_branch_id=source`, `semver`, `changelog`, `submitted_at=now()`. Blobs are shared (content-addressed) — no copy in S3.
- The partial unique index turns a race into a constraint violation → map to 409.

### `GET /api/v1/moderation/branches?status=WAITING_APPROVE|APPROVING&page=&limit=` — moderator
```jsonc
{ "items": [ { "plugin_id", "plugin_name", "branch_id", "semver", "changelog", "status",
               "author": { "id": "<uuid>" },          // Gateway adds "mxid" (E8)
               "submitted_at", "claimed_by": null | "<uuid>" } ],
  "page": 0, "total": 7 }
```
Default: both open statuses, oldest `submitted_at` first.

### `POST /api/v1/plugins/{id}/branches/{bid}/claim` — moderator
`WAITING_APPROVE → APPROVING`, `claimed_by = caller`. Already `APPROVING` by caller → 200; by another → `409 claimed_by_other`.

### `POST /api/v1/plugins/{id}/branches/{bid}/approve` — the claimer
`APPROVING → RELEASED`. Not the claimer → `403 not_claimer`. Already `RELEASED` → 200. Other → 409.
Side effects: log; `plugins.last_rejection` untouched; notify author `kind=released` (7.4). The plugin now shows in the public list (E2 query).

### `POST /api/v1/plugins/{id}/branches/{bid}/reject` — moderator
```jsonc
{ "reason": "…" }   // required, 1..2000 chars → else 422 invalid_reason
```
From `WAITING_APPROVE` (any moderator) or `APPROVING` (claimer only, else `403 not_claimer`). Sets `reject_reason`, `plugins.last_rejection = { reason, at, semver, branch_id }`, log, notify `kind=rejected`. Already `REJECTED` → 200; `RELEASED`/`CANCELLED` → 409.

### `POST /api/v1/plugins/{id}/branches/{bid}/cancel` — author
From `WAITING_APPROVE` or `APPROVING` → `CANCELLED`. No `last_rejection`, no notification, log row. Already `CANCELLED` → 200; `RELEASED`/`REJECTED` → 409.

### Catalog fields (fill what E2 left `null`)
`GET /plugins?owned=true` → `candidate` (open branch or null), `last_rejection`; `GET /plugins/{id}` (author view) → all branches + `last_rejection`.
Clear `last_rejection` on the next successful `approve` of that plugin (the "needs changes" banner disappears once a release goes through).

## Feature 7.4 — Author notification (integration)

Registry → integration `POST /api/v1/dev-room/notify` (fire-and-forget, after commit):
```jsonc
{ "author_id": "<uuid>", "kind": "released" | "rejected",
  "plugin_id", "plugin_name", "semver", "reason": "…" }   // reason only for rejected
```
**MVP implementation (this epic):** integration maps `author_id → mxid` and pushes a sync event `fstick.dev.notification` with the same content to the author. E9 shows it as a toast and in *My plugins*. `plugins.last_rejection` remains the source of truth.

**Follow-up (separate ticket, needs a Dendrite endpoint):** a real "Fstick Developer" room per author — Dendrite `POST /fstick/api/v1/dev-rooms/ensure { user_id }` creating a DM from a system user, then `POST /chats/{room}/messages`. Keep the integration API unchanged so Registry doesn't care which delivery is used.

## Tests
- Publish: happy path; second publish while open → 409; semver not greater → 422; non-dev source → 422; non-author → 403.
- Claim twice by same moderator → 200/200; by another → 409.
- Approve by non-claimer → 403; approve twice → 200/200; plugin appears in public list.
- Reject from WAITING by any moderator → 200 + `last_rejection` set; from APPROVING by non-claimer → 403; reject on CANCELLED → 409; reject twice → 200/200.
- Cancel by author from both open states; cancel twice → 200/200; cancel after RELEASED → 409.
- Each transition writes exactly one log row (none on 200-retries or errors).
- Non-moderator on queue/claim/approve/reject → 403.
- Notifications: approve → `released`, reject → `rejected`, cancel → none.

## Acceptance criteria
- [ ] Full cycle publish → claim → approve makes the plugin public and installable by others (E6).
- [ ] Reject shows "needs changes" with the reason in *My plugins*; the author republishes successfully.
- [ ] The moderation log reconstructs the full history of a plugin.
