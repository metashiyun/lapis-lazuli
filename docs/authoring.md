# Authoring And Installation Guide

This guide covers the current repo-based workflow for:

- developing a new plugin in TypeScript
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

## 2. Create A New TypeScript Plugin

Use the CLI from this repository:

```sh
bun packages/cli/src/index.ts create /absolute/path/to/my-plugin "My Plugin"
```

That creates:

- `/absolute/path/to/my-plugin/lapis-plugin.json`
- `/absolute/path/to/my-plugin/src/index.ts`
- `/absolute/path/to/my-plugin/package.json`

## 3. Develop The Plugin

Edit:

- `/absolute/path/to/my-plugin/src/index.ts`
- `/absolute/path/to/my-plugin/lapis-plugin.json`

The entrypoint should export `definePlugin(...)` from `@lapis-lazuli/sdk`.

Example:

```ts
import { definePlugin } from "@lapis-lazuli/sdk";

export default definePlugin({
  name: "My Plugin",
  onEnable(context) {
    context.logger.info("My Plugin enabled.");
  },
});
```

## 4. Validate And Bundle The Plugin

Run:

```sh
bun packages/cli/src/index.ts validate /absolute/path/to/my-plugin
bun packages/cli/src/index.ts bundle /absolute/path/to/my-plugin
```

That produces:

- `/absolute/path/to/my-plugin/dist/<plugin-id>/lapis-plugin.json`
- `/absolute/path/to/my-plugin/dist/<plugin-id>/main.js`

This folder is the deployable script-plugin bundle.

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

## 6. Install The Bundled TypeScript Plugin

Copy the bundled plugin directory:

- `/absolute/path/to/my-plugin/dist/<plugin-id>/`

into:

- `<server>/plugins/LapisLazuli/bundles/<plugin-id>/`

After copying, the server should contain:

- `<server>/plugins/runtime-bukkit.jar`
- `<server>/plugins/LapisLazuli/bundles/<plugin-id>/lapis-plugin.json`
- `<server>/plugins/LapisLazuli/bundles/<plugin-id>/main.js`

## 7. Start The Server

Start Paper again. On startup, the runtime plugin will:

- scan `plugins/LapisLazuli/bundles/`
- parse each `lapis-plugin.json`
- load each `main.js`
- call the plugin `onEnable` hook

If the bundle loads successfully, you should see the script plugin’s log output in the server console.

## 8. Updating A Plugin

To update a script plugin:

1. Edit the TypeScript source.
2. Run `bundle` again.
3. Replace the bundle folder under `plugins/LapisLazuli/bundles/<plugin-id>/`.
4. Restart the server.

V1 does not support hot reload as an official workflow.

## 9. Real-Server Validation

To verify the full install flow on a real Paper server jar:

```sh
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
```

That smoke test builds the runtime plugin, bundles the example script plugin, boots a real Paper server, installs both artifacts, executes the script command from the server console, and verifies the server shuts down cleanly.
