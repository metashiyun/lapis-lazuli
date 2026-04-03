from __future__ import annotations

from collections.abc import Callable, Mapping, Sequence
from dataclasses import asdict, dataclass, is_dataclass
import json
import inspect
from typing import Any

_MISSING = object()
_SCALAR_TYPES = (str, int, float, bool, bytes)


def _snake_to_camel(name: str) -> str:
    if "_" not in name:
        return name

    head, *tail = name.split("_")
    return head + "".join(part[:1].upper() + part[1:] for part in tail)


def _resolve_member_name(raw: Any, name: str) -> str:
    if hasattr(raw, name):
        return name

    camel_name = _snake_to_camel(name)
    if hasattr(raw, camel_name):
        return camel_name

    raise AttributeError(f"{type(raw).__name__} has no attribute {name!r}")


def _accepts_argument(callback: Callable[..., Any]) -> bool:
    try:
        signature = inspect.signature(callback)
    except (TypeError, ValueError):
        return True

    for parameter in signature.parameters.values():
        if parameter.kind in (
            inspect.Parameter.POSITIONAL_ONLY,
            inspect.Parameter.POSITIONAL_OR_KEYWORD,
            inspect.Parameter.VAR_POSITIONAL,
        ):
            return True

    return False


def _wrap_guest_callable(callback: Callable[..., Any]) -> Callable[..., Any]:
    def invoke(*args: Any, **kwargs: Any) -> Any:
        if kwargs:
            raise TypeError("Keyword arguments are not supported for raw runtime methods.")
        return wrap(callback(*(_unwrap(arg) for arg in args)))

    return invoke


def _wrap_callback(
    callback: Callable[..., Any],
    *,
    payload_wrapper: Callable[[Any], Any] = lambda value: value,
) -> Callable[..., Any]:
    accepts_argument = _accepts_argument(callback)

    def invoke(payload: Any = _MISSING) -> Any:
        if payload is _MISSING:
            return callback()
        if accepts_argument:
            return callback(payload_wrapper(payload))
        return callback()

    return invoke


def _looks_like_location(value: Any) -> bool:
    return all(hasattr(value, field) for field in ("x", "y", "z"))


def _looks_like_store(value: Any) -> bool:
    return all(hasattr(value, field) for field in ("get", "set", "delete", "save", "reload", "keys"))


def _looks_like_file_store(value: Any) -> bool:
    return all(hasattr(value, field) for field in ("path", "resolve", "readText", "writeText", "exists", "mkdirs"))


def _camelize_mapping(mapping: Mapping[Any, Any]) -> dict[Any, Any]:
    result: dict[Any, Any] = {}
    for key, value in mapping.items():
        normalized_key = _snake_to_camel(key) if isinstance(key, str) else key
        if value is None:
            continue
        result[normalized_key] = _unwrap(value)
    return result


def _unwrap(value: Any) -> Any:
    if isinstance(value, GuestProxy):
        return value.raw

    if isinstance(value, Location):
        return value.to_payload()

    if value is None or isinstance(value, _SCALAR_TYPES):
        return value

    if is_dataclass(value):
        return _camelize_mapping(asdict(value))

    if isinstance(value, Mapping):
        return _camelize_mapping(value)

    if isinstance(value, Sequence) and not isinstance(value, (str, bytes, bytearray)):
        return [_unwrap(item) for item in value]

    return value


def wrap(value: Any) -> Any:
    if isinstance(value, (GuestProxy, Location)) or value is None or isinstance(value, _SCALAR_TYPES):
        return value

    if isinstance(value, Mapping):
        return MappingProxy(value)

    if isinstance(value, Sequence) and not isinstance(value, (str, bytes, bytearray)):
        return [wrap(item) for item in value]

    if callable(value):
        return _wrap_guest_callable(value)

    if _looks_like_location(value):
        return Location.from_raw(value)

    if _looks_like_store(value):
        return KeyValueStore(value)

    if _looks_like_file_store(value):
        return FileStore(value)

    return GuestProxy(value)


