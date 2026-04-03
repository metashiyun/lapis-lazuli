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

## 1. Prepare The Workspace

```sh
bun install
./gradlew :runtimes:jvm:bukkit:shadowJar
```

## 2. Create A Plugin

```sh
npx create-lapis-lazuli /absolute/path/to/my-plugin
npx create-lapis-lazuli /absolute/path/to/my-python-plugin "My Python Plugin" python
```

## 3. Implement The Plugin

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

If you are authoring outside this monorepo, install the SDKs from the public registries:

```sh
npm install lapis-lazuli
python -m pip install lapis-lazuli
```

## 4. Validate And Bundle

```sh
npx create-lapis-lazuli validate /absolute/path/to/my-plugin
npx create-lapis-lazuli bundle /absolute/path/to/my-plugin
```

The deployable bundle is written under `dist/<plugin-id>/`.

## 5. Install The Runtime

Copy:

- `runtimes/jvm/bukkit/build/libs/lapis-runtime-bukkit.jar`

into:

- `<server>/plugins/`

## 6. Install The Bundle

Copy:

- `/absolute/path/to/my-plugin/dist/<plugin-id>/`

into:

- `<server>/plugins/LapisLazuli/bundles/<plugin-id>/`

## 7. Run The Server

On startup the runtime:

- scans `plugins/LapisLazuli/bundles/`
- loads each bundle
- invokes `onEnable`
- starts hot reload polling

## 8. Recommended Authoring Model

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

## 9. Validate On A Real Server

```sh
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
```
