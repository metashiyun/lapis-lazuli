import { Worker, isMainThread, parentPort, workerData } from "node:worker_threads";
import { createRequire } from "node:module";
import process from "node:process";
import { TextDecoder, TextEncoder } from "node:util";

const encoder = new TextEncoder();
const decoder = new TextDecoder();
const SHARED_BUFFER_BYTES = 1024 * 1024;

if (isMainThread) {
  runMainThread();
} else {
  runWorkerThread();
}

function runMainThread() {
  const mainFile = process.argv[2];
  if (!mainFile) {
    process.stderr.write("Missing bundle entrypoint for Node bootstrap.\n");
    process.exit(1);
    return;
  }

  const worker = new Worker(new URL(import.meta.url), {
    workerData: { mainFile },
  });

  const pending = new Map();
  let nextRequestId = 1;
  let inputBuffer = "";

  process.stdin.setEncoding("utf8");
  process.stdin.on("data", (chunk) => {
    inputBuffer += chunk;
    flushTransportInput();
  });
  process.stdin.on("end", async () => {
    await worker.terminate();
    process.exit(0);
  });
  process.stdin.resume();

  worker.on("message", (message) => {
    if (message.type === "parentResponse") {
      writeTransportMessage({
        type: "response",
        id: message.id,
        ok: message.ok,
        result: message.result,
        error: message.error,
      });
      return;
    }

    if (message.type === "hostRequest") {
      requestParent(message.method, message.params)
        .then((result) => {
          writeShared(message.shared, true, result);
        })
        .catch((error) => {
          writeShared(message.shared, false, serializeError(error));
        });
    }
  });

  worker.on("error", (error) => {
    process.stderr.write(`${error?.stack ?? error?.message ?? String(error)}\n`);
    process.exit(1);
  });
  worker.on("exit", (code) => {
    process.exit(code ?? 0);
  });

  function flushTransportInput() {
    while (true) {
      const newlineIndex = inputBuffer.indexOf("\n");
      if (newlineIndex < 0) {
        break;
      }

      const line = inputBuffer.slice(0, newlineIndex);
      inputBuffer = inputBuffer.slice(newlineIndex + 1);
      if (!line.trim()) {
        continue;
      }

      const message = JSON.parse(line);
      if (message.type === "response") {
        const pendingRequest = pending.get(message.id);
        if (!pendingRequest) {
          continue;
        }
        pending.delete(message.id);
        if (message.ok) {
          pendingRequest.resolve(message.result);
        } else {
          pendingRequest.reject(deserializeError(message.error));
        }
        continue;
      }

      if (message.type === "request") {
        worker.postMessage({
          type: "parentRequest",
          id: message.id,
          method: message.method,
          params: message.params,
        });
      }
    }
  }

  function requestParent(method, params) {
    const requestId = `host-${nextRequestId++}`;
    return new Promise((resolve, reject) => {
      pending.set(requestId, { resolve, reject });
      writeTransportMessage({
        type: "request",
        id: requestId,
        method,
        params,
      });
    });
  }
}