@dataclass(slots=True)
class Location:
    x: float
    y: float
    z: float
    world: str | None = None
    yaw: float | None = None
    pitch: float | None = None

    @classmethod
    def from_raw(cls, raw: Any) -> "Location":
        return cls(
            world=getattr(raw, "world", None),
            x=getattr(raw, "x"),
            y=getattr(raw, "y"),
            z=getattr(raw, "z"),
            yaw=getattr(raw, "yaw", None),
            pitch=getattr(raw, "pitch", None),
        )

    def to_payload(self) -> dict[str, Any]:
        return _camelize_mapping(asdict(self))


@dataclass(slots=True)
class TitleOptions:
    subtitle: Any | None = None
    fade_in_ticks: int | None = None
    stay_ticks: int | None = None
    fade_out_ticks: int | None = None


class GuestProxy:
    __slots__ = ("_raw",)

    def __init__(self, raw: Any) -> None:
        object.__setattr__(self, "_raw", raw)

    @property
    def raw(self) -> Any:
        return object.__getattribute__(self, "_raw")

    def __getattr__(self, name: str) -> Any:
        raw_name = _resolve_member_name(self.raw, name)
        value = getattr(self.raw, raw_name)

        if raw_name == "unsafe":
            return UnsafeHandle(value)

        if callable(value):
            return _wrap_guest_callable(value)

        return wrap(value)

    def __setattr__(self, name: str, value: Any) -> None:
        if name.startswith("_"):
            object.__setattr__(self, name, value)
            return

        raw_name = _resolve_member_name(self.raw, name)
        setattr(self.raw, raw_name, _unwrap(value))

    def __repr__(self) -> str:
        return f"{self.__class__.__name__}({self.raw!r})"


class MappingProxy(GuestProxy):
    def __getattr__(self, name: str) -> Any:
        if name in self.raw:
            return wrap(self.raw[name])

        camel_name = _snake_to_camel(name)
        if camel_name in self.raw:
            return wrap(self.raw[camel_name])

        raise AttributeError(f"mapping has no attribute {name!r}")

    def __setattr__(self, name: str, value: Any) -> None:
        if name.startswith("_"):
            object.__setattr__(self, name, value)
            return

        if name in self.raw:
            self.raw[name] = _unwrap(value)
            return

        self.raw[_snake_to_camel(name)] = _unwrap(value)

    def __getitem__(self, key: Any) -> Any:
        return wrap(self.raw[key])

    def get(self, key: Any, default: Any = None) -> Any:
        if key in self.raw:
            return wrap(self.raw[key])
        return default

    def items(self) -> list[tuple[Any, Any]]:
        return [(key, wrap(value)) for key, value in self.raw.items()]

    def keys(self) -> list[Any]:
        return list(self.raw.keys())

    def values(self) -> list[Any]:
        return [wrap(value) for value in self.raw.values()]

    def __contains__(self, key: Any) -> bool:
        return key in self.raw

    def __iter__(self):
        return iter(self.raw)


class UnsafeHandle(GuestProxy):
    @property
    def handle(self) -> Any:
        return getattr(self.raw, "handle")


class Logger(GuestProxy):
    pass


class HookHandle(GuestProxy):
    pass


class TaskHandle(GuestProxy):
    pass


class KeyValueStore(GuestProxy):
    pass


class FileStore(GuestProxy):
    pass


class AppService(GuestProxy):
    @property
    def log(self) -> Logger:
        return Logger(getattr(self.raw, "log"))

    def on_shutdown(self, handler: Callable[..., Any]) -> HookHandle:
        return HookHandle(getattr(self.raw, "onShutdown")(_wrap_callback(handler)))


class CommandRegistry(GuestProxy):
    def register(
        self,
        name: str | Mapping[str, Any] | None = None,
        execute: Callable[..., Any] | None = None,
        **kwargs: Any,
    ) -> HookHandle:
        if isinstance(name, Mapping):
            if execute is not None or kwargs:
                raise TypeError("register() accepts either a mapping or keyword arguments, not both.")
            spec = dict(name)
        else:
            spec = dict(kwargs)
            if name is not None:
                spec["name"] = name
            if execute is not None:
                spec["execute"] = execute

        callback = spec.get("execute")
        if callback is None:
            raise TypeError("register() requires an execute callback.")

        spec["execute"] = _wrap_callback(callback, payload_wrapper=wrap)
        return HookHandle(getattr(self.raw, "register")(_unwrap(spec)))


