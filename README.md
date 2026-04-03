# Lapis Lazuli

Lapis Lazuli is a Minecraft plugin SDK for TypeScript, JavaScript, and Python.
You write plugins against the Lapis SDK, bundle them, and run them through the
Lapis runtime plugin on Bukkit-family servers.

## Quick Start

Install dependencies and build the runtime:

```sh
bun install
./gradlew :runtimes:jvm:bukkit:shadowJar
```

Create a plugin project:

```sh
npx create-lapis-lazuli /absolute/path/to/my-plugin
npx create-lapis-lazuli /absolute/path/to/my-python-plugin "My Python Plugin" python
```

Validate and bundle it:

```sh
npx create-lapis-lazuli validate /absolute/path/to/my-plugin
npx create-lapis-lazuli bundle /absolute/path/to/my-plugin
```

Copy these into your server:

```text
runtimes/jvm/bukkit/build/libs/lapis-runtime-bukkit.jar -> <server>/plugins/
dist/<plugin-id>/ -> <server>/plugins/LapisLazuli/bundles/<plugin-id>/
```

## Development Checks

```sh
bun test
./gradlew :runtimes:jvm:core:test :runtimes:jvm:bukkit:compileKotlin
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
```

## Docs

Start with [docs/README.md](docs/README.md).