function runWorkerThread() {
  const require = createRequire(import.meta.url);
  const moduleExports = require(workerData.mainFile);
  const plugin = moduleExports?.default ?? moduleExports;

  if (!plugin || typeof plugin !== "object" || typeof plugin.name !== "string" || !plugin.name.trim()) {
    throw new Error("Node bundle must export a plugin object with a name.");
  }

  const callbacks = new Map();
  const shutdownCallbacks = [];
  let nextCallbackId = 1;
  let context = null;
  let shutdownFlushed = false;

  parentPort.on("message", (message) => {
    if (message.type !== "parentRequest") {
      return;
    }

    Promise.resolve()
      .then(() => handleParentRequest(message.method, decode(message.params)))
      .then((result) => {
        parentPort.postMessage({
          type: "parentResponse",
          id: message.id,
          ok: true,
          result: encode(normalizeCallbackReturn(result)),
        });
      })
      .catch((error) => {
        parentPort.postMessage({
          type: "parentResponse",
          id: message.id,
          ok: false,
          error: serializeError(error),
        });
      });
  });

  function handleParentRequest(method, params) {
    switch (method) {
      case "plugin.describe":
        return {
          name: plugin.name,
          version: plugin.version ?? null,
        };
      case "plugin.enable":
        return Promise.resolve(plugin.onEnable?.(getContext()));
      case "plugin.disable":
        return Promise.resolve(plugin.onDisable?.(getContext()))
          .then(() => flushShutdownCallbacks());
      case "plugin.abort":
        return flushShutdownCallbacks();
      case "callback.invoke": {
        const callback = callbacks.get(params.callbackId);
        if (!callback) {
          throw new Error(`Unknown callback "${params.callbackId}".`);
        }
        return Promise.resolve(callback(params.payload))
          .then((result) => normalizeCallbackReturn(result));
      }
      default:
        throw new Error(`Unsupported worker request "${method}".`);
    }
  }

  function getContext() {
    if (context) {
      return context;
    }

    const description = decode(callHost("context.describe", null));
    const appDescriptor = description.app;

    context = {
      app: {
        ...appDescriptor,
        log: {
          info(message) {
            callHost("app.log", { level: "info", message: textToString(message) });
          },
          warn(message) {
            callHost("app.log", { level: "warn", message: textToString(message) });
          },
          error(message) {
            callHost("app.log", { level: "error", message: textToString(message) });
          },
          debug(message) {
            callHost("app.log", { level: "debug", message: textToString(message) });
          },
        },
        onShutdown(handler) {
          const callbackId = rememberCallback(handler);
          shutdownCallbacks.unshift(callbackId);
          return {
            unsubscribe() {
              const index = shutdownCallbacks.indexOf(callbackId);
              if (index >= 0) {
                shutdownCallbacks.splice(index, 1);
              }
              callbacks.delete(callbackId);
            },
          };
        },
      },
      commands: {
        register(command) {
          return decode(callHost("commands.register", {
            name: command.name,
            description: command.description == null ? "" : textToString(command.description),
            usage: command.usage == null ? "" : textToString(command.usage),
            aliases: command.aliases ?? [],
            permission: command.permission ?? null,
            execute: command.execute,
          }));
        },
      },
      events: {
        on(event, handler) {
          return decode(callHost("events.on", { event, handler }));
        },
      },
      tasks: {
        run(task) {
          return decode(callHost("tasks.run", { task }));
        },
        delay(delayTicks, task) {
          return decode(callHost("tasks.delay", { delayTicks, task }));
        },
        repeat(intervalTicks, task) {
          return decode(callHost("tasks.repeat", { intervalTicks, task }));
        },
        timer(delayTicks, intervalTicks, task) {
          return decode(callHost("tasks.timer", { delayTicks, intervalTicks, task }));
        },
      },
      players: {
        online() {
          return decode(callHost("players.online", null));
        },
        get(query) {
          return decode(callHost("players.get", { query }));
        },
        require(query) {
          return decode(callHost("players.require", { query }));
        },
      },
      worlds: {
        list() {
          return decode(callHost("worlds.list", null));
        },
        get(name) {
          return decode(callHost("worlds.get", { name }));
        },
        require(name) {
          return decode(callHost("worlds.require", { name }));
        },
      },
      entities: {
        get(id) {
          return decode(callHost("entities.get", { id }));
        },
        spawn(spec) {
          return decode(callHost("entities.spawn", {
            type: spec.type,
            location: normalizeLocation(spec.location),
            world: spec.world ?? null,
          }));
        },
      },
      items: {
        create(spec) {
          return decode(callHost("items.create", normalizeItemSpec(spec)));
        },
      },
      inventory: {
        create(spec) {
          return decode(callHost("inventory.create", {
            id: spec.id ?? null,
            title: textToString(spec.title),
            size: spec.size,
          }));
        },
        open(player, inventory) {
          callHost("inventory.open", { player, inventory });
        },
      },
      chat: {
        broadcast(message) {
          return callHost("chat.broadcast", { message: textToString(message) });
        },
      },
      effects: {
        playSound(spec) {
          callHost("effects.playSound", normalizeEffectSpec(spec));
        },
        spawnParticle(spec) {
          callHost("effects.spawnParticle", {
            particle: spec.particle,
            location: normalizeLocation(spec.location),
            count: spec.count ?? 1,
            offsetX: spec.offsetX ?? 0,
            offsetY: spec.offsetY ?? 0,
            offsetZ: spec.offsetZ ?? 0,
            extra: spec.extra ?? 0,
            players: spec.players ?? [],
          });
        },
        applyPotion(spec) {
          return callHost("effects.applyPotion", {
            player: spec.player,
            effect: spec.effect,
            durationTicks: spec.durationTicks,
            amplifier: spec.amplifier ?? 0,
            ambient: spec.ambient ?? false,
            particles: spec.particles ?? true,
            icon: spec.icon ?? true,
          });
        },
        clearPotion(player, effect) {
          callHost("effects.clearPotion", { player, effect: effect ?? null });
        },
      },
      recipes: {
        register(spec) {
          return decode(callHost("recipes.register", normalizeRecipeSpec(spec)));
        },
      },
      bossBars: {
        create(spec) {
          return decode(callHost("bossBars.create", {
            id: spec.id ?? null,
            title: textToString(spec.title),
            color: spec.color ?? null,
            style: spec.style ?? null,
            progress: spec.progress ?? null,
          }));
        },
      },
      scoreboards: {
        create(spec) {
          return decode(callHost("scoreboards.create", {
            id: spec.id ?? null,
            title: textToString(spec.title),
          }));
        },
      },
      storage: decode(encode(description.storage)),
      http: {
        fetch(urlOrRequest, init) {
          const request = typeof urlOrRequest === "string"
            ? { url: urlOrRequest, ...init }
            : urlOrRequest;
          const response = decode(callHost("http.fetch", {
            url: request.url,
            method: request.method ?? "GET",
            headers: request.headers ?? {},
            body: request.body ?? null,
          }));
          return Promise.resolve(inflateHttpResponse(response));
        },
      },
      config: decode(encode(description.config)),
      unsafe: {
        events: {
          onJava() {
            throw new Error("context.unsafe.events.onJava is not available in the Node runtime.");
          },
        },
        java: {
          type() {
            throw new Error("context.unsafe.java.type is not available in the Node runtime.");
          },
        },
        backend: {
          server: null,
          plugin: null,
          console: null,
          dispatchCommand(command) {
            return callHost("unsafe.dispatchCommand", { command });
          },
        },
      },
    };

    return context;
  }

  function flushShutdownCallbacks() {
    if (shutdownFlushed) {
      return null;
    }
    shutdownFlushed = true;

    return shutdownCallbacks.reduce(
      (promise, callbackId) => promise.then(() => {
        const callback = callbacks.get(callbackId);
        if (!callback) {
          return null;
        }
        return Promise.resolve(callback());
      }),
      Promise.resolve(),
    );
  }

  function rememberCallback(callback) {
    const callbackId = `cb-${nextCallbackId++}`;
    callbacks.set(callbackId, callback);
    return callbackId;
  }

  function callHost(method, params) {
    const shared = new SharedArrayBuffer(SHARED_BUFFER_BYTES);
    const header = new Int32Array(shared, 0, 2);
    parentPort.postMessage({
      type: "hostRequest",
      method,
      params: encode(params),
      shared,
    });

    Atomics.wait(header, 0, 0);
    const length = header[1];
    const payloadText = decoder.decode(new Uint8Array(shared, 8, length));
    const payload = JSON.parse(payloadText);
    if (header[0] === 1) {
      return payload.result;
    }
    throw deserializeError(payload.error);
  }

  function encode(value) {
    if (value == null || typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
      return value;
    }

    if (Array.isArray(value)) {
      return value.map((entry) => encode(entry));
    }

    if (typeof value === "function") {
      return {
        __lapisCallback: rememberCallback(value),
      };
    }

    if (typeof value === "object") {
      if (typeof value.__lapisRef === "string" && typeof value.__lapisType === "string") {
        return {
          __lapisRef: value.__lapisRef,
          __lapisType: value.__lapisType,
        };
      }

      return Object.fromEntries(
        Object.entries(value).map(([key, nestedValue]) => [key, encode(nestedValue)]),
      );
    }

    return null;
  }

  function decode(value) {
    if (value == null || typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
      return value;
    }

    if (Array.isArray(value)) {
      return value.map((entry) => decode(entry));
    }

    if (typeof value === "object") {
      if (typeof value.__lapisCallback === "string") {
        return (payload) => decode(callHost("callback.invoke", {
          callbackId: value.__lapisCallback,
          payload: normalizeCallbackPayload(payload),
        }));
      }

      if (typeof value.__lapisRef === "string" && typeof value.__lapisType === "string") {
        return inflateHandle(value.__lapisType, value.__lapisRef, value);
      }

      return Object.fromEntries(
        Object.entries(value).map(([key, nestedValue]) => [key, decode(nestedValue)]),
      );
    }

    return value;
  }

  function inflateHandle(type, ref, snapshot) {
    const base = {
      __lapisType: type,
      __lapisRef: ref,
      unsafe: { handle: null },
    };

    if ("id" in snapshot) {
      base.id = snapshot.id;
    }
    if ("name" in snapshot) {
      base.name = snapshot.name;
    }
    if ("type" in snapshot && type !== "world") {
      base.type = snapshot.type;
    }
    if ("title" in snapshot) {
      base.title = snapshot.title;
    }
    if ("size" in snapshot) {
      base.size = snapshot.size;
    }
    if ("environment" in snapshot) {
      base.environment = snapshot.environment;
    }
    if ("path" in snapshot) {
      base.path = snapshot.path;
    }
    if ("tags" in snapshot) {
      base.tags = decode(snapshot.tags);
    }

    const callHandle = (method, args = []) => decode(callHost("handle.call", {
      target: base,
      method,
      args,
    }));

    switch (type) {
      case "registration":
        base.unsubscribe = () => callHandle("unsubscribe");
        return base;
      case "task":
        base.cancel = () => callHandle("cancel");
        return base;
      case "recipe":
        base.unregister = () => callHandle("unregister");
        return base;
      case "config":
      case "store":
        base.get = (path) => callHandle("get", [path]);
        base.set = (path, payload) => callHandle("set", [path, payload]);
        if (type === "store") {
          base.delete = (path) => callHandle("delete", [path]);
        }
        base.save = () => callHandle("save");
        base.reload = () => callHandle("reload");
        base.keys = () => callHandle("keys");
        return base;
      case "files":
        base.resolve = (...segments) => callHandle("resolve", segments);
        base.readText = (relativePath) => callHandle("readText", [relativePath]);
        base.writeText = (relativePath, contents) => callHandle("writeText", [relativePath, textToString(contents)]);
        base.exists = (relativePath) => callHandle("exists", [relativePath]);
        base.mkdirs = (relativePath = "") => callHandle("mkdirs", [relativePath]);
        return base;
      case "player":
        base.worldName = () => callHandle("worldName");
        base.location = () => callHandle("location");
        base.sendMessage = (message) => callHandle("sendMessage", [textToString(message)]);
        base.actionBar = (message) => callHandle("actionBar", [textToString(message)]);
        base.showTitle = (title, options = {}) => callHandle("showTitle", [
          textToString(title),
          {
            subtitle: options.subtitle == null ? "" : textToString(options.subtitle),
            fadeInTicks: options.fadeInTicks,
            stayTicks: options.stayTicks,
            fadeOutTicks: options.fadeOutTicks,
          },
        ]);
        base.hasPermission = (permission) => callHandle("hasPermission", [permission]);
        base.teleport = (location) => callHandle("teleport", [normalizeLocation(location)]);
        base.inventory = () => callHandle("inventory");
        return base;
      case "world":
        base.players = () => callHandle("players");
        base.entities = () => callHandle("entities");
        base.spawnLocation = () => callHandle("spawnLocation");
        base.time = () => callHandle("time");
        base.setTime = (time) => callHandle("setTime", [time]);
        base.storming = () => callHandle("storming");
        base.setStorming = (storming) => callHandle("setStorming", [storming]);
        base.blockAt = (location) => callHandle("blockAt", [normalizeLocation(location)]);
        return base;
      case "entity":
        base.worldName = () => callHandle("worldName");
        base.location = () => callHandle("location");
        base.teleport = (location) => callHandle("teleport", [normalizeLocation(location)]);
        base.remove = () => callHandle("remove");
        return base;
      case "block":
        base.location = () => callHandle("location");
        base.setType = (blockType) => callHandle("setType", [blockType]);
        return base;
      case "item":
        base.amount = () => callHandle("amount");
        base.setAmount = (amount) => callHandle("setAmount", [amount]);
        base.name = () => callHandle("name");
        base.setName = (name) => callHandle("setName", [name == null ? null : textToString(name)]);
        base.lore = () => callHandle("lore");
        base.setLore = (lore) => callHandle("setLore", [(lore ?? []).map((entry) => textToString(entry))]);
        base.enchantments = () => callHandle("enchantments");
        base.enchant = (key, level) => callHandle("enchant", [key, level]);
        base.removeEnchantment = (key) => callHandle("removeEnchantment", [key]);
        base.clone = () => callHandle("clone");
        return base;
      case "inventory":
        base.get = (slot) => callHandle("get", [slot]);
        base.set = (slot, item) => callHandle("set", [slot, item]);
        base.add = (item) => callHandle("add", [item]);
        base.clear = (slot) => slot == null ? callHandle("clear") : callHandle("clearSlot", [slot]);
        base.open = (player) => callHandle("open", [player]);
        return base;
      case "bossBar":
        base.title = () => callHandle("title");
        base.setTitle = (title) => callHandle("setTitle", [textToString(title)]);
        base.progress = () => callHandle("progress");
        base.setProgress = (progress) => callHandle("setProgress", [progress]);
        base.color = () => callHandle("color");
        base.setColor = (color) => callHandle("setColor", [color]);
        base.style = () => callHandle("style");
        base.setStyle = (style) => callHandle("setStyle", [style]);
        base.players = () => callHandle("players");
        base.addPlayer = (player) => callHandle("addPlayer", [player]);
        base.removePlayer = (player) => callHandle("removePlayer", [player]);
        base.clearPlayers = () => callHandle("clearPlayers");
        base.delete = () => callHandle("delete");
        return base;
      case "scoreboard":
        base.title = () => callHandle("title");
        base.setTitle = (title) => callHandle("setTitle", [textToString(title)]);
        base.setLine = (score, text) => callHandle("setLine", [score, textToString(text)]);
        base.removeLine = (score) => callHandle("removeLine", [score]);
        base.clear = () => callHandle("clear");
        base.viewers = () => callHandle("viewers");
        base.show = (player) => callHandle("show", [player]);
        base.hide = (player) => callHandle("hide", [player]);
        base.delete = () => callHandle("delete");
        return base;
      default:
        return base;
    }
  }
}