class EventRegistry(GuestProxy):
    def on(self, event_name: str, handler: Callable[..., Any]) -> HookHandle:
        return HookHandle(getattr(self.raw, "on")(event_name, _wrap_callback(handler, payload_wrapper=wrap)))


class TaskRegistry(GuestProxy):
    def run(self, task: Callable[..., Any]) -> TaskHandle:
        return TaskHandle(getattr(self.raw, "run")(_wrap_callback(task)))

    def delay(self, delay_ticks: int, task: Callable[..., Any]) -> TaskHandle:
        return TaskHandle(getattr(self.raw, "delay")(delay_ticks, _wrap_callback(task)))

    def repeat(self, interval_ticks: int, task: Callable[..., Any]) -> TaskHandle:
        return TaskHandle(getattr(self.raw, "repeat")(interval_ticks, _wrap_callback(task)))

    def timer(self, delay_ticks: int, interval_ticks: int, task: Callable[..., Any]) -> TaskHandle:
        return TaskHandle(getattr(self.raw, "timer")(delay_ticks, interval_ticks, _wrap_callback(task)))


class PlayerDirectory(GuestProxy):
    pass


class WorldDirectory(GuestProxy):
    pass


class EntityDirectory(GuestProxy):
    def spawn(
        self,
        entity_type: str | Mapping[str, Any],
        location: Any | None = None,
        *,
        world: str | None = None,
    ) -> GuestProxy:
        if isinstance(entity_type, Mapping):
            if location is not None or world is not None:
                raise TypeError("spawn() accepts either a mapping or explicit arguments, not both.")
            spec = dict(entity_type)
        else:
            if location is None:
                raise TypeError("spawn() requires a location.")
            spec = {"type": entity_type, "location": location, "world": world}

        return wrap(getattr(self.raw, "spawn")(_unwrap(spec)))


class ItemFactory(GuestProxy):
    def create(self, item_type: str | Mapping[str, Any], **kwargs: Any) -> GuestProxy:
        if isinstance(item_type, Mapping):
            if kwargs:
                raise TypeError("create() accepts either a mapping or keyword arguments, not both.")
            spec = dict(item_type)
        else:
            spec = {"type": item_type, **kwargs}

        return wrap(getattr(self.raw, "create")(_unwrap(spec)))


class InventoryDirectory(GuestProxy):
    def create(self, title: Any | Mapping[str, Any], size: int | None = None, *, id: str | None = None) -> GuestProxy:
        if isinstance(title, Mapping):
            if size is not None or id is not None:
                raise TypeError("create() accepts either a mapping or explicit arguments, not both.")
            spec = dict(title)
        else:
            if size is None:
                raise TypeError("create() requires a size.")
            spec = {"title": title, "size": size, "id": id}

        return wrap(getattr(self.raw, "create")(_unwrap(spec)))

    def open(self, player: Any, inventory: Any) -> None:
        getattr(self.raw, "open")(_unwrap(player), _unwrap(inventory))


class ChatService(GuestProxy):
    pass


