# Runtime Host API

This document defines the runtime contract exposed to a loaded JavaScript bundle.

The host context contains six stable service groups plus one escape hatch:

- `logger`
- `events`
- `commands`
- `scheduler`
- `config`
- `dataDir`
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
}
```

### Supported Event Keys

| Event key | Payload |
| --- | --- |
| `playerJoin` | `{ type: "playerJoin"; playerName: string; playerUuid: string; joinMessage?: string | null }` |
| `playerQuit` | `{ type: "playerQuit"; playerName: string; playerUuid: string; quitMessage?: string | null }` |
| `serverLoad` | `{ type: "serverLoad"; reload: boolean }` |

Notes:

- only these three event keys are supported today
- payloads are plain JS objects, not raw Bukkit or Paper event instances
- unsupported event keys fail at registration time

## `commands`

```ts
type CommandResult = void | boolean | string;

interface CommandSender {
  name: string;
  type: "player" | "console" | "other";
  uuid?: string;
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

The exposed command sender is a small DTO, not the raw Bukkit `CommandSender`.

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
