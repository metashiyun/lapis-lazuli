# Compatibility And Support Matrix

## Short Answer

No, Lapis Lazuli does not currently provide a complete typed SDK for the full Bukkit,
Spigot, or Paper API surface.

What it supports today is:

- a JavaScript runtime
- TypeScript and JavaScript authoring
- a small stable host bridge
- direct Java interop for advanced JVM access

## Support Matrix

| Area | Status | Notes |
| --- | --- | --- |
| JavaScript runtime | Supported | `manifest.engine` must be `"js"` |
| TypeScript authoring | Supported | Bundled through Bun into JS |
| JavaScript authoring | Supported | Plain JS bundles are valid |
| Python authoring SDK | Not implemented | No Python package exists in `packages/` |
| Python runtime engine | Not implemented | `runtime-core` only registers `JsLanguageRuntime` |
| Paper 1.21.x server target | Supported | Compile target and smoke-tested path |
| Bukkit server target | Not supported as a product target | No compatibility tests or adapter guarantee |
| Spigot server target | Not supported as a product target | No compatibility tests or adapter guarantee |
| Typed commands API | Supported | Simple command registration and execution context |
| Typed events API | Supported | `playerJoin`, `playerQuit`, `serverLoad` only |
| Typed scheduler API | Supported | Immediate, delayed, and repeating main-thread tasks |
| Typed config API | Supported | YAML-backed key/value access |
| Typed data directory API | Supported | Bundle-scoped file access |
| Full Bukkit / Spigot / Paper typed wrappers | Not implemented | No object-model wrapper layer exists |
| Direct Bukkit / Paper access through Java interop | Available | Powerful, but not a stable SDK contract |

## What The Stable SDK Actually Covers

The documented Lapis Lazuli API currently exposes:

- `logger`
- `events.on(...)`
- `commands.register(...)`
- `scheduler.runNow(...)`
- `scheduler.runLaterTicks(...)`
- `scheduler.runTimerTicks(...)`
- `config.get/set/save/reload/keys`
- `dataDir.path/resolve/readText/writeText/exists/mkdirs`
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

## Why Bukkit / Spigot Are Not First-Class Targets Today

The current adapter is Paper-first:

- the runtime compiles against `io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT`
- the real smoke test boots a Paper server, not Bukkit or Spigot
- the event adapter serializes join and quit messages through Adventure component APIs

That is enough to support a clear product statement:

- Paper is the supported target today
- Bukkit and Spigot should be treated as unverified

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