function writeTransportMessage(message) {
  process.stdout.write(`${JSON.stringify(message)}\n`);
}

function writeShared(shared, ok, payload) {
  const header = new Int32Array(shared, 0, 2);
  const serialized = JSON.stringify(ok ? { result: payload } : { error: payload });
  let bytes = encoder.encode(serialized);

  if (bytes.length > shared.byteLength - 8) {
    bytes = encoder.encode(JSON.stringify({
      error: { message: "Node host response exceeded the shared transport buffer." },
    }));
    ok = false;
  }

  new Uint8Array(shared, 8, bytes.length).set(bytes);
  header[1] = bytes.length;
  Atomics.store(header, 0, ok ? 1 : -1);
  Atomics.notify(header, 0, 1);
}

function serializeError(error) {
  if (error && typeof error === "object") {
    return {
      message: error.message ?? String(error),
      stack: error.stack ?? null,
    };
  }
  return {
    message: String(error),
    stack: null,
  };
}

function deserializeError(error) {
  const result = new Error(error?.message ?? "Unknown Node bridge error.");
  if (error?.stack) {
    result.stack = error.stack;
  }
  return result;
}

function textToString(input) {
  if (input == null) {
    return "";
  }
  if (typeof input === "string") {
    return input;
  }
  if (Array.isArray(input)) {
    return input.map((entry) => textToString(entry)).join("");
  }
  if (typeof input === "object" && typeof input.text === "string") {
    return `${input.text}${textToString(input.extra ?? [])}`;
  }
  return String(input);
}

