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
# or
bun packages/cli/src/index.ts create /absolute/path/to/my-python-plugin "My Python Plugin" python
```

The scaffold contains:

- `lapis-plugin.json`
- `src/index.ts` for JS/TS projects or `src/main.py` for Python projects
- `package.json` for JS/TS projects
- `.gitignore`

## 2. Implement The Plugin

Edit:

- the generated entrypoint file
- `lapis-plugin.json`

TypeScript example:

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

Python entrypoints can expose `name` plus `on_enable` and `on_disable` functions.

Python example:

```py
name = "My Python Plugin"


def on_enable(context):
    context.logger.info("My Python Plugin enabled.")
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
- `/absolute/path/to/my-plugin/dist/<plugin-id>/...`

TypeScript and JavaScript projects are bundled to `main.js`.
Python projects keep their source layout, so the bundle typically contains `src/main.py`
and any bundle-local modules alongside it.

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
<server>/plugins/LapisLazuli/bundles/<plugin-id>/...
```

## 6. Start Paper

On startup the runtime:

- scans `plugins/LapisLazuli/bundles/`
- parses each `lapis-plugin.json`
- loads each configured bundle entrypoint
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

Python bundles currently support bundle-local source files and modules. Third-party
Python packages are not bundled automatically yet.

## 8. Recommended Development Model

Use the stable host API for common plugin behavior:

- logging
- simple commands
- the documented event keys
- generic Java event hooks
- scheduler tasks
- config and data storage
- server bridge access

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
