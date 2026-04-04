# TypeScript SDK

npm package:

```sh
npm install lapis-lazuli
```

The SDK is the public TypeScript authoring surface for Lapis Lazuli. It is service-first
and TypeScript-first; it does not attempt to mirror Bukkit or Paper class-for-class.

## Minimal Example

```ts
import { definePlugin } from "lapis-lazuli";

export default definePlugin({
  name: "Example Plugin",
  onEnable(context) {
    context.app.log.info("Enabled.");

    context.commands.register({
      name: "hello",
      execute({ sender }) {
        sender.sendMessage("Hello from Lapis.");
      },
    });
  },
});
```

## `PluginContext`

```ts
interface PluginContext {
  app: AppService;
  commands: CommandRegistry;
  events: EventRegistry;
  tasks: TaskRegistry;
  players: PlayerDirectory;
  worlds: WorldDirectory;
  entities: EntityDirectory;
  items: ItemFactory;
  inventory: InventoryDirectory;
  chat: ChatService;
  http: HttpService;
  storage: StorageService;
  config: KeyValueStore;
  unsafe: UnsafeBridge;
}
```

## Main Design Rules

- Common plugin work should not require Java knowledge.
- Service modules should stay small and discoverable.
- Values passed across the API should prefer plain DTOs such as `Location`, `TextInput`,
  and item specs.
- Raw backend access belongs under `unsafe`.

## Event Model

The SDK ships typed Lapis event names instead of raw Java event class names. Examples:

- `server.ready`
- `player.join`
- `player.chat`
- `block.break`
- `entity.damage`
- `inventory.click`

For backend-specific or not-yet-modeled events, use `context.unsafe.events.onJava(...)`.

## Handle Model

The SDK uses focused handles such as:

- `PlayerHandle`
- `WorldHandle`
- `EntityHandle`
- `BlockHandle`
- `InventoryHandle`
- `ItemHandle`

These provide a curated subset of common operations. They are not intended to be direct
wrappers around the full Java object model.

## HTTP

TypeScript plugins can make HTTP requests through `context.http.fetch(...)`.

```ts
const response = await context.http.fetch("https://example.com/api", {
  method: "POST",
  headers: {
    "content-type": "application/json",
  },
  body: JSON.stringify({ hello: "lapis" }),
});

context.app.log.info(`status=${response.status}`);
context.app.log.info(response.body);
```

## Escape Hatch

The SDK still supports advanced backend access through:

- `context.unsafe.java.type(...)`
- `context.unsafe.backend.*`

Treat those as secondary APIs, not the primary authoring path.

When a bundle uses `engine: "node"`, only `context.unsafe.backend.dispatchCommand(...)`
is available. Raw Java access and backend handles remain limited to the embedded
`js` runtime.

For the exact exported types, read [sdks/typescript/src/index.ts](../../sdks/typescript/src/index.ts).