function isTextInput(value) {
  if (typeof value === "string") {
    return true;
  }
  if (Array.isArray(value)) {
    return value.every((entry) => isTextInput(entry));
  }
  return Boolean(value && typeof value === "object" && typeof value.text === "string");
}

function normalizeCallbackPayload(payload) {
  return isTextInput(payload) ? textToString(payload) : payload;
}

function normalizeCallbackReturn(result) {
  return isTextInput(result) ? textToString(result) : result;
}

function normalizeLocation(location) {
  return {
    world: location.world ?? null,
    x: location.x,
    y: location.y,
    z: location.z,
    yaw: location.yaw ?? 0,
    pitch: location.pitch ?? 0,
  };
}

function normalizeItemSpec(spec) {
  return {
    type: spec.type,
    amount: spec.amount ?? 1,
    name: spec.name == null ? null : textToString(spec.name),
    lore: (spec.lore ?? []).map((entry) => textToString(entry)),
    enchantments: spec.enchantments ?? {},
  };
}

function normalizeRecipeIngredient(ingredient) {
  if (typeof ingredient === "string") {
    return ingredient;
  }
  return normalizeItemSpec(ingredient);
}

function normalizeRecipeSpec(spec) {
  if (spec.kind === "shaped") {
    return {
      kind: "shaped",
      id: spec.id,
      result: normalizeItemSpec(spec.result),
      shape: spec.shape,
      ingredients: Object.fromEntries(
        Object.entries(spec.ingredients).map(([key, value]) => [key, normalizeRecipeIngredient(value)]),
      ),
    };
  }

  return {
    kind: "shapeless",
    id: spec.id,
    result: normalizeItemSpec(spec.result),
    ingredients: spec.ingredients.map((ingredient) => normalizeRecipeIngredient(ingredient)),
  };
}

function normalizeEffectSpec(spec) {
  const result = {
    sound: spec.sound,
    volume: spec.volume ?? 1,
    pitch: spec.pitch ?? 1,
  };

  if (spec.player) {
    result.player = spec.player;
  }
  if (spec.location) {
    result.location = normalizeLocation(spec.location);
  }
  return result;
}

function inflateHttpResponse(response) {
  return {
    ...response,
    text() {
      return response.body;
    },
  };
}
