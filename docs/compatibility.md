# Compatibility And Support Matrix

## Short Answer

No, Lapis Lazuli does not currently provide a complete typed SDK for the full Bukkit,
Spigot, or Paper API surface.

What it supports today is:

- a JavaScript runtime
- a Python runtime
- TypeScript and JavaScript authoring
- Python authoring
- an expanding stable host bridge
- generic Java event registration
- a server bridge with raw server/plugin access
- direct Java interop for advanced JVM access

## Support Matrix

| Area | Status | Notes |
| --- | --- | --- |
| JavaScript runtime | Supported | `manifest.engine` must be `"js"` |
| Python runtime | Supported | `manifest.engine` may be `"python"` |
| TypeScript authoring | Supported | Bundled through Bun into JS |
| JavaScript authoring | Supported | Plain JS bundles are valid |
| Python authoring | Supported | Python bundles are staged and loaded through the Python runtime |
| Python SDK package | Not implemented | No dedicated Python package exists in `packages/` yet |
| Paper 1.21.x server target | Supported | Compile target and smoke-tested path |
| Bukkit server target | Experimental / unverified | No compatibility tests or release guarantee yet |
| Spigot server target | Experimental / unverified | No compatibility tests or release guarantee yet |
| Typed commands API | Supported | Simple command registration and execution context |
| Typed events API | Supported | `playerJoin`, `playerQuit`, `serverLoad` only |
| Generic Java event subscription | Supported | `events.onJava(...)` accepts any Bukkit/Paper event class name |
| Typed scheduler API | Supported | Immediate, delayed, and repeating main-thread tasks |
| Typed config API | Supported | YAML-backed key/value access |
| Typed data directory API | Supported | Bundle-scoped file access |
| Server bridge | Supported | Raw server/plugin handles plus console command dispatch and broadcast |
| Full Bukkit / Spigot / Paper typed wrappers | Not implemented | No object-model wrapper layer exists |
| Direct Bukkit / Paper access through Java interop | Available | Powerful, but not a stable SDK contract |

## What The Stable SDK Actually Covers

The documented Lapis Lazuli API currently exposes:

- `logger`
- `events.on(...)`
- `events.onJava(...)`
- `commands.register(...)`
- `scheduler.runNow(...)`
- `scheduler.runLaterTicks(...)`
- `scheduler.runTimerTicks(...)`
- `config.get/set/save/reload/keys`
- `dataDir.path/resolve/readText/writeText/exists/mkdirs`
- `server.bukkit/plugin/console`
- `server.dispatchCommand(...)`
- `server.broadcast(...)`
- `javaInterop.type(...)`

The only typed events are:

- `playerJoin`
- `playerQuit`
- `serverLoad`

Anything beyond that is outside the current stable SDK contract.

## What Java Interop Changes

Through `context.javaInterop.type(...)`, a script can load Java classes such as:

- `org.bukkit.Bukkit`
- `org.bukkit.Material`
- Paper-specific classes under `io.papermc.*`

That gives JS and TS plugins access to much more than the stable host bridge. However:

- it is not wrapped by the SDK
- it is not versioned as a Lapis Lazuli API surface
- it is not tested as "full Bukkit / Spigot / Paper compatibility"
- it can be distribution-specific

Treat Java interop as an escape hatch, not as proof that Lapis Lazuli has complete SDK
coverage.

Separately, `events.onJava(...)` now allows plugins to subscribe to arbitrary Bukkit or
Paper event classes without waiting for each one to be wrapped into `EventMap`.

## Why Bukkit / Spigot Are Not First-Class Targets Today

The current adapter is Paper-first:

- the runtime compiles against `io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT`
- the real smoke test boots a Paper server, not Bukkit or Spigot
- broader Java event access still relies on the actual server implementation at runtime

That is enough to support a clear product statement:

- Paper is the supported target today
- Bukkit and Spigot should still be treated as unverified

## Recommendation

The documentation, README, and future release notes should describe Lapis Lazuli as:

- a Paper-focused runtime
- with TypeScript / JavaScript authoring
- with a limited stable SDK
- and optional low-level Java interop for advanced use cases

If "full Bukkit / Spigot / Paper support" is a product goal, it needs explicit
engineering work:

1. define the intended support contract
2. wrap or type the required APIs
3. add distribution-specific validation
4. test against each claimed server family
