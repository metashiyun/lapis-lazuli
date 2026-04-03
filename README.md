# Lapis Lazuli

Lapis Lazuli is a modern Minecraft plugin SDK for TypeScript, JavaScript, and Python.
You write plugins against the Lapis SDK, bundle them, and run them through the
Lapis runtime plugin on Bukkit-family servers.

## Quick Start

Install dependencies and build the runtime:

```sh
bun install
./gradlew :runtime-bukkit:shadowJar
```

Create a plugin project:

```sh
bun packages/cli/src/index.ts create /absolute/path/to/my-plugin "My Plugin"
# or
bun packages/cli/src/index.ts create /absolute/path/to/my-python-plugin "My Python Plugin" python
```

Validate and bundle it:

```sh
bun packages/cli/src/index.ts validate /absolute/path/to/my-plugin
bun packages/cli/src/index.ts bundle /absolute/path/to/my-plugin
```

Copy these into your server:

```text
runtime-bukkit/build/libs/runtime-bukkit.jar -> <server>/plugins/
dist/<plugin-id>/ -> <server>/plugins/LapisLazuli/bundles/<plugin-id>/
```

## Development Checks

```sh
bun test
./gradlew :runtime-core:test :runtime-bukkit:compileKotlin
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
```

## Docs

Start with [docs/README.md](docs/README.md).