class EffectsService(GuestProxy):
    def play_sound(
        self,
        sound: str | Mapping[str, Any],
        *,
        location: Any | None = None,
        player: Any | None = None,
        volume: float | None = None,
        pitch: float | None = None,
    ) -> None:
        if isinstance(sound, Mapping):
            spec = dict(sound)
        else:
            spec = {
                "sound": sound,
                "location": location,
                "player": player,
                "volume": volume,
                "pitch": pitch,
            }

        getattr(self.raw, "playSound")(_unwrap(spec))

    def spawn_particle(
        self,
        particle: str | Mapping[str, Any],
        *,
        location: Any | None = None,
        count: int | None = None,
        offset_x: float | None = None,
        offset_y: float | None = None,
        offset_z: float | None = None,
        extra: float | None = None,
        players: Sequence[Any] | None = None,
    ) -> None:
        if isinstance(particle, Mapping):
            spec = dict(particle)
        else:
            spec = {
                "particle": particle,
                "location": location,
                "count": count,
                "offset_x": offset_x,
                "offset_y": offset_y,
                "offset_z": offset_z,
                "extra": extra,
                "players": players,
            }

        getattr(self.raw, "spawnParticle")(_unwrap(spec))

    def apply_potion(
        self,
        player: Any | Mapping[str, Any],
        *,
        effect: str | None = None,
        duration_ticks: int | None = None,
        amplifier: int | None = None,
        ambient: bool | None = None,
        particles: bool | None = None,
        icon: bool | None = None,
    ) -> bool:
        if isinstance(player, Mapping):
            spec = dict(player)
        else:
            spec = {
                "player": player,
                "effect": effect,
                "duration_ticks": duration_ticks,
                "amplifier": amplifier,
                "ambient": ambient,
                "particles": particles,
                "icon": icon,
            }

        return bool(getattr(self.raw, "applyPotion")(_unwrap(spec)))

    def clear_potion(self, player: Any, effect: str | None = None) -> None:
        if effect is None:
            getattr(self.raw, "clearPotion")(_unwrap(player))
            return

        getattr(self.raw, "clearPotion")(_unwrap(player), effect)


class RecipeService(GuestProxy):
    def register(self, spec: Mapping[str, Any]) -> GuestProxy:
        return wrap(getattr(self.raw, "register")(_unwrap(spec)))

    def register_shaped(
        self,
        *,
        id: str,
        result: Mapping[str, Any],
        shape: Sequence[str],
        ingredients: Mapping[str, Any],
    ) -> GuestProxy:
        return self.register(
            {
                "kind": "shaped",
                "id": id,
                "result": result,
                "shape": list(shape),
                "ingredients": dict(ingredients),
            },
        )

    def register_shapeless(
        self,
        *,
        id: str,
        result: Mapping[str, Any],
        ingredients: Sequence[Any],
    ) -> GuestProxy:
        return self.register(
            {
                "kind": "shapeless",
                "id": id,
                "result": result,
                "ingredients": list(ingredients),
            },
        )


class BossBarsService(GuestProxy):
    def create(
        self,
        title: Any | Mapping[str, Any],
        *,
        id: str | None = None,
        color: str | None = None,
        style: str | None = None,
        progress: float | None = None,
    ) -> GuestProxy:
        if isinstance(title, Mapping):
            spec = dict(title)
        else:
            spec = {
                "title": title,
                "id": id,
                "color": color,
                "style": style,
                "progress": progress,
            }

        return wrap(getattr(self.raw, "create")(_unwrap(spec)))


class ScoreboardsService(GuestProxy):
    def create(self, title: Any | Mapping[str, Any], *, id: str | None = None) -> GuestProxy:
        if isinstance(title, Mapping):
            spec = dict(title)
        else:
            spec = {"title": title, "id": id}

        return wrap(getattr(self.raw, "create")(_unwrap(spec)))


class StorageService:
    __slots__ = ("plugin", "files", "raw")

    def __init__(self, raw: Any) -> None:
        self.raw = raw
        self.plugin = KeyValueStore(getattr(raw, "plugin"))
        self.files = FileStore(getattr(raw, "files"))


@dataclass(slots=True)
class HttpResponse:
    url: str
    status: int
    ok: bool
    headers: dict[str, str]
    body: str

    @classmethod
    def from_raw(cls, raw: Any) -> "HttpResponse":
        raw_headers = wrap(getattr(raw, "headers"))
        return cls(
            url=getattr(raw, "url"),
            status=getattr(raw, "status"),
            ok=bool(getattr(raw, "ok")),
            headers={key: raw_headers[key] for key in raw_headers},
            body=getattr(raw, "body"),
        )

    @property
    def text(self) -> str:
        return self.body

    def json(self) -> Any:
        return json.loads(self.body)


