# Lapis Lazuli

Lapis Lazuli is a script-plugin platform for Minecraft servers. Plugin authors write
TypeScript, JavaScript, or Python, bundle that code into a deployable directory, and
load it through a JVM runtime plugin.

The repository currently ships:

- `runtime-core`: bundle loading, lifecycle management, and the GraalJS/GraalPy runtimes
- `runtime-bukkit`: the Paper-focused Minecraft server adapter
- `@lapis-lazuli/sdk`: the TypeScript authoring API
- `@lapis-lazuli/cli`: project scaffolding, validation, build, and bundle commands
- `examples/hello-ts`: the reference TypeScript plugin
- `examples/hello-python`: the reference Python plugin

## Status Snapshot

| Area | Status | Notes |
| --- | --- | --- |
| Runtime language engines | Supported | JavaScript via GraalJS and Python via GraalPy |
| TypeScript authoring | Supported | TypeScript compiles to the JavaScript runtime format |
| JavaScript authoring | Supported | Plain JS bundles are valid |
| Python authoring | Supported | Python bundles are loaded through the Python runtime |
| Paper server target | Supported | Compiled and smoke-tested against Paper 1.21.x |
| Bukkit / Spigot server target | Experimental / unverified | The adapter now avoids some Paper-only assumptions, but Bukkit and Spigot are still not validated release targets |
| Stable typed server API | Expanding | Commands, 3 typed events, generic Java event hooks, scheduler, config, data directory, logger, server bridge, Java interop |
| Full Bukkit / Paper API via SDK | Not supported | Use raw server access and Java interop for advanced access; this is not yet a full typed wrapper layer |

## What "Supports Bukkit / Spigot / Paper API" Means Here

Lapis Lazuli does not currently expose the full Bukkit, Spigot, or Paper API as a
curated typed SDK.

What it does provide is:

- a documented host API through `@lapis-lazuli/sdk`
- a server bridge for raw server/plugin access and console command dispatch
- generic Java-event subscription through `context.events.onJava(...)`
- unrestricted Java interop from JS/TS/Python scripts through `context.javaInterop.type(...)`

That means a script can call deeper JVM APIs such as `org.bukkit.Bukkit` or Paper
classes directly, can receive raw Java event instances, and can dispatch console
commands, but Lapis Lazuli does not currently type, wrap, document, or cross-version
test that full surface for you.

## Documentation

Start with the documentation index:

- [docs/README.md](docs/README.md)

Key reference documents:

- [docs/compatibility.md](docs/compatibility.md)
- [docs/architecture.md](docs/architecture.md)
- [docs/api/runtime-host-api.md](docs/api/runtime-host-api.md)
- [docs/api/typescript-sdk.md](docs/api/typescript-sdk.md)
- [docs/cli.md](docs/cli.md)
- [docs/bundle-format.md](docs/bundle-format.md)
- [docs/authoring.md](docs/authoring.md)
- [docs/testing.md](docs/testing.md)
- [docs/python-sdk.md](docs/python-sdk.md)

## Quick Start

```sh
bun install
bun test
./gradlew :runtime-bukkit:shadowJar
bun packages/cli/src/index.ts create /absolute/path/to/my-plugin "My Plugin"
bun packages/cli/src/index.ts create /absolute/path/to/my-python-plugin "My Python Plugin" python
bun packages/cli/src/index.ts validate /absolute/path/to/my-plugin
bun packages/cli/src/index.ts bundle /absolute/path/to/my-plugin
```

Deploy the generated bundle directory from `dist/<plugin-id>/` into:

```text
<server>/plugins/LapisLazuli/bundles/<plugin-id>/
```

Deploy the runtime adapter jar:

```text
runtime-bukkit/build/libs/runtime-bukkit.jar
```

into:

```text
<server>/plugins/
```

The tested server target is Paper.

## Local Development Commands

```sh
bun install
bun test
./gradlew :runtime-core:test :runtime-bukkit:compileKotlin
./gradlew :runtime-bukkit:shadowJar
bun packages/cli/src/index.ts validate examples/hello-ts
bun packages/cli/src/index.ts bundle examples/hello-ts
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
```

The TypeScript workspace is Bun-based. The JVM runtime is Gradle-based and targets
Java 21.

Python bundles currently support bundle-local source files and modules. Third-party
Python packages are not bundled automatically yet.

For a fuller walkthrough, see [docs/authoring.md](docs/authoring.md).
