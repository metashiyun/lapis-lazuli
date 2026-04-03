export type Awaitable<T> = T | Promise<T>;

export interface HookHandle {
  unsubscribe(): void;
}

export interface TaskHandle {
  cancel(): void;
}

export interface Logger {
  info(message: TextInput): void;
  warn(message: TextInput): void;
  error(message: TextInput): void;
  debug(message: TextInput): void;
}

export interface TextPart {
  text: string;
  extra?: TextInput[];
}

export type TextInput = string | TextPart | TextInput[];

export interface TitleOptions {
  subtitle?: TextInput;
  fadeInTicks?: number;
  stayTicks?: number;
  fadeOutTicks?: number;
}

export interface Location {
  world?: string | null;
  x: number;
  y: number;
  z: number;
  yaw?: number;
  pitch?: number;
}

export interface KeyValueStore {
  get<T = unknown>(path: string): T | null;
  set(path: string, value: unknown): void;
  delete(path: string): void;
  save(): void;
  reload(): void;
  keys(): string[];
}

export interface FileStore {
  path: string;
  resolve(...segments: string[]): string;
  readText(relativePath: string): string;
  writeText(relativePath: string, contents: TextInput): void;
  exists(relativePath: string): boolean;
  mkdirs(relativePath?: string): void;
}

export interface UnsafeHandle {
  handle?: unknown;
}

export interface BlockHandle {
  type: string;
  worldName: string;
  location: Location;
  setType(type: string): boolean;
  unsafe?: UnsafeHandle;
}

export interface ItemHandle {
  type: string;
  amount(): number;
  setAmount(amount: number): void;
  name(): string | null;
  setName(name?: TextInput | null): void;
  lore(): string[];
  setLore(lore: TextInput[]): void;
  enchantments(): Record<string, number>;
  enchant(key: string, level: number): void;
  removeEnchantment(key: string): void;
  clone(): ItemHandle;
  tags?: KeyValueStore;
  unsafe?: UnsafeHandle;
}

export interface InventoryHandle {
  id?: string | null;
  title: string;
  size: number;
  get(slot: number): ItemHandle | null;
  set(slot: number, item: ItemHandle | null): void;
  add(item: ItemHandle): void;
  clear(slot?: number): void;
  open(player: PlayerHandle): void;
  unsafe?: UnsafeHandle;
}

export interface PlayerHandle {
  id: string;
  name: string;
  worldName(): string;
  location(): Location;
  sendMessage(message: TextInput): void;
  actionBar(message: TextInput): void;
  showTitle(title: TextInput, options?: TitleOptions): void;
  hasPermission(permission: string): boolean;
  teleport(location: Location): boolean;
  inventory(): InventoryHandle;
  tags?: KeyValueStore;
  unsafe?: UnsafeHandle;
}

export interface WorldHandle {
  name: string;
  environment: string;
  players(): PlayerHandle[];
  entities(): EntityHandle[];
  spawnLocation(): Location;
  time(): number;
  setTime(time: number): void;
  storming(): boolean;
  setStorming(storming: boolean): void;
  blockAt(location: Location): BlockHandle | null;
  unsafe?: UnsafeHandle;
}

export interface EntityHandle {
  id: string;
  type: string;
  name?: string | null;
  worldName(): string;
  location(): Location;
  teleport(location: Location): boolean;
  remove(): void;
  tags?: KeyValueStore;
  unsafe?: UnsafeHandle;
}

export interface ItemSpec {
  type: string;
  amount?: number;
  name?: TextInput | null;
  lore?: TextInput[];
  enchantments?: Record<string, number>;
}

export interface SoundSpec {
  sound: string;
  location?: Location;
  player?: PlayerHandle;
  volume?: number;
  pitch?: number;
}

export interface ParticleSpec {
  particle: string;
  location: Location;
  count?: number;
  offsetX?: number;
  offsetY?: number;
  offsetZ?: number;
  extra?: number;
  players?: PlayerHandle[];
}

export interface PotionEffectSpec {
  player: PlayerHandle;
  effect: string;
  durationTicks: number;
  amplifier?: number;
  ambient?: boolean;
  particles?: boolean;
  icon?: boolean;
}

export type RecipeIngredient = string | ItemSpec;

export interface ShapedRecipeSpec {
  kind: "shaped";
  id: string;
  result: ItemSpec;
  shape: string[];
  ingredients: Record<string, RecipeIngredient>;
}

export interface ShapelessRecipeSpec {
  kind: "shapeless";
  id: string;
  result: ItemSpec;
  ingredients: RecipeIngredient[];
}