class HttpService(GuestProxy):
    def fetch(
        self,
        url: str | Mapping[str, Any],
        method: str | None = None,
        *,
        headers: Mapping[str, str] | None = None,
        body: str | None = None,
    ) -> HttpResponse:
        if isinstance(url, Mapping):
            if method is not None or headers is not None or body is not None:
                raise TypeError("fetch() accepts either a mapping or explicit arguments, not both.")
            spec = dict(url)
        else:
            spec = {
                "url": url,
                "method": method,
                "headers": headers,
                "body": body,
            }

        return HttpResponse.from_raw(getattr(self.raw, "fetch")(_unwrap(spec)))

    def get(self, url: str, *, headers: Mapping[str, str] | None = None) -> HttpResponse:
        return self.fetch(url, headers=headers)

    def post(
        self,
        url: str,
        *,
        headers: Mapping[str, str] | None = None,
        body: str | None = None,
    ) -> HttpResponse:
        return self.fetch(url, method="POST", headers=headers, body=body)

    def put(
        self,
        url: str,
        *,
        headers: Mapping[str, str] | None = None,
        body: str | None = None,
    ) -> HttpResponse:
        return self.fetch(url, method="PUT", headers=headers, body=body)

    def delete(self, url: str, *, headers: Mapping[str, str] | None = None) -> HttpResponse:
        return self.fetch(url, method="DELETE", headers=headers)


class UnsafeEvents(GuestProxy):
    def on_java(self, event_class_name: str, handler: Callable[..., Any]) -> HookHandle:
        return HookHandle(getattr(self.raw, "onJava")(event_class_name, _wrap_callback(handler, payload_wrapper=wrap)))


class UnsafeJava(GuestProxy):
    def type(self, class_name: str) -> Any:
        return getattr(self.raw, "type")(class_name)


class UnsafeBackend(GuestProxy):
    @property
    def server(self) -> Any:
        return getattr(self.raw, "server")

    @property
    def plugin(self) -> Any:
        return getattr(self.raw, "plugin")

    @property
    def console(self) -> Any:
        return getattr(self.raw, "console")

    def dispatch_command(self, command: str) -> bool:
        return bool(getattr(self.raw, "dispatchCommand")(command))


class UnsafeBridge:
    __slots__ = ("events", "java", "backend", "raw")

    def __init__(self, raw: Any) -> None:
        self.raw = raw
        self.events = UnsafeEvents(getattr(raw, "events"))
        self.java = UnsafeJava(getattr(raw, "java"))
        self.backend = UnsafeBackend(getattr(raw, "backend"))


class PluginContext:
    __slots__ = (
        "app",
        "boss_bars",
        "chat",
        "commands",
        "config",
        "effects",
        "entities",
        "events",
        "http",
        "inventory",
        "items",
        "players",
        "raw",
        "recipes",
        "scoreboards",
        "storage",
        "tasks",
        "unsafe",
        "worlds",
    )

    def __init__(self, raw: Any) -> None:
        self.raw = raw
        self.app = AppService(getattr(raw, "app"))
        self.commands = CommandRegistry(getattr(raw, "commands"))
        self.events = EventRegistry(getattr(raw, "events"))
        self.tasks = TaskRegistry(getattr(raw, "tasks"))
        self.players = PlayerDirectory(getattr(raw, "players"))
        self.worlds = WorldDirectory(getattr(raw, "worlds"))
        self.entities = EntityDirectory(getattr(raw, "entities"))
        self.items = ItemFactory(getattr(raw, "items"))
        self.inventory = InventoryDirectory(getattr(raw, "inventory"))
        self.chat = ChatService(getattr(raw, "chat"))
        self.effects = EffectsService(getattr(raw, "effects"))
        self.recipes = RecipeService(getattr(raw, "recipes"))
        self.boss_bars = BossBarsService(getattr(raw, "bossBars"))
        self.scoreboards = ScoreboardsService(getattr(raw, "scoreboards"))
        self.storage = StorageService(getattr(raw, "storage"))
        self.http = HttpService(getattr(raw, "http"))
        self.config = KeyValueStore(getattr(raw, "config"))
        self.unsafe = UnsafeBridge(getattr(raw, "unsafe"))
