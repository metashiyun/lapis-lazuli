from __future__ import annotations

from collections.abc import Callable
from typing import Any

from ._runtime import PluginContext


class Plugin:
    def __init__(self, name: str, version: str | None = None) -> None:
        self.name = name
        self.version = version
        self._startup_handler: Callable[[PluginContext], Any] | None = None
        self._shutdown_handler: Callable[[PluginContext], Any] | None = None

    def startup(self, handler: Callable[[PluginContext], Any]) -> Callable[[PluginContext], Any]:
        self._startup_handler = handler
        return handler

    def shutdown(self, handler: Callable[[PluginContext], Any]) -> Callable[[PluginContext], Any]:
        self._shutdown_handler = handler
        return handler

    def on_enable(self, raw_context: Any) -> Any:
        if self._startup_handler is None:
            return None
        return self._startup_handler(PluginContext(raw_context))

    def on_disable(self, raw_context: Any) -> Any:
        if self._shutdown_handler is None:
            return None
        return self._shutdown_handler(PluginContext(raw_context))


def define_plugin(
    *,
    name: str,
    version: str | None = None,
    on_enable: Callable[[PluginContext], Any] | None = None,
    on_disable: Callable[[PluginContext], Any] | None = None,
) -> Plugin:
    plugin = Plugin(name=name, version=version)

    if on_enable is not None:
        plugin.startup(on_enable)

    if on_disable is not None:
        plugin.shutdown(on_disable)

    return plugin
