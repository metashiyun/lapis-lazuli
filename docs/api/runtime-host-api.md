# Runtime Host API

This document describes the runtime context injected into JavaScript and Python bundles.

The public model is service-oriented:

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
- `unsafe`

## Lifecycle

```ts
type Awaitable<T> = T | Promise<T>;

interface PluginDefinition {
  name: string;
  version?: string;
  onEnable?(context: PluginContext): Awaitable<void>;
  onDisable?(context: PluginContext): Awaitable<void>;
}
```

`onEnable` runs once after the bundle is loaded.

`onDisable` runs during shutdown or hot reload if enable completed successfully.

## `app`

`app` contains runtime metadata and bundle-local lifecycle helpers:

- `id`
- `name`
- `version`
- `engine`
- `apiVersion`
- `backend`
- `runtime`
- `log`
- `onShutdown(...)`

The logger is now accessed through `context.app.log`.

## `commands`

`commands.register(...)` installs a command using a Lapis command definition.

Command sender payloads expose:

- `name`
- `type`
- optional `player`
- `sendMessage(...)`
- `hasPermission(...)`
- `unsafe.handle`

## `events`

`events.on(...)` subscribes to named Lapis events.

Current named events:

- `server.ready`
- `player.join`
- `player.quit`
- `player.chat`
- `player.move`
- `player.interact`
- `player.teleport`
- `block.break`
- `block.place`
- `entity.damage`
- `entity.death`
- `inventory.click`
- `inventory.close`
- `world.load`
- `world.unload`

Cancellable events expose:

- `cancelled`
- `cancel()`
- `uncancel()`

Event payloads use Lapis handles such as `player`, `world`, `entity`, `block`,
`inventory`, and `item`.

## `tasks`

Task helpers are lifecycle-aware. Tasks are cancelled automatically when the bundle is
disabled.

Available methods:

- `run(task)`
- `delay(delayTicks, task)`
- `repeat(intervalTicks, task)`
- `timer(delayTicks, intervalTicks, task)`

## Gameplay Services

The runtime exposes common plugin work through service modules:

- `players.online/get/require`
- `worlds.list/get/require`
- `entities.get/spawn`
- `items.create`
- `inventory.create/open`
- `chat.broadcast`

Handles returned from these services are intentionally small and ergonomic rather than
mirroring the Java API directly.

## Persistence

Two persistence paths are exposed:

- `config`: bundle config values backed by `config.yml`
- `storage.plugin`: bundle-scoped persistent key/value storage
- `storage.files`: bundle-scoped file storage rooted at `data/`

## `unsafe`

`unsafe` is the explicit escape hatch for backend-specific work.

Available paths:

- `unsafe.events.onJava(...)`
- `unsafe.java.type(...)`
- `unsafe.backend.server`
- `unsafe.backend.plugin`
- `unsafe.backend.console`
- `unsafe.backend.dispatchCommand(...)`

Use this only when the Lapis SDK does not yet cover the required capability.
