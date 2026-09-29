# E9 — Frontend: editor, console, state panel, sync handlers, management & review views

> Design note: §1, §12 (Frontend contract), §11 (events). Read [00-overview.md](00-overview.md) first.
> Code root: `fstickfrontend/element-web/apps/web/src/` (below: `src/`).

## Goal
An author edits both files of an installed dev plugin from the room (right panel or separate window), sees backend console output live, edits state, hot-reloads, and manages releases. Every client in the room picks up client-code changes. Moderators review candidates.

## Depends on
E1 (`/me`), E2, E3, E4 (state PUT), E5 (console events), E6 (snake_case installations, PATCH, `installation_changed`), E7, E8 (all routes). Can be built against mocks once contracts are merged.

## Current state
| File | Role today |
|---|---|
| `src/fstick/syncInterceptor.ts` | wraps `fetch`, dispatches `fstick:sync-event` `{ type, content }` for every `fstick_events` entry |
| `src/fstick/DslTopBarSlot.tsx` | per room: `loadPluginsForChat` → `runDsl(DSL_LIB_CODE, appCode, …)` → renders `DslRenderer`; listens to `fstick.plugin.state` and window `fstick:plugins-changed` |
| `src/fstick/dsl/loader.ts` | installations (camelCase) → registry plugin (versions) → `code/client` presigned URL → download → initial state |
| `src/fstick/dsl/transport.ts` | `BackendService`, command POST to `/plugins/{id}/command?chat_id=` |
| `src/components/views/right_panel/MarketplaceCard.tsx` + `viewmodels/right_panel/MarketplaceCardViewModel.tsx` | marketplace list/install/uninstall/create (presigned upload flow) |
| `src/stores/right-panel/RightPanelStorePhases.ts` | `RightPanelPhases.Extensions` → `MarketplaceCard` in `components/structures/RightPanel.tsx` |
| API base | `SdkConfig.get("fstick_marketplace_api_url")`, token `MatrixClientPeg.get().getAccessToken()` |

---

## Feature 9.1 — API layer & identity
- `src/fstick/api/client.ts`: `fstickFetch(path, init)` adding `Authorization`, resolving `apiBase` once (move `resolveApiBase` here from `DslTopBarSlot`/view model), parsing `{ error, message }` into a typed `FstickApiError { status, code, message, extra }`.
- Typed functions per endpoint used below (one file per area: `api/registry.ts`, `api/editor.ts`, `api/installations.ts`, `api/moderation.ts`, `api/runtime.ts`). Types mirror §10 of the note (snake_case fields).
- `src/fstick/api/me.ts`: `getMe(): Promise<{ internal_uuid, mxid }>` — fetched once per session, memoised; cleared on logout.
- `displayUser(mxid)` → `mxid.split(":")[0]` (`@ilya`). Never render uuids or full MXIDs.

## Feature 9.2 — Loader & top-bar slot on branches
Rewrite `loader.ts`:
1. `GET /installations?chat_id=` → items `{ installation_id, plugin_id, branch_id, branch_status, author_id, … }` (snake_case now).
2. For each: `GET /registry/plugins/{plugin_id}/code/client?branch_id=&chat_id=` → `{ text, sha }` (inline; keep `sha`, send `If-None-Match` on re-fetch).
3. Initial state as today.
`PluginEntry` becomes `{ installationId, pluginId, branchId, branchStatus, authorId, clientSha, appCode, initialState }`. Delete version/runtime handling and the MinIO download.

