# Lapis Lazuli

Lapis Lazuli is a script-plugin platform for Minecraft servers. Plugin authors write
TypeScript or JavaScript, bundle that code into a deployable directory, and load it
through a JVM runtime plugin.

The repository currently ships:

- `runtime-core`: bundle loading, lifecycle management, and the GraalJS runtime
- `runtime-bukkit`: the Paper-focused Minecraft server adapter
- `@lapis-lazuli/sdk`: the TypeScript authoring API
- `@lapis-lazuli/cli`: project scaffolding, validation, build, and bundle commands
- `examples/hello-ts`: the reference example plugin

## Status Snapshot

| Area | Status | Notes |
| --- | --- | --- |
| Runtime language engine | Supported | JavaScript only, via GraalJS |
| TypeScript authoring | Supported | TypeScript compiles to the JavaScript runtime format |
| JavaScript authoring | Supported | Plain JS bundles are valid |
| Python SDK | Not implemented | No Python package or Python runtime exists in this repo |
| Paper server target | Supported | Compiled and smoke-tested against Paper 1.21.x |
| Bukkit / Spigot server target | Not a supported target | The adapter is Paper-biased and not validated on Bukkit or Spigot |
| Stable typed server API | Limited | Commands, 3 events, scheduler, config, data directory, logger, Java interop |
| Full Bukkit / Paper API via SDK | Not supported | Use Java interop for advanced access, but that is an escape hatch rather than a stable SDK contract |

## What "Supports Bukkit / Spigot / Paper API" Means Here

Lapis Lazuli does not currently expose the full Bukkit, Spigot, or Paper API as a
curated typed SDK.

What it does provide is:

- a small documented host API through `@lapis-lazuli/sdk`
- unrestricted Java interop from JS/TS scripts through `context.javaInterop.type(...)`

That means a script can call deeper JVM APIs such as `org.bukkit.Bukkit` or Paper
classes directly, but Lapis Lazuli does not currently type, wrap, document, or
cross-version test that full surface for you.

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
./gradlew :runtime-bukkit:shadowJar
bun packages/cli/src/index.ts create /absolute/path/to/my-plugin "My Plugin"
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
