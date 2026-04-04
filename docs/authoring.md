# Authoring And Installation Guide

This guide covers the current supported workflow for building and installing a Lapis
Lazuli plugin.

The validated server target is Paper, but the SDK is being designed around
Bukkit-common capabilities.

Published packages:

- npm SDK: `lapis-lazuli`
- npm CLI: `create-lapis-lazuli`
- PyPI SDK: `lapis-lazuli`
- Python import path: `lapis_lazuli`

## 1. Create A Plugin

```sh
npx create-lapis-lazuli /absolute/path/to/my-plugin
npx create-lapis-lazuli /absolute/path/to/my-node-plugin "My Node Plugin" node
npx create-lapis-lazuli /absolute/path/to/my-python-plugin "My Python Plugin" python
```

The generated TypeScript starter declares `lapis-lazuli` as a normal npm
dependency and imports from `lapis-lazuli`. The generated Python starter declares
`lapis-lazuli` in `pyproject.toml` and imports `lapis_lazuli`.

Engine choices:

- `js`: bundled TS/JS executed by the embedded GraalJS runtime
- `node`: bundled TS/JS executed by an external Node process
- `python`: Python bundle executed by the embedded Python runtime

If you are adding Lapis to an existing plugin project instead of scaffolding a new
one, install the SDK from the public registry for your language:

```sh
npm install lapis-lazuli
python -m pip install lapis-lazuli
```

## 2. Implement The Plugin

TypeScript example:

```ts
import { definePlugin } from "lapis-lazuli";

export default definePlugin({
  name: "My Plugin",
  onEnable(context) {
    context.app.log.info("My Plugin enabled.");

    context.commands.register({
      name: "hello",
      execute({ sender }) {
        sender.sendMessage("Hello from Lapis.");
      },
    });
  },
});
```

Python example:

```py
from lapis_lazuli import Plugin

plugin = Plugin("My Python Plugin", version="0.1.0")


@plugin.startup
def on_enable(context):
    context.app.log.info("My Python Plugin enabled.")
```

## 3. Validate And Bundle

```sh
npx create-lapis-lazuli validate /absolute/path/to/my-plugin
npx create-lapis-lazuli bundle /absolute/path/to/my-plugin
```

The deployable bundle is written under `dist/<plugin-id>/`.

For `engine: "node"` bundles, keep a `node` executable available on the server host
or set `plugins/LapisLazuli/config.yml`:

```yaml
node:
  command: /absolute/path/to/node
```

## 4. Install The Runtime

Download the precompiled runtime JAR from the latest GitHub release:

- <https://github.com/metashiyun/lapis-lazuli/releases/latest/download/lapis-runtime-bukkit.jar>

into:

- `<server>/plugins/`

Only build the runtime from source if you are developing this repository itself or
testing unpublished runtime changes:

```sh
bun install
./gradlew :runtimes:jvm:bukkit:shadowJar
```

## 5. Install The Bundle

Copy:

- `/absolute/path/to/my-plugin/dist/<plugin-id>/`

into:

- `<server>/plugins/LapisLazuli/bundles/<plugin-id>/`

## 6. Run The Server

On startup the runtime:

- scans `plugins/LapisLazuli/bundles/`
- loads each bundle
- invokes `onEnable`
- starts hot reload polling

## 7. Recommended Authoring Model

Use the Lapis services for normal plugin work:

- `app`
- `commands`
- `events`
- `tasks`
- `players`
- `worlds`
- `entities`
- `items`
- `inventory`
- `chat`
- `storage`
- `config`

Use `context.unsafe` only when the required backend capability is not yet modeled.
In the `node` engine, raw Java access is intentionally unavailable, so prefer the
documented Lapis services.

## 8. Validate On A Real Server

```sh
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
```
