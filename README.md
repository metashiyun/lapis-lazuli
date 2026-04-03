# Lapis Lazuli

Lapis Lazuli is a zero-Java authoring platform for lightweight Minecraft server plugins.
Plugin authors write TypeScript, JavaScript, or Python. The project ships:

- a JVM runtime plugin for Paper/Bukkit servers
- `@lapis-lazuli/sdk` for typed plugin authoring
- `@lapis-lazuli/cli` for scaffolding, validating, building, and bundling script plugins

## Repository Layout

- `runtime-core`: engine-agnostic bundle loading, lifecycle management, and the GraalJS/GraalPy runtimes
- `runtime-bukkit`: Paper/Bukkit plugin adapter and host bridge implementation
- `packages/sdk`: TypeScript-first plugin authoring API
- `packages/cli`: project scaffolding and bundling CLI
- `examples/hello-ts`: canonical zero-Java example plugin
- `examples/hello-python`: canonical Python example plugin

## Local Commands

```sh
bun install
bun test
bun packages/cli/src/index.ts create /path/to/my-plugin "My Plugin"
bun packages/cli/src/index.ts create /path/to/my-python-plugin "My Python Plugin" python
bun packages/cli/src/index.ts validate examples/hello-ts
bun packages/cli/src/index.ts bundle examples/hello-ts
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
./gradlew :runtime-core:test :runtime-bukkit:shadowJar
```

The TypeScript workspace is Bun-based and uses only built-in Bun capabilities. The JVM runtime is Gradle-based and targets Java 21.

## Bundle Format

Each script plugin bundle contains:

- `lapis-plugin.json`
- a language-specific entrypoint such as `main.js` or `src/main.py`

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

For JavaScript and TypeScript bundles, the CLI rewrites `main` to `main.js` in the packaged bundle.
For Python bundles, the CLI stages the project source tree into the deployable bundle and preserves the Python entrypoint path.

## Develop And Install A Plugin

The current workflow is source-first from this repository:

1. Create a new plugin project:

```sh
bun packages/cli/src/index.ts create /absolute/path/to/my-plugin "My Plugin"
# or
bun packages/cli/src/index.ts create /absolute/path/to/my-python-plugin "My Python Plugin" python
```

2. Edit the generated source file and `lapis-plugin.json`.

3. Validate and bundle the plugin:

```sh
bun packages/cli/src/index.ts validate /absolute/path/to/my-plugin
bun packages/cli/src/index.ts bundle /absolute/path/to/my-plugin
```

4. Install the runtime plugin on the Paper server:

```sh
./gradlew :runtime-bukkit:shadowJar
```

Then copy:

- `runtime-bukkit/build/libs/runtime-bukkit.jar` into `<server>/plugins/`

5. Start the server once so Paper loads the runtime plugin and creates `plugins/LapisLazuli/`, then stop the server.

6. Install the bundled plugin by copying:

- `/absolute/path/to/my-plugin/dist/<plugin-id>/`

into:

- `<server>/plugins/LapisLazuli/bundles/<plugin-id>/`

7. Start the server again. Lapis Lazuli will discover the bundle and load it on startup.

Bundle hot reload is now enabled by default on Paper. When files under `plugins/LapisLazuli/bundles/<plugin-id>/` change, the runtime will unload the current script plugins and reload the bundle set automatically without restarting the server.

The runtime-owned `config.yml` and `data/` paths inside each bundle are ignored by hot reload so plugin saves do not trigger reload loops.

You can tune this behavior in `plugins/LapisLazuli/config.yml`:

```yaml
hotReload:
  enabled: true
  pollIntervalTicks: 20
```

The supported update flow is now build bundle, replace the bundle folder, and wait for the runtime to reload it.

Python bundles currently support bundle-local source files and modules. Third-party Python packages are not bundled automatically yet.

For a fuller walkthrough, see [docs/authoring.md](docs/authoring.md).
