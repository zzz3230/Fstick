# E5 — Console streaming (`print` / `warn` → author)

> Design note: §8 (Console streaming), §10 *Integration — console*, §11 `fstick.plugin.console`. Read [00-overview.md](00-overview.md) first.

## Goal
Every `print`/`warn` executed on a **dev branch** (and every Runtime-generated error on it) reaches the plugin author live, batched, rate-limited, and only while the author is still a member of that chat.

## Depends on / blocks
- Depends on: E4 (engine context carries branch/chat/command/track; `AuthorCache`), E1 (uuid ↔ mxid in integration).
- Blocks: E9 console pane.

## Wire format

Runtime → integration `POST /api/v1/plugin-console` (fire-and-forget; integration answers `202` always):
```jsonc
{ "author_id": "<internal_uuid>",
  "msgs": [ { "plugin_id", "chat_id", "branch_id",
              "level": "info" | "warn" | "error",
              "system": false,
              "message": "…", "ts": 1759000000123,
              "command_name": "counter.increment", "track_id": "…" } ] }
```
integration → author's `/sync` as event `fstick.plugin.console` with content `{ "msgs": [ … ] }` (same objects; no `author_id`).

## Feature 5.1 — Lua hooks (runtime)

In `LuaPluginEngine`:
- Replace the `print` global: join all args with `\t` using `tostring` semantics (use `VarArgFunction`, not `OneArgFunction` — Lua `print` is variadic), then `sink.line(INFO, false, text)`. Still `log.debug` it.
- Add `warn` global (variadic): `sink.line(WARN, false, text)`.
- The engine gets a `ConsoleSink` via constructor (no static). Current per-command context (`pluginId, branchId, branchStatus, chatId, commandName, trackId`) is read by the sink from the engine's current `PluginRuntimeContext`.
- Hooks during **load** (top-level `print` in the file while compiling) have no command context: send with `command_name = null`, `track_id = null`, `chat_id` = the reload's chat if known, else skip.

System lines (`system: true`, `level: "error"`), emitted by Runtime itself:
| When | Message |
|---|---|
| compile/load error (`LoadedEngine.loadError`) on reload or first load | `"load error: <LuaError message>"` |
| command on a broken engine | `"plugin failed to load: <msg>"` |
| `RUNTIME_ERROR` / `VALIDATION_ERROR` result from `ExecuteCommandHandler` | the error message returned by the stdlib |
| uncaught Java exception around execution | `"internal error: <class>"` (no stack trace to the client) |
| flood drop | `"<N> console lines dropped (rate limit)"` |

**Only when `branchStatus == WORKING`.** For RELEASED branches the sink is a no-op (still `log.debug`).

## Feature 5.2 — Buffering, batching, flood control (runtime)

`console/ConsoleService` (singleton bean) + `console/ConsoleSink` (per engine, thin):
- Buffer per author: `ConcurrentHashMap<UUID authorId, Deque<ConsoleMsg>>`.
- Flush triggers: every 100 ms (`ScheduledExecutorService`, one thread), when a buffer reaches 64 lines, and at the end of each command (call `consoleService.flush(authorId)` after `executeCommand` returns, outside the engine lock).
- Per line: `message` truncated to 8 KB (UTF-8 bytes; cut on a code-point boundary) + `…[truncated]`.
- Per batch: at most 256 lines; overflow dropped and replaced by one synthetic system line.
- Per `(pluginId, chatId)` token bucket: capacity 500, refill 50 tokens/s. No token → drop, count; on the next accepted line or flush, append one synthetic `"<N> console lines dropped (rate limit)"` system line.
- Author id from `AuthorCache.authorOf(pluginId)` (Caffeine, `GET /internal/plugins/{id}` → `author_id`, no expiry; the author never changes). Create it here if E4 didn't.
- Sending: `IntegrationServiceClient.pushConsole(authorId, msgs)` — POST, 1 s timeout, errors logged at `debug`, never thrown into the command path.

Config:
```properties
runtime.console.flush-ms=100
runtime.console.flush-lines=64
runtime.console.max-batch=256
runtime.console.max-line-bytes=8192
runtime.console.bucket-capacity=500
runtime.console.bucket-refill-per-sec=50
```

## Feature 5.3 — integration `POST /api/v1/plugin-console`

`controller/PluginConsoleController` + `service/PluginConsoleService`:
1. Validate: `author_id` uuid, `msgs` ≤ 256 (truncate extra), each `message` ≤ 8 KB (truncate).
2. `mxid = identity.lookup(author_id)`; unknown → log + return `202` (never 404).
3. Membership filter: for each distinct `chat_id` in the batch call Dendrite `GET /chats/{chat}/members/{mxid}`; drop lines for chats where `is_member` is false. Cache the answer per `(chat, author)` for 30 s (Caffeine) — console traffic is bursty.
4. If lines remain: one Dendrite `/events/push` `{ user_id: mxid, type: "fstick.plugin.console", content: { msgs } }`. Preserve order.
5. Return `202` before step 3 (do 2–4 on an executor).

## Tests
- Runtime: `print("a", 1, nil)` → one line `"a\t1\tnil"`; `warn` → level warn; RELEASED branch → nothing sent.
- 70 prints in one command → two batches (64 + 6) or one at command end — assert total 70 lines, order preserved.
- 1000 prints within a second on one chat → ≤ ~500 delivered + exactly one synthetic drop line with the right count.
- 9 KB line → truncated with marker.
- Broken Lua on reload → one system error line.
- integration: unknown author → 202, no push; author left chat A, still in B → only B's lines pushed; single push per batch.

## Acceptance criteria
- [ ] Author opens the editor, another member runs a command → author sees the line with `command_name`; the other member sees nothing.
- [ ] A Lua runtime error appears as a red system line, distinct from `print` output.
- [ ] An infinite-ish `for i=1,100000 do print(i) end` doesn't slow other chats and yields a single "dropped" notice.
