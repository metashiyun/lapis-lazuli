# Authoring And Installation Guide

This guide covers the current supported workflow for building and installing a Lapis
Lazuli plugin.

The target server in this document is Paper.

## Prerequisites

From the repository root:

```sh
bun install
./gradlew :runtime-bukkit:shadowJar
```

This prepares:

- the local SDK and CLI workspace
- the runtime adapter jar at `runtime-bukkit/build/libs/runtime-bukkit.jar`

## 1. Create A Plugin Project

```sh
bun packages/cli/src/index.ts create /absolute/path/to/my-plugin "My Plugin"
```

The scaffold contains:

- `lapis-plugin.json`
- `src/index.ts`
- `package.json`
- `.gitignore`

## 2. Implement The Plugin

Edit:

- `/absolute/path/to/my-plugin/src/index.ts`
- `/absolute/path/to/my-plugin/lapis-plugin.json`

Minimal example:

```ts
import { definePlugin } from "@lapis-lazuli/sdk";

export default definePlugin({
  name: "My Plugin",
  version: "0.1.0",
  onEnable(context) {
    context.logger.info("My Plugin enabled.");
  },
});
```

For the available runtime surface, see [api/runtime-host-api.md](api/runtime-host-api.md)
and [api/typescript-sdk.md](api/typescript-sdk.md).

## 3. Validate And Bundle

```sh
bun packages/cli/src/index.ts validate /absolute/path/to/my-plugin
bun packages/cli/src/index.ts bundle /absolute/path/to/my-plugin
```

This produces a deployable bundle directory:

- `/absolute/path/to/my-plugin/dist/<plugin-id>/lapis-plugin.json`
- `/absolute/path/to/my-plugin/dist/<plugin-id>/main.js`

## 4. Install The Runtime Adapter

Copy:

- `runtime-bukkit/build/libs/runtime-bukkit.jar`

into:

- `<server>/plugins/`

Start the server once so the runtime can create:

- `<server>/plugins/LapisLazuli/`

Then stop the server.

## 5. Install The Script Bundle

Copy:

- `/absolute/path/to/my-plugin/dist/<plugin-id>/`

into:

- `<server>/plugins/LapisLazuli/bundles/<plugin-id>/`

The deployed structure should look like:

```text
<server>/plugins/runtime-bukkit.jar
<server>/plugins/LapisLazuli/bundles/<plugin-id>/lapis-plugin.json
<server>/plugins/LapisLazuli/bundles/<plugin-id>/main.js
```

## 6. Start Paper

On startup the runtime:

- scans `plugins/LapisLazuli/bundles/`
- parses each `lapis-plugin.json`
- loads each bundle entrypoint
- invokes `onEnable`

If the bundle loads successfully, the script plugin logs will appear in the Paper
console.

## 7. Update A Plugin

To update a deployed plugin:

1. Edit the source project.
2. Run `bundle` again.
3. Replace the deployed bundle directory.
4. Wait for the runtime to hot reload the bundle set.

Default hot reload configuration:

```yaml
hotReload:
  enabled: true
  pollIntervalTicks: 20
```

The runtime ignores bundle-local `config.yml` and `data/` paths during file watching so
plugin persistence does not trigger reload loops.

## 8. Recommended Development Model

Use the stable host API for common plugin behavior:

- logging
- simple commands
- the documented event keys
- scheduler tasks
- config and data storage

Use `context.javaInterop.type(...)` only when you need lower-level server APIs that are
not yet wrapped by Lapis Lazuli.

## 9. Validate On A Real Server

```sh
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
```

This smoke test:

- builds the runtime jar
- bundles the example plugin
- boots a real Paper server
- installs the runtime and bundle
- verifies plugin enable logging, event wiring, command execution, hot reload, and shutdown