`DslTopBarSlot` additions:
- **`fstick.plugin.reloaded`** `{ plugin_id, branch_id, chat_id, client_changed }` with `chat_id === roomId`: for the matching running plugin, re-fetch `code/client` (304 → nothing), then re-run `runDsl` with the **current store state** as `initialState` (don't lose state), replace the entry. Debounce 300 ms per plugin.
- **`fstick.plugin.installation_changed`** for this room → `loadPlugins(true)` (same as the local `fstick:plugins-changed`).
- **Edit affordance**: a small pencil button in each slot header when `branchStatus === "WORKING" && authorId === me.internal_uuid`; opens the editor (9.4) for `{ pluginId, branchId, chatId: roomId }`.

## Feature 9.3 — Code editor component
Add CodeMirror 6: `@codemirror/state`, `@codemirror/view`, `@codemirror/commands`, `@codemirror/language`, `@codemirror/lang-javascript`, `@codemirror/legacy-modes` (Lua via `StreamLanguage.define(lua)`), `@codemirror/lint`. Wrap in `src/fstick/editor/CodePane.tsx` `{ language, value, onChange, diagnostics[] }`.
- Byte counter under each pane: `new TextEncoder().encode(text).length` / 500 000; red at the limit; Save disabled when any pane is over.
- Server-side warnings (`{ side, message, line }`) → CodeMirror lint diagnostics on that line.
- JS: optional client-side syntax check with `new Function(text)` inside try/catch (never execute the result) → diagnostic.

## Feature 9.4 — Editor surface (`src/fstick/editor/PluginEditor.tsx`)
Props `{ pluginId, branchId, chatId }`. Layout: toolbar · two panes (Client JS / Server Lua; tabs on narrow widths) · console · collapsible state panel.

State machine (keep it in a `usePluginEditor` hook):
1. **Load**: `GET …/branches/{bid}/edit?chat_id=` → texts + `baseSha {client, server}`. 404/409 → show "not editable" and close.
2. **Dirty tracking**: per side, `text !== loadedText`.
3. **Save** (Ctrl/Cmd+S, button): body contains only dirty sides with their `base_sha`. If the JSON body > 32 KB, gzip it with `CompressionStream("gzip")` and send `Content-Encoding: gzip`.
   - 200 → update `baseSha` + loaded texts; show warnings; if `reloaded === false` → status "reload pending", schedule **hot-reload** after `retry_after_ms` (min 3000 ms).
   - 409 `stale_code` → modal "Code changed elsewhere" with *Reload from server* (discard) / *Keep mine* (sets `base_sha` to `current` and lets the user save again — overwrite).
   - 413 → mark the pane; 422/5xx → toast.
4. **Hot-reload** button: `POST …/reload?chat_id=`; disabled for 3 s after any reload; 429 → countdown from `retry_after_ms`.
5. **Unsaved changes** guard on close/navigate.
6. When a `fstick.plugin.reloaded` arrives for this branch that this editor did not cause (compare resulting sha with `baseSha.client`), show "client code changed elsewhere — reload?".

## Feature 9.5 — Console pane (`src/fstick/editor/ConsolePane.tsx`)
- Subscribes to `FSTICK_SYNC_EVENT`, `type === "fstick.plugin.console"`; appends each `msgs[]` entry where `plugin_id === pluginId && chat_id === chatId`.
- Ring buffer 2 000 lines; toggles `info` / `warn` / `error`; *Clear*; auto-scroll unless the user scrolled up.
- Line: time (`ts`), level badge, `command_name` (if any), message (pre-wrap, monospace).
- `system: true` → distinct left border + icon + "runtime" label; never styled like the author's own output.
- No history: show "Live output since HH:MM" at the top.

## Feature 9.6 — State panel (`src/fstick/editor/StatePanel.tsx`)
- *Refresh*: `GET /plugins/{id}/state?chat_id=` → pretty JSON in a CodeMirror JSON pane.
- *Apply*: parse locally (invalid → inline error), size ≤ 256 KB, `PUT /plugins/{id}/state?chat_id=` `{ state }` → replace content with the returned effective `state`; list `warnings` (`path: message`).
- 403 `not_dev_install` → panel disabled with an explanation.

## Feature 9.7 — Two hosts: right panel and separate window
- **Right panel**: add `RightPanelPhases.PluginEditor` in `RightPanelStorePhases.ts`; extend the card state with `fstickEditor?: { pluginId; branchId; chatId }`; render `PluginEditorCard` (header + close + "pop out" button + `PluginEditor`) in `RightPanel.tsx` next to the `Extensions` case. Open via `RightPanelStore.instance.setCard({ phase: RightPanelPhases.PluginEditor, state: { fstickEditor } })`.
- **Separate window** — recommended implementation: `const w = window.open("", "fstick-editor-" + branchId, "width=1100,height=800")`, copy the opener's `<link rel=stylesheet>`/`<style>` nodes into `w.document.head`, then render `PluginEditor` into `w.document.body` with a React portal from the opener. The child window runs in the opener's JS context, so the Matrix client, access token and sync events are shared — **no token is read from storage or transferred**. Close the popup when the opener unloads; re-focus instead of opening twice.
  (If a standalone page is ever needed, hand the token over with an origin-checked `postMessage` from the opener — never read Element's storage layout.)

## Feature 9.8 — My plugins (management view in the marketplace card)
Add a *My plugins* tab to `MarketplaceCard` (view-model: `useMyPluginsViewModel`):
- `GET /registry/plugins?owned=true` → rows: icon, name, `released_semver`, candidate status pill, "needs changes" banner with `last_rejection.reason`.
- **Create plugin**: replace the old presigned-code flow in `MarketplaceCardViewModel` with `POST /registry/plugins { name, description, category, tags, icon | null }` → optional icon PUT to `icon_upload.upload_url` → `POST /registry/plugins/{id}/assets/commit { keys: [icon_upload.key] }`. No code upload.
- **Install dev branch here**: `POST /installations?chat_id=` `{ plugin_id, branch_id: dev_branch_id }`.
- **Publish**: dialog (semver prefilled with next patch of `released_semver`, changelog) → `POST /registry/plugins/{id}/publish { source_branch_id: dev_branch_id, semver, changelog }`; show 409/422 messages.
- **Cancel submission** when `candidate` is set → `POST …/branches/{candidate.branch_id}/cancel`.
- Toast on `fstick.dev.notification` (`released` / `rejected` + reason) and refresh the list.

Marketplace install for releases: pick a branch from `GET /registry/plugins/{id}` `branches` (RELEASED only, newest first); send `branch_id` (omit for latest). Installed list: *Upgrade* button when a newer RELEASED exists → `PATCH /installations/{iid} { branch_id }`.

## Feature 9.9 — Review view (moderators)
- Probe `GET /registry/moderation/branches?limit=1` once; 403 → hide the tab.
- Queue list: plugin, `@author` (from `author.mxid`), semver, changelog, submitted time, claim status.
- Detail: read-only CodePanes loaded via `code/client?branch_id=` and `code/server?branch_id=`; buttons *Claim*, *Approve* (claimer only), *Reject* (reason textarea, 1–2000 chars). Handle `claimed_by_other` / `not_claimer` / `invalid_transition` messages.

## Tests (jest + React Testing Library, like existing element-web tests)
- `usePluginEditor`: save sends only dirty sides; 409 flow; `reloaded:false` schedules reload after `retry_after_ms`; byte limit disables Save.
- ConsolePane: filtering by plugin/chat, level toggles, system styling, ring buffer cap.
- DslTopBarSlot: `fstick.plugin.reloaded` re-runs DSL keeping state; Edit visible only for author + WORKING.
- loader: snake_case parsing; `If-None-Match` sent on re-fetch.

## Acceptance criteria
- [ ] End-to-end smoke test steps 1–8 in [00-overview.md](00-overview.md) §7 pass in the browser.
- [ ] The editor works identically in the right panel and the popped-out window; closing the room closes neither unexpectedly and no token appears in storage access code.
- [ ] No full MXID or uuid is rendered anywhere in the new UI.
