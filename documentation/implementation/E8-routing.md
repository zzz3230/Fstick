# E8 — Routing: Dendrite `/fstick` proxy + Gateway (`/me`, enrichment, compression)

> Design note: §3 (Gateway swap), §9 Gateway, §10 *Gateway `GET /api/v1/me`*. Read [00-overview.md](00-overview.md) first. The identity **filter and cache** are built in E1; this epic exposes them and wires every browser-facing route.

## Goal
Every endpoint the frontend needs is reachable as `https://<homeserver>/fstick/api/v1/...` with a Matrix token, reaches the right service with `X-User-Id: <internal_uuid>`, and comes back with `*_mxid` fields filled in. `/internal/**` is never reachable from outside.

## Depends on / blocks
- Depends on: E1 (Gateway `UserIdSwapFilter`, `IdentityCache`, integration `lookup-batch`).
- Ships incrementally: each epic's PR adds its rows below. Land 8.1–8.3 with E1.

---

## Feature 8.1 — Dendrite: generic authenticated proxy + body-safe retry

File: `fstickbackend/dendrite/fstickapi/routing/proxy_handlers.go`, `routing.go`.

1. **Fix the retry bug**: in `proxyToGateway`, read the body once (`io.ReadAll(io.LimitReader(r.Body, 3<<20))`; if exactly 3 MiB read → `413`) into `[]byte`, and build both attempts with `bytes.NewReader(body)`. Today the retry sends `nil`.
2. Forward request headers as today (drops `Host` and client `X-User-Id`) — this already passes `Content-Encoding`, `If-None-Match`; keep it that way.
3. Replace the one-function-per-route boilerplate with a helper:
   ```go
   // authProxy authenticates, then proxies to gatewayPath(vars) keeping the query string.
   func authProxy(cfg *config.Dendrite, uAPI userapi.QueryAcccessTokenAPI,
       gatewayPath func(vars map[string]string) string) http.HandlerFunc
   ```
   Path variables must be escaped with `url.PathEscape`.
4. Register every row of the table in 8.4 with `router.Handle(path, authProxy(...)).Methods(method, http.MethodOptions)`. Keep `corsMiddleware`; make sure it allows `PUT`, `PATCH`, and headers `Content-Encoding`, `If-None-Match`, and exposes `ETag`.
5. **Never** register `/internal/...` paths in Dendrite.

## Feature 8.2 — Gateway: generic forwarder

`PluginPlatformProxyService.forward` currently takes a `String` body and sets only `X-User-Id` + JSON content type. Change to:
```java
ResponseEntity<byte[]> forward(HttpMethod m, String baseUrl, String path, String rawQuery,
                               byte[] body, HttpServletRequest in)
```
- Copy request headers `Content-Type`, `Accept`, `If-None-Match`, `X-User-Id` (already swapped by the filter). Do **not** copy `Content-Encoding` (the body was decoded in 8.5).
- Pass status, body and headers (`Content-Type`, `ETag`, `Cache-Control`) through unchanged (as today).
- Controllers become thin: one method per row of 8.4, or a single `@RequestMapping("/api/v1/**")` dispatcher with a route table — either is fine; a route table is less code and easier to audit. Whatever you choose, **only listed routes are forwarded**; everything else → 404.
- Keep `PluginPlatformProxyServiceTest` green (update to the new signature).

## Feature 8.3 — `GET /api/v1/me` (Gateway-local)
```jsonc
// 200
{ "internal_uuid": "9f3c1a2e-…", "mxid": "@ilya:fstick.local" }
```
Served by `MeController` from the request's swapped `X-User-Id` + `IdentityCache` (the MXID the filter saw). No downstream call. Missing header → 401.

## Feature 8.4 — Route table (browser-facing)

Dendrite path = `/fstick` + the "Dendrite" column. Gateway path is what Dendrite calls on the Gateway. "Target" is the downstream service path.

