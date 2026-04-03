# Lapis Lazuli

Lapis Lazuli is a modern Minecraft plugin SDK for TypeScript, JavaScript, and optional
Python authoring. Plugins target the Lapis SDK, and the runtime plugin implements that
SDK on Bukkit-family servers.

Current architecture:

- `packages/sdk`: the public Lapis SDK
- `packages/cli`: project scaffolding, validation, build, and bundling
- `runtime-core`: bundle loading plus the GraalJS and GraalPy runtimes
- `runtime-bukkit`: the Bukkit/Paper backend implementation of the SDK
- `examples/hello-ts`: reference TypeScript plugin
- `examples/hello-python`: reference Python plugin

## Status Snapshot

| Area | Status | Notes |
| --- | --- | --- |
| TypeScript authoring | Supported | Primary design target |
| JavaScript authoring | Supported | Uses the same runtime shape as TS |
| Python authoring | Supported / early | Runtime support exists, but TS remains the design authority |
| `@lapis-lazuli/sdk` | Active redesign | Service-oriented Lapis API |
| Paper runtime target | Supported | Compile target and smoke-tested path |
| Bukkit-family core | In progress | SDK is designed around Bukkit-common capabilities |
| Raw backend escape hatch | Supported | Available under `context.unsafe`, not the primary API |

## SDK Direction

Lapis is not a Java bridge API.

The goal is:

- a TypeScript-first SDK with modern ergonomics
- capability-focused services such as `app`, `commands`, `events`, `tasks`, `players`,
  `worlds`, `entities`, `items`, `inventory`, `chat`, `storage`, and `config`
- a runtime backend that maps those services onto Bukkit-family servers
- an explicit `unsafe` escape hatch for raw Java and backend access when needed

The SDK deliberately does not try to mirror the Bukkit or Paper Java APIs 1:1.

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

## Local Development

```sh
bun install
bun test
./gradlew :runtime-core:test :runtime-bukkit:compileKotlin
./gradlew :runtime-bukkit:shadowJar
bun packages/cli/src/index.ts validate examples/hello-ts
bun packages/cli/src/index.ts bundle examples/hello-ts
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
```

## Documentation

Start with [docs/README.md](docs/README.md).