export type RecipeSpec = ShapedRecipeSpec | ShapelessRecipeSpec;

export interface RecipeHandle {
  id: string;
  unregister(): void;
}

export interface BossBarHandle {
  id?: string | null;
  title(): string;
  setTitle(title: TextInput): void;
  progress(): number;
  setProgress(progress: number): void;
  color(): string;
  setColor(color: string): void;
  style(): string;
  setStyle(style: string): void;
  players(): PlayerHandle[];
  addPlayer(player: PlayerHandle): void;
  removePlayer(player: PlayerHandle): void;
  clearPlayers(): void;
  delete(): void;
  unsafe?: UnsafeHandle;
}

export interface ScoreboardHandle {
  id?: string | null;
  title(): string;
  setTitle(title: TextInput): void;
  setLine(score: number, text: TextInput): void;
  removeLine(score: number): void;
  clear(): void;
  viewers(): PlayerHandle[];
  show(player: PlayerHandle): void;
  hide(player: PlayerHandle): void;
  delete(): void;
  unsafe?: UnsafeHandle;
}

export interface CancellableEvent {
  cancelled: boolean;
  cancel(): void;
  uncancel(): void;
}

export interface ServerReadyEvent {
  type: "server.ready";
  reload: boolean;
}

export interface PlayerJoinEvent {
  type: "player.join";
  player: PlayerHandle;
  joinMessage?: string | null;
  setJoinMessage(message?: TextInput | null): void;
}

export interface PlayerQuitEvent {
  type: "player.quit";
  player: PlayerHandle;
  quitMessage?: string | null;
  setQuitMessage(message?: TextInput | null): void;
}

export interface PlayerChatEvent extends CancellableEvent {
  type: "player.chat";
  player: PlayerHandle;
  message: string;
  setMessage(message: TextInput): void;
  recipients(): PlayerHandle[];
}

export interface PlayerMoveEvent extends CancellableEvent {
  type: "player.move";
  player: PlayerHandle;
  from: Location;
  to: Location;
}

export interface PlayerInteractEvent extends CancellableEvent {
  type: "player.interact";
  player: PlayerHandle;
  action: string;
  hand?: string | null;
  item?: ItemHandle | null;
  block?: BlockHandle | null;
  face?: string | null;
}

export interface PlayerTeleportEvent extends CancellableEvent {
  type: "player.teleport";
  player: PlayerHandle;
  from: Location;
  to: Location;
  cause?: string | null;
}

export interface BlockBreakEvent extends CancellableEvent {
  type: "block.break";
  player?: PlayerHandle | null;
  block: BlockHandle;
  expToDrop: number;
  setExpToDrop(exp: number): void;
  dropItems: boolean;
  setDropItems(dropItems: boolean): void;
}

export interface BlockPlaceEvent extends CancellableEvent {
  type: "block.place";
  player?: PlayerHandle | null;
  block: BlockHandle;
  against?: BlockHandle | null;
  item?: ItemHandle | null;
  canBuild: boolean;
}

export interface EntityDamageEvent extends CancellableEvent {
  type: "entity.damage";
  entity: EntityHandle;
  damage: number;
  finalDamage?: number;
  cause?: string | null;
  damager?: EntityHandle | null;
  setDamage(damage: number): void;
}

export interface EntityDeathEvent {
  type: "entity.death";
  entity: EntityHandle;
  drops(): ItemHandle[];
  droppedExp: number;
  setDroppedExp(exp: number): void;
}

export interface InventoryClickEvent extends CancellableEvent {
  type: "inventory.click";
  player?: PlayerHandle | null;
  inventory: InventoryHandle;
  slot: number;
  clickType: string;
  currentItem?: ItemHandle | null;
  cursorItem?: ItemHandle | null;
}

export interface InventoryCloseEvent {
  type: "inventory.close";
  player?: PlayerHandle | null;
  inventory: InventoryHandle;
}

export interface WorldLoadEvent {
  type: "world.load";
  world: WorldHandle;
}

export interface WorldUnloadEvent extends CancellableEvent {
  type: "world.unload";
  world: WorldHandle;
}

export interface EventMap {
  "server.ready": ServerReadyEvent;
  "player.join": PlayerJoinEvent;
  "player.quit": PlayerQuitEvent;
  "player.chat": PlayerChatEvent;
  "player.move": PlayerMoveEvent;
  "player.interact": PlayerInteractEvent;
  "player.teleport": PlayerTeleportEvent;
  "block.break": BlockBreakEvent;
  "block.place": BlockPlaceEvent;
  "entity.damage": EntityDamageEvent;
  "entity.death": EntityDeathEvent;
  "inventory.click": InventoryClickEvent;
  "inventory.close": InventoryCloseEvent;
  "world.load": WorldLoadEvent;
  "world.unload": WorldUnloadEvent;
}