| Method | Dendrite path | Gateway path → target | Epic |
|---|---|---|---|
| GET | `/api/v1/me` | `/api/v1/me` → *gateway itself* | E8 |
| GET | `/api/v1/registry/plugins` | `/api/v1/plugins` → registry `/api/v1/plugins` (query incl. `owned`) | E2 |
| POST | `/api/v1/registry/plugins` | `/api/v1/plugins` → registry | E2 |
| GET | `/api/v1/registry/plugins/{id}` | `/api/v1/plugins/{id}` → registry (query incl. `chat_id`) | E2 |
| POST | `/api/v1/registry/plugins/{id}/assets/commit` | → registry `/api/v1/plugins/{id}/assets/commit` | E2 |
| GET | `/api/v1/registry/plugins/{id}/code/client` | → registry `…/code/client` (`branch_id`, `chat_id`) | E2 |
| GET | `/api/v1/registry/plugins/{id}/code/server` | → registry `…/code/server` (`branch_id`) | E2 |
| GET | `/api/v1/registry/plugins/{id}/branches/{bid}/edit` | → registry | E3 |
| PUT | `/api/v1/registry/plugins/{id}/branches/{bid}/code` | → registry (decompressed, 8.5) | E3 |
| POST | `/api/v1/registry/plugins/{id}/branches/{bid}/reload` | → registry | E3 |
| POST | `/api/v1/registry/plugins/{id}/publish` | → registry | E7 |
| POST | `/api/v1/registry/plugins/{id}/branches/{bid}/cancel` | → registry | E7 |
| POST | `/api/v1/registry/plugins/{id}/branches/{bid}/claim` | → registry | E7 |
| POST | `/api/v1/registry/plugins/{id}/branches/{bid}/approve` | → registry | E7 |
| POST | `/api/v1/registry/plugins/{id}/branches/{bid}/reject` | → registry | E7 |
| GET | `/api/v1/registry/moderation/branches` | → registry `/api/v1/moderation/branches` | E7 |
| POST | `/api/v1/plugins/{id}/command` | → runtime `/command` (existing mapping) | — |
| GET | `/api/v1/plugins/{id}/state` | → runtime `/plugins/{id}/state` | — |
| PUT | `/api/v1/plugins/{id}/state` | → runtime `/plugins/{id}/state` (same path as the GET) | E4 |
| POST | `/api/v1/installations` | → installation | E6 |
| GET | `/api/v1/installations` | → installation | E6 |
| POST | `/api/v1/installations/confirm` | → installation | E6 |
| PATCH | `/api/v1/installations/{iid}` | → installation | E6 |
| DELETE | `/api/v1/installations/{iid}` | → installation | E6 |

Remove: `/api/v1/registry/plugins/{id}/commit` (replaced by `assets/commit`).
Existing registry routes for update/delete/status/screenshots are not browser-exposed today; expose them only when the UI needs them.

## Feature 8.5 — Request decompression + size cap (Gateway)

`RequestDecompressionFilter` (after `UserIdSwapFilter`), applied to `PUT /api/v1/plugins/*/branches/*/code` only:
- `Content-Encoding: gzip` → `GZIPInputStream`; `br` → Brotli (`org.brotli:dec:0.1.2` or `com.aayushatharva.brotli4j:brotli4j`); anything else except identity → `415 unsupported_encoding`.
- Read through a counting stream; abort with `413 { "error": "body_too_large", "limit": 2200000 }` as soon as decoded bytes exceed **2 200 000**, regardless of `Content-Length`.
- Ratio guard: decoded / encoded > 200 once decoded > 64 KB → `413 { "error": "compression_ratio_exceeded" }`.
- Wrap the request with the decoded bytes; remove `Content-Encoding`, set `Content-Length`.
- For identity bodies, still enforce the 2.2 MB cap.

## Feature 8.6 — Response enrichment (`*_mxid`)

`MxidEnrichmentAdvice` — applied in `forward` for JSON responses (`Content-Type: application/json`) from **registry and installation** with status 2xx:
1. Parse with Jackson into a tree.
2. Walk it; collect uuids from fields named `author_id`, `installed_by`, `claimed_by`, and `id` inside an object under key `author`.
3. Resolve unknown uuids with one `POST /api/v1/identity/lookup-batch`; fill `IdentityCache`.
4. Add siblings: `author_id` → `author_mxid`, `installed_by` → `installed_by_mxid`, `claimed_by` → `claimed_by_mxid`, `author.id` → `author.mxid`. Unknown → `null`.
5. Re-serialise; fix `Content-Length`.
If integration is down, return the response un-enriched (log a warning) — never fail the request.
Skip enrichment for `code/*` responses (large, no user ids) by path.

## Tests
- Dendrite (Go): table-driven test that every row routes to the expected gateway path; retry after a first failure re-sends the same body (use `httptest.Server` that fails once).
- Gateway: forwarder copies `If-None-Match`, returns `304`/`ETag`; unknown path → 404; `/internal/x` → 404.
- Decompression: gzip body decodes; 2.2 MB + 1 decoded → 413 even with small `Content-Length`; ratio bomb (10 MB of zeros gzipped) → 413 without allocating 10 MB.
- Enrichment: nested arrays/objects; unknown uuid → `null`; integration down → passthrough.
- `/me`: returns both ids.

## Acceptance criteria
- [ ] Every route in 8.4 works end-to-end with a real Matrix token (curl script in `fstickbackend/scripts/smoke-editor.sh` recommended).
- [ ] `curl http://gateway:8085/internal/...` from the host returns 404; `/fstick/api/v1/internal/...` on Dendrite returns 404.
- [ ] A 400 KB Lua file saved gzip-compressed arrives intact; a zip bomb is rejected cheaply.
- [ ] `GET /registry/plugins?owned=true` items carry `author_mxid`.
