# TypeScript SDK

Package name:

```ts
@lapis-lazuli/sdk
```

The SDK is the typed authoring layer for Lapis Lazuli plugins. It describes the plugin
shape and the runtime context, but it does not itself implement server behavior.

## Current Export Surface

The package currently exports:

- `definePlugin`
- the type interfaces used by plugin authors

## Minimal Example

```ts
import { definePlugin } from "@lapis-lazuli/sdk";

export default definePlugin({
  name: "Example Plugin",
  version: "0.1.0",
  onEnable(context) {
    context.logger.info("Enabled.");
  },
  onDisable(context) {
    context.logger.info("Disabled.");
  },
});
```

## Core Types

```ts
export type Awaitable<T> = T | Promise<T>;

export interface PluginDefinition {
  name: string;
  version?: string;
  onEnable?(context: PluginContext): Awaitable<void>;
  onDisable?(context: PluginContext): Awaitable<void>;
}

export function definePlugin<T extends PluginDefinition>(definition: T): T;
```

## `PluginContext`

```ts
export interface PluginContext {
  logger: Logger;
  events: EventRegistry;
  commands: CommandRegistry;
  scheduler: Scheduler;
  config: ConfigStore;
  dataDir: DataDirectory;
  javaInterop: JavaInterop;
}
```

For runtime semantics, see [runtime-host-api.md](runtime-host-api.md).

## Logger Types

```ts
export interface Logger {
  info(message: string): void;
  warn(message: string): void;
  error(message: string): void;
  debug(message: string): void;
}
```

## Command Types

```ts
export interface HookHandle {
  unsubscribe(): void;
}

export interface CommandSender {
  name: string;
  type: "player" | "console" | "other";
  uuid?: string;
  sendMessage(message: string): void;
}

export interface CommandExecutionContext {
  sender: CommandSender;
  args: string[];
  label: string;
}

export type CommandResult = void | boolean | string;

export interface CommandDefinition {
  name: string;
  description?: string;
  usage?: string;
  aliases?: string[];
  execute(context: CommandExecutionContext): Awaitable<CommandResult>;
}

export interface CommandRegistry {
  register(command: CommandDefinition): HookHandle;
}
```

## Event Types

```ts
export interface PlayerJoinEvent {
  type: "playerJoin";
  playerName: string;
  playerUuid: string;
  joinMessage?: string | null;
}

export interface PlayerQuitEvent {
  type: "playerQuit";
  playerName: string;
  playerUuid: string;
  quitMessage?: string | null;
}

export interface ServerLoadEvent {
  type: "serverLoad";
  reload: boolean;
}

export interface EventMap {
  playerJoin: PlayerJoinEvent;
  playerQuit: PlayerQuitEvent;
  serverLoad: ServerLoadEvent;
}

export interface EventRegistry {
  on<K extends keyof EventMap>(
    event: K,
    handler: (payload: EventMap[K]) => Awaitable<void>,
  ): HookHandle;
}
```

Only these events are typed today.

## Scheduler Types

```ts
export interface TaskHandle {
  cancel(): void;
}

export interface Scheduler {
  runNow(task: () => Awaitable<void>): TaskHandle;
  runLaterTicks(delayTicks: number, task: () => Awaitable<void>): TaskHandle;
  runTimerTicks(
    delayTicks: number,
    intervalTicks: number,
    task: () => Awaitable<void>,
  ): TaskHandle;
}
```

## Config And Data Types

```ts
export interface ConfigStore {
  get<T = unknown>(path: string): T | null;
  set(path: string, value: unknown): void;
  save(): void;
  reload(): void;
  keys(): string[];
}

export interface DataDirectory {
  path: string;
  resolve(...segments: string[]): string;
  readText(relativePath: string): string;
  writeText(relativePath: string, contents: string): void;
  exists(relativePath: string): boolean;
  mkdirs(relativePath?: string): void;
}
```

## Java Interop Type

```ts
export interface JavaInterop {
  type<T = unknown>(className: string): T;
}
```

This is how TS plugins break out of the limited stable SDK and call JVM APIs directly.

## Scope Boundary

The SDK should currently be understood as:

- a typed plugin authoring contract
- not a full Bukkit / Spigot / Paper wrapper
- compatible with both TS and JS authoring styles

If broader server coverage is needed, the correct next step is to grow the runtime host
bridge and update this document accordingly.