export interface EventRegistry {
  on<K extends keyof EventMap>(
    event: K,
    handler: (payload: EventMap[K]) => Awaitable<void>,
  ): HookHandle;
}

export interface CommandSender {
  name: string;
  type: "player" | "console" | "other";
  id?: string;
  player?: PlayerHandle;
  sendMessage(message: TextInput): void;
  hasPermission(permission: string): boolean;
  unsafe?: UnsafeHandle;
}

export interface CommandExecutionContext {
  sender: CommandSender;
  args: string[];
  label: string;
  command: string;
}

export type CommandResult = void | boolean | TextInput;

export interface CommandDefinition {
  name: string;
  description?: TextInput;
  usage?: TextInput;
  aliases?: string[];
  permission?: string;
  execute(context: CommandExecutionContext): Awaitable<CommandResult>;
}

export interface CommandRegistry {
  register(command: CommandDefinition): HookHandle;
}

export interface TaskRegistry {
  run(task: () => Awaitable<void>): TaskHandle;
  delay(delayTicks: number, task: () => Awaitable<void>): TaskHandle;
  repeat(intervalTicks: number, task: () => Awaitable<void>): TaskHandle;
  timer(delayTicks: number, intervalTicks: number, task: () => Awaitable<void>): TaskHandle;
}

export interface PlayerDirectory {
  online(): PlayerHandle[];
  get(query: string): PlayerHandle | null;
  require(query: string): PlayerHandle;
}

export interface WorldDirectory {
  list(): WorldHandle[];
  get(name: string): WorldHandle | null;
  require(name: string): WorldHandle;
}

export interface EntityDirectory {
  get(id: string): EntityHandle | null;
  spawn(spec: { type: string; location: Location; world?: string }): EntityHandle;
}

export interface ItemFactory {
  create(spec: ItemSpec): ItemHandle;
}

export interface InventoryDirectory {
  create(spec: { id?: string; title: TextInput; size: number }): InventoryHandle;
  open(player: PlayerHandle, inventory: InventoryHandle): void;
}

export interface ChatService {
  broadcast(message: TextInput): number;
}

export interface EffectsService {
  playSound(spec: SoundSpec): void;
  spawnParticle(spec: ParticleSpec): void;
  applyPotion(spec: PotionEffectSpec): boolean;
  clearPotion(player: PlayerHandle, effect?: string): void;
}

export interface RecipesService {
  register(spec: RecipeSpec): RecipeHandle;
}

export interface BossBarsService {
  create(spec: {
    id?: string;
    title: TextInput;
    color?: string;
    style?: string;
    progress?: number;
  }): BossBarHandle;
}

export interface ScoreboardsService {
  create(spec: { id?: string; title: TextInput }): ScoreboardHandle;
}

export interface StorageService {
  plugin: KeyValueStore;
  files: FileStore;
}

export interface HttpRequestInit {
  method?: string;
  headers?: Record<string, string>;
  body?: string;
}

export interface HttpRequest {
  url: string;
  method?: string;
  headers?: Record<string, string>;
  body?: string;
}

export interface HttpResponse {
  url: string;
  status: number;
  ok: boolean;
  headers: Record<string, string>;
  body: string;
  text(): string;
}

export interface HttpService {
  fetch(url: string, init?: HttpRequestInit): Promise<HttpResponse>;
  fetch(request: HttpRequest): Promise<HttpResponse>;
}

export interface UnsafeBridge {
  events: {
    onJava<T = unknown>(
      eventClassName: string,
      handler: (payload: T) => Awaitable<void>,
    ): HookHandle;
  };
  java: {
    type<T = unknown>(className: string): T;
  };
  backend: {
    server: unknown;
    plugin: unknown;
    console: unknown;
    dispatchCommand(command: string): boolean;
  };
}

export interface AppService {
  id: string;
  name: string;
  version: string;
  engine: string;
  apiVersion: string;
  backend: string;
  runtime: string;
  log: Logger;
  onShutdown(handler: () => Awaitable<void>): HookHandle;
}

export interface PluginContext {
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
  effects: EffectsService;
  recipes: RecipesService;
  bossBars: BossBarsService;
  scoreboards: ScoreboardsService;
  storage: StorageService;
  http: HttpService;
  config: KeyValueStore;
  unsafe: UnsafeBridge;
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
