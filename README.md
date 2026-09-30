# Fstick

**English** · [Русский](README.ru.md)

Fstick is a messenger built on [Matrix](https://matrix.org) where every chat can be extended with plugins. A plugin is a small app, such as a poll, a game or a task board, that lives inside a chat. Everyone in the chat sees it and works with the same data. Developers publish plugins to a marketplace, and chat admins install them into a specific room.

## Why it's different

**Plugins are two small programs.**
- The **client part** describes the UI in a compact declarative JavaScript DSL: `Column`, `Row`, `Text`, `Button`, `Input`, `IfBlock`, bindings to state. There's no framework to learn and no build step, and the host app renders it natively.
- The **server part** is written in Lua. It declares a typed state schema and registers commands that the UI can call.

```lua
SetStateSchema({ counter = Types.int })

RegisterCommand({
    name = "counter.increment",
    in_schema = { by = Types.int },
    out_schema = { value = Types.int },
    handler = function(ctx, payload)
        local v = ctx.state.counter:get() + payload.by
        ctx.state.counter:set(v)
        return { value = v }
    end,
})
```

```js
const increment = backend.makeCommand('counter.increment');

DSL_CONTEXT.exports.__app = Column({ gap: 8 }, [
    Text(state.counter, { font_size: 'title' }),
    Button('+1', () => increment({ by: 1 }).before(() => state.counter.set(state.counter.value + 1))),
]);
```

**State syncs itself.**
- Each installed plugin has one shared state per chat, and the server is the source of truth. After every command, the new state is pushed to all chat members over the Matrix sync stream.
- The UI updates optimistically and rolls back if the server rejects the change.
- Fields marked `user_scoped` are private: each member only receives their own slice.

**Sandboxed execution.** Server code never runs in the client or with access to the host. The runtime service executes it in an isolated sandbox:
- only the plugin API is available;
- there's no file system or network;
- resource limits apply.

A broken or malicious plugin can't hurt the platform or other plugins. The runtime is being rewritten, so the plugin API above is the contract, not the current engine.

**Develop inside the chat.**
- The built-in editor shows both files side by side, with a live debug console.
- Hot reload keeps the state.
- You debug in a real chat with real users.
- When ready, you publish a release; moderators review it before it appears in the marketplace.

## Architecture

```
Element-based web client
        │  Matrix client API + /fstick/api/v1/*  (Matrix access token)
        ▼
Dendrite (Matrix homeserver, extended)
        │  authenticates the user, proxies plugin API calls
        ▼
Gateway ──► Registry      plugin catalog, branches, code blobs, moderation   (PostgreSQL, MinIO)
        ├─► Installation  which plugin/branch is installed in which chat     (PostgreSQL, Redis)
        └─► Runtime       executes plugin commands, keeps plugin state      (Redis)

Integration ◄── services     the only service that talks back to Matrix:
        │                   user identity mapping, chat membership, messages, pushing events
        ▼
Dendrite ──► /sync ──► client     (plugin state updates, console output, notifications)
```

**Why Matrix and Dendrite.** Matrix gives us rooms, accounts, federation and a reliable real-time sync channel out of the box. We use [Dendrite](https://github.com/element-hq/dendrite), a Go homeserver, and extend it with a small `/fstick` API:
- it authenticates plugin API requests with the user's Matrix token and forwards them to the Gateway;
- it delivers custom `fstick_events` (state updates, console lines, notifications) inside the normal `/sync` response.

The client is a fork of [Element Web](https://github.com/element-hq/element-web) with a plugin marketplace, plugin slots in the room header and the plugin editor.

**Services**

| Service | Responsibility |
|---|---|
| **Gateway** | Single entry point behind Dendrite. Maps Matrix ids to internal user ids, routes requests, adds display ids to responses |
| **Registry** | Marketplace: plugins, debug/release branches, content-addressed code storage, publishing and moderation |
| **Installation** | Installs a plugin branch into a chat; debug branches only for their author |
| **Runtime** | Runs plugin commands in the sandbox, stores per-chat state, hot reload |
| **Integration** | Anti-corruption layer for Matrix: identity table, chat members, messages, event delivery |

Inside the backend, users are identified by an internal UUID. Matrix ids (`@user:server`) stay at the edges: in Dendrite, Integration and the Gateway.

## Tech stack

- **Backend:** Java 17/21, Spring Boot 4, Gradle / Maven
- **Storage:** PostgreSQL, Redis, MinIO (S3)
- **Matrix:** Dendrite (Go), Matrix client-server API
- **Plugins:** Lua on the server, JavaScript DSL on the client
- **Frontend:** Element Web (TypeScript, React), pnpm + Nx
- **Infrastructure:** Docker Compose

## Repository layout

```
fstickbackend/
  gateway-service/ registry-service/ installation-service/
  runtime-service/ integration-service/
  dendrite/              Matrix homeserver with the /fstick extension
  docker-compose.yml     all backend services + databases
fstickfrontend/element-web/   web client
documentation/           design notes and implementation guides
vote-plugin/ ttt-plugin/ example plugins
```

## Running locally

```bash
# Matrix homeserver
docker compose -f fstickbackend/dendrite/build/docker/docker-compose.yml up -d

# backend services, databases, MinIO
cd fstickbackend && docker compose up --build

# web client
cd fstickfrontend/element-web && pnpm install && pnpm start
```

Database schemas are created from init scripts on first start. After schema changes, recreate the volumes: `docker compose down -v`.

## Documentation

- [Plugin editor design](documentation/plugin-editor-integration.html) ([RU](documentation/plugin-editor-integration.ru.html)): API contracts, branches, moderation, console
- [Implementation guides](documentation/implementation/00-overview.md): conventions and one guide per epic
- [Service specification](fstick_specification.md) (RU)
