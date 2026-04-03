export type Awaitable<T> = T | Promise<T>;

export interface HookHandle {
  unsubscribe(): void;
}

export interface TaskHandle {
  cancel(): void;
}

export interface Logger {
  info(message: string): void;
  warn(message: string): void;
  error(message: string): void;
  debug(message: string): void;
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

export interface Scheduler {
  runNow(task: () => Awaitable<void>): TaskHandle;
  runLaterTicks(delayTicks: number, task: () => Awaitable<void>): TaskHandle;
  runTimerTicks(
    delayTicks: number,
    intervalTicks: number,
    task: () => Awaitable<void>,
  ): TaskHandle;
}

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

export interface JavaInterop {
  type<T = unknown>(className: string): T;
}

export interface PluginContext {
  logger: Logger;
  events: EventRegistry;
  commands: CommandRegistry;
  scheduler: Scheduler;
  config: ConfigStore;
  dataDir: DataDirectory;
  javaInterop: JavaInterop;
}

export interface PluginDefinition {
  name: string;
  version?: string;
  onEnable?(context: PluginContext): Awaitable<void>;
  onDisable?(context: PluginContext): Awaitable<void>;
}

export function definePlugin<T extends PluginDefinition>(definition: T): T {
  return definition;
}

