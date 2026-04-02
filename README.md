# Lapis Lazuli

Lapis Lazuli is a zero-Java authoring platform for lightweight Minecraft server plugins.
Plugin authors write TypeScript or JavaScript only. The project ships:

- a JVM runtime plugin for Paper/Bukkit servers
- `@lapis-lazuli/sdk` for typed plugin authoring
- `@lapis-lazuli/cli` for scaffolding, validating, building, and bundling script plugins

## Repository Layout

- `runtime-core`: engine-agnostic bundle loading, lifecycle management, and the GraalJS runtime
- `runtime-bukkit`: Paper/Bukkit plugin adapter and host bridge implementation
- `packages/sdk`: TypeScript-first plugin authoring API
- `packages/cli`: project scaffolding and bundling CLI
- `examples/hello-ts`: canonical zero-Java example plugin

## Local Commands

```sh
bun test
bun packages/cli/src/index.ts validate examples/hello-ts
bun packages/cli/src/index.ts bundle examples/hello-ts
./gradlew :runtime-core:test :runtime-bukkit:shadowJar
```

The TypeScript workspace is Bun-based and uses only built-in Bun capabilities. The JVM runtime is Gradle-based and targets Java 21.

## Bundle Format

Each script plugin bundle contains:

- `lapis-plugin.json`
- `main.js`

The runtime plugin discovers bundles from its data directory under `bundles/<bundle-id>/`.

Example manifest:

```json
{
  "id": "hello-ts",
  "name": "Hello TS",
  "version": "0.1.0",
  "engine": "js",
  "main": "./src/index.ts",
  "apiVersion": "1.0"
}
```

The CLI rewrites `main` to `main.js` in the packaged bundle.
