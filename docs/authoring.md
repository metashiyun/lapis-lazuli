# Authoring And Installation Guide

This guide covers the current repo-based workflow for:

- developing a new plugin in TypeScript or Python
- bundling it into a deployable folder
- installing the Lapis Lazuli runtime plugin into a real Paper server
- installing the bundled script plugin into that server

## 1. Prepare The Repository

From the Lapis Lazuli repository:

```sh
bun install
./gradlew :runtime-bukkit:shadowJar
```

This gives you:

- the local TypeScript SDK and CLI
- the runtime plugin jar at `runtime-bukkit/build/libs/runtime-bukkit.jar`

## 2. Create A New Plugin

Use the CLI from this repository:

```sh
bun packages/cli/src/index.ts create /absolute/path/to/my-plugin "My Plugin"
# or
bun packages/cli/src/index.ts create /absolute/path/to/my-python-plugin "My Python Plugin" python
```

For a TypeScript project, that creates:

- `/absolute/path/to/my-plugin/lapis-plugin.json`
- `/absolute/path/to/my-plugin/src/index.ts`
- `/absolute/path/to/my-plugin/package.json`

For a Python project, that creates:

- `/absolute/path/to/my-python-plugin/lapis-plugin.json`
- `/absolute/path/to/my-python-plugin/src/main.py`

## 3. Develop The Plugin

Edit:

- the generated entrypoint file
- `lapis-plugin.json`

TypeScript entrypoints should export `definePlugin(...)` from `@lapis-lazuli/sdk`.

TypeScript example:

```ts
import { definePlugin } from "@lapis-lazuli/sdk";

export default definePlugin({
  name: "My Plugin",
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

## 4. Validate And Bundle The Plugin

Run:

```sh
bun packages/cli/src/index.ts validate /absolute/path/to/my-plugin
bun packages/cli/src/index.ts bundle /absolute/path/to/my-plugin
```

That produces:

- `/absolute/path/to/my-plugin/dist/<plugin-id>/lapis-plugin.json`
- `/absolute/path/to/my-plugin/dist/<plugin-id>/...`

This folder is the deployable script-plugin bundle.

TypeScript and JavaScript projects are bundled to `main.js`.
Python projects keep their source layout, so the bundle typically contains `src/main.py` and any bundle-local modules alongside it.

## 5. Install The Runtime Plugin Into Paper

Build the runtime plugin:

```sh
./gradlew :runtime-bukkit:shadowJar
```

Copy:

- `runtime-bukkit/build/libs/runtime-bukkit.jar`

into:

- `<server>/plugins/`

Then start the server once. This allows Paper to load the runtime plugin and create the runtime data directory:

- `<server>/plugins/LapisLazuli/`

Stop the server after that first boot.

## 6. Install The Bundled Plugin

Copy the bundled plugin directory:

- `/absolute/path/to/my-plugin/dist/<plugin-id>/`

into:

- `<server>/plugins/LapisLazuli/bundles/<plugin-id>/`

After copying, the server should contain:

- `<server>/plugins/runtime-bukkit.jar`
- `<server>/plugins/LapisLazuli/bundles/<plugin-id>/lapis-plugin.json`
- the bundled entrypoint named by `lapis-plugin.json`

## 7. Start The Server

Start Paper again. On startup, the runtime plugin will:

- scan `plugins/LapisLazuli/bundles/`
- parse each `lapis-plugin.json`
- load each configured bundle entrypoint
- call the plugin `onEnable` hook

If the bundle loads successfully, you should see the script plugin’s log output in the server console.

## 8. Updating A Plugin

To update a script plugin:

1. Edit the source files.
2. Run `bundle` again.
3. Replace the bundle folder under `plugins/LapisLazuli/bundles/<plugin-id>/`.
4. Wait for Lapis Lazuli to detect the bundle change and hot reload it.

By default the Paper runtime polls the `bundles/` directory every 20 ticks and reloads script bundles automatically when tracked bundle files change.

The runtime ignores bundle-local `config.yml` and `data/` paths while watching for changes, so plugin state saves do not trigger reload loops.

You can configure or disable polling in `plugins/LapisLazuli/config.yml`:

```yaml
hotReload:
  enabled: true
  pollIntervalTicks: 20
```

Python bundles currently support bundle-local source files and modules. Third-party Python packages are not bundled automatically yet.

## 9. Real-Server Validation

To verify the full install flow on a real Paper server jar:

```sh
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
```

That smoke test builds the runtime plugin, bundles the example script plugin, boots a real Paper server, installs both artifacts, verifies the initial command flow, rewrites the deployed bundle to trigger hot reload, verifies the updated command output, and then shuts the server down cleanly.
