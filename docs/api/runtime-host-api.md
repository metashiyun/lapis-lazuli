# Runtime Host API

This document defines the runtime contract exposed to a loaded JavaScript bundle.

The host context contains seven stable service groups plus one escape hatch:

- `logger`
- `events`
- `commands`
- `scheduler`
- `config`
- `dataDir`
- `server`
- `javaInterop`

## Lifecycle

A bundle exports a plugin object with this lifecycle shape:

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

`onDisable` runs during shutdown or hot reload if the plugin was successfully enabled.

## `PluginContext`

```ts
interface PluginContext {
  logger: Logger;
  events: EventRegistry;
  commands: CommandRegistry;
  scheduler: Scheduler;
  config: ConfigStore;
  dataDir: DataDirectory;
  server: ServerBridge;
  javaInterop: JavaInterop;
}
```

## `logger`

```ts
interface Logger {
  info(message: string): void;
  warn(message: string): void;
  error(message: string): void;
  debug(message: string): void;
}
```

Notes:

- `debug(...)` currently logs through the host logger as an info-level message with a
  `[debug]` prefix.
- bundle logs are prefixed on the Bukkit side with the bundle id.

## `events`

```ts
interface HookHandle {
  unsubscribe(): void;
}

interface EventRegistry {
  on<K extends keyof EventMap>(
    event: K,
    handler: (payload: EventMap[K]) => Awaitable<void>,
  ): HookHandle;
  onJava<T = unknown>(
    eventClassName: string,
    handler: (payload: T) => Awaitable<void>,
  ): HookHandle;
}
```

### Supported Event Keys

| Event key | Payload |
| --- | --- |
| `playerJoin` | `{ type: "playerJoin"; playerName: string; playerUuid: string; playerHandle?: unknown; javaEvent?: unknown; joinMessage?: string | null }` |
| `playerQuit` | `{ type: "playerQuit"; playerName: string; playerUuid: string; playerHandle?: unknown; javaEvent?: unknown; quitMessage?: string | null }` |
| `serverLoad` | `{ type: "serverLoad"; reload: boolean; javaEvent?: unknown }` |

Notes:

- only these three event keys are supported today
- payloads are plain JS objects
- typed payloads now also include raw handles where useful, such as `javaEvent` and `playerHandle`
- unsupported event keys fail at registration time

### Generic Java Events

`events.onJava(...)` subscribes to any JVM event class by fully qualified class name.

Example:

```ts
context.events.onJava("org.bukkit.event.block.BlockBreakEvent", (event: any) => {
  const player = event.getPlayer();
  context.logger.info(`Block break by ${player.getName()}`);
});
```

This is the main bridge for broader Bukkit / Spigot / Paper event coverage before a
typed wrapper exists for each event.

## `commands`

```ts
type CommandResult = void | boolean | string;

interface CommandSender {
  name: string;
  type: "player" | "console" | "other";
  uuid?: string;
  javaHandle?: unknown;
  sendMessage(message: string): void;
}

interface CommandExecutionContext {
  sender: CommandSender;
  args: string[];
  label: string;
}

interface CommandDefinition {
  name: string;
  description?: string;
  usage?: string;
  aliases?: string[];
  execute(context: CommandExecutionContext): Awaitable<CommandResult>;
}

interface CommandRegistry {
  register(command: CommandDefinition): HookHandle;
}
```

Command return handling:

- `true` or `false` maps directly to the underlying command success value
- a returned `string` is sent to the sender as a chat message and then treated as success
- `undefined` produces no extra output

The exposed command sender is a small DTO with an optional `javaHandle` for direct access
to the backing Bukkit sender object.

## `scheduler`

```ts
interface TaskHandle {
  cancel(): void;
}

interface Scheduler {
  runNow(task: () => Awaitable<void>): TaskHandle;
  runLaterTicks(delayTicks: number, task: () => Awaitable<void>): TaskHandle;
  runTimerTicks(
    delayTicks: number,
    intervalTicks: number,
    task: () => Awaitable<void>,
  ): TaskHandle;
}
```

Notes:

- the Bukkit adapter uses the server scheduler
- these helpers are for immediate, delayed, and repeating tasks
- there is no separate async scheduler abstraction yet

## `config`

```ts
interface ConfigStore {
  get<T = unknown>(path: string): T | null;
  set(path: string, value: unknown): void;
  save(): void;
  reload(): void;
  keys(): string[];
}
```

The Bukkit adapter backs this with `config.yml` inside the bundle directory.

Recommended value shapes:

- strings
- booleans
- numbers
- arrays
- plain objects

Those are the safest cross-language values for the current bridge.

## `dataDir`

```ts
interface DataDirectory {
  path: string;
  resolve(...segments: string[]): string;
  readText(relativePath: string): string;
  writeText(relativePath: string, contents: string): void;
  exists(relativePath: string): boolean;
  mkdirs(relativePath?: string): void;
}
```

Notes:

- the data directory is bundle-scoped
- the runtime creates it automatically
- path resolution is constrained so calls cannot escape the bundle data directory

## `server`

```ts
interface ServerBridge {
  bukkit: unknown;
  plugin: unknown;
  console: unknown;
  dispatchCommand(command: string): boolean;
  broadcast(message: string): number;
}
```

This bridge exposes raw runtime handles plus a few common server actions.

Notes:

- `bukkit` is the backing `org.bukkit.Server` instance
- `plugin` is the Lapis Lazuli runtime plugin instance
- `console` is the console sender handle
- `dispatchCommand(...)` runs a command as the console sender
- `broadcast(...)` sends a plain-text broadcast through the backing server

## `javaInterop`

```ts
interface JavaInterop {
  type<T = unknown>(className: string): T;
}
```

This is the low-level escape hatch to the JVM world.

Example:

```ts
const Bukkit = context.javaInterop.type<any>("org.bukkit.Bukkit");

const onlinePlayers = Bukkit.getOnlinePlayers();
```

Important boundary:

- this is not a stable Lapis Lazuli wrapper API
- it is direct JVM interop
- compatibility depends on the actual server implementation and visible classes
- it is the mechanism you use when the stable SDK does not expose a feature yet
