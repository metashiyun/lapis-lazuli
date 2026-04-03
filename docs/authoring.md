# Authoring And Installation Guide

This guide covers the current supported workflow for building and installing a Lapis
Lazuli plugin.

The validated server target is Paper, but the SDK is being designed around
Bukkit-common capabilities.

## 1. Prepare The Workspace

```sh
bun install
./gradlew :runtime-bukkit:shadowJar
```

## 2. Create A Plugin

```sh
bun packages/cli/src/index.ts create /absolute/path/to/my-plugin "My Plugin"
# or
bun packages/cli/src/index.ts create /absolute/path/to/my-python-plugin "My Python Plugin" python
```

## 3. Implement The Plugin

TypeScript example:

```ts
import { definePlugin } from "@lapis-lazuli/sdk";

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

## 4. Validate And Bundle

```sh
bun packages/cli/src/index.ts validate /absolute/path/to/my-plugin
bun packages/cli/src/index.ts bundle /absolute/path/to/my-plugin
```

The deployable bundle is written under `dist/<plugin-id>/`.

## 5. Install The Runtime

Copy:

- `runtime-bukkit/build/libs/runtime-bukkit.jar`

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
