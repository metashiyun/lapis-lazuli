from __future__ import annotations

import unittest

from lapis_lazuli import Plugin, PluginContext


class RawLogger:
    def __init__(self) -> None:
        self.messages: list[str] = []

    def info(self, message: str) -> None:
        self.messages.append(message)


class RawApp:
    def __init__(self) -> None:
        self.id = "hello-python"
        self.name = "Hello Python"
        self.version = "0.1.0"
        self.engine = "python"
        self.apiVersion = "1.0"
        self.backend = "paper"
        self.runtime = "test"
        self.log = RawLogger()

    def onShutdown(self, handler):
        self.handler = handler
        return RawHandle()


class RawHandle:
    def unsubscribe(self) -> None:
        return None

    def cancel(self) -> None:
        return None


class RawSender:
    def __init__(self) -> None:
        self.messages: list[str] = []

    def sendMessage(self, message: str) -> None:
        self.messages.append(message)

    def hasPermission(self, permission: str) -> bool:
        return True


class RawCommands:
    def __init__(self) -> None:
        self.registrations: list[dict[str, object]] = []

    def register(self, spec):
        self.registrations.append(spec)
        return RawHandle()


class RawEvents:
    def __init__(self) -> None:
        self.handlers: dict[str, object] = {}

    def on(self, event_name, handler):
        self.handlers[event_name] = handler
        return RawHandle()


class RawNoop:
    def run(self, handler):
        self.handler = handler
        return RawHandle()

    def delay(self, delay_ticks, handler):
        self.delay_ticks = delay_ticks
        self.delay_handler = handler
        return RawHandle()

    def repeat(self, interval_ticks, handler):
        self.interval_ticks = interval_ticks
        self.repeat_handler = handler
        return RawHandle()

    def timer(self, delay_ticks, interval_ticks, handler):
        self.delay_ticks = delay_ticks
        self.interval_ticks = interval_ticks
        self.timer_handler = handler
        return RawHandle()


class RawStore:
    def get(self, path):
        return None

    def set(self, path, value):
        self.last_set = (path, value)

    def delete(self, path):
        self.deleted = path

    def save(self):
        return None

    def reload(self):
        return None

    def keys(self):
        return []


class RawFiles:
    path = "/tmp"

    def resolve(self, *segments):
        return "/tmp/" + "/".join(segments)

    def readText(self, relative_path):
        return relative_path

    def writeText(self, relative_path, contents):
        self.last_write = (relative_path, contents)

    def exists(self, relative_path):
        return False

    def mkdirs(self, relative_path=""):
        self.last_mkdir = relative_path


class RawStorage:
    def __init__(self) -> None:
        self.plugin = RawStore()
        self.files = RawFiles()


class RawHttpResponse:
    def __init__(self, url: str, status: int, headers: dict[str, str], body: str) -> None:
        self.url = url
        self.status = status
        self.ok = 200 <= status <= 299
        self.headers = headers
        self.body = body


class RawHttp:
    def fetch(self, spec):
        self.last_request = spec
        return RawHttpResponse(
            url=spec["url"],
            status=201,
            headers={"content-type": "application/json"},
            body='{"ok": true}',
        )


class RawUnsafeEvents:
    def onJava(self, event_class_name, handler):
        self.last_handler = (event_class_name, handler)
        return RawHandle()


class RawUnsafeJava:
    def type(self, class_name):
        return class_name


class RawUnsafeBackend:
    server = object()
    plugin = object()
    console = object()

    def dispatchCommand(self, command):
        self.last_command = command
        return True


class RawUnsafe:
    def __init__(self) -> None:
        self.events = RawUnsafeEvents()
        self.java = RawUnsafeJava()
        self.backend = RawUnsafeBackend()


class RawContext:
    def __init__(self) -> None:
        self.app = RawApp()
        self.commands = RawCommands()
        self.events = RawEvents()
        self.tasks = RawNoop()
        self.players = RawNoop()
        self.worlds = RawNoop()
        self.entities = RawNoop()
        self.items = RawNoop()
        self.inventory = RawNoop()
        self.chat = RawNoop()
        self.effects = RawNoop()
        self.recipes = RawNoop()
        self.bossBars = RawNoop()
        self.scoreboards = RawNoop()
        self.storage = RawStorage()
        self.http = RawHttp()
        self.config = RawStore()
        self.unsafe = RawUnsafe()


class RawCommandContext:
    def __init__(self, sender: RawSender) -> None:
        self.sender = sender
        self.args = []
        self.label = "hello"
        self.command = "hello"


class PluginTests(unittest.TestCase):
    def test_plugin_wraps_context_and_registers_pythonic_command(self) -> None:
        raw_context = RawContext()
        plugin = Plugin("Hello Python", version="0.1.0")

        @plugin.startup
        def on_enable(context: PluginContext) -> None:
            context.app.log.info("enabled")
            context.commands.register(
                "hello",
                lambda command: command.sender.send_message("Hello from Python."),
                description="Send a greeting.",
            )

        plugin.on_enable(raw_context)

        self.assertEqual(["enabled"], raw_context.app.log.messages)
        registration = raw_context.commands.registrations[0]
        self.assertEqual("hello", registration["name"])
        self.assertEqual("Send a greeting.", registration["description"])

        sender = RawSender()
        registration["execute"](RawCommandContext(sender))
        self.assertEqual(["Hello from Python."], sender.messages)

    def test_http_service_wraps_requests_pythonically(self) -> None:
        raw_context = RawContext()
        context = PluginContext(raw_context)

        response = context.http.post(
            "https://example.test/items",
            headers={"content-type": "application/json"},
            body='{"name":"lapis"}',
        )

        self.assertEqual(
            {
                "url": "https://example.test/items",
                "method": "POST",
                "headers": {"content-type": "application/json"},
                "body": '{"name":"lapis"}',
            },
            raw_context.http.last_request,
        )
        self.assertEqual(201, response.status)
        self.assertTrue(response.ok)
        self.assertEqual("application/json", response.headers["content-type"])
        self.assertEqual({"ok": True}, response.json())


if __name__ == "__main__":
    unittest.main()
