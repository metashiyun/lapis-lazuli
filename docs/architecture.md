# Architecture

Lapis Lazuli has four main layers:

- `runtimes/jvm/core`: bundle loading, manifest parsing, lifecycle management, and JS/Python execution
- `runtimes/jvm/bukkit`: the Bukkit-family backend implementation of the Lapis SDK
- `sdks/typescript`: the public TypeScript SDK
- `tooling/cli`: authoring and bundling tools

## Component Responsibilities

### `runtimes/jvm/core`

`runtimes/jvm/core` is responsible for:

- discovering bundles
- parsing `lapis-plugin.json`
- selecting the language runtime by `manifest.engine`
- evaluating JS and Python bundles
- passing the Lapis runtime context into lifecycle hooks
- closing loaded bundles on shutdown or hot reload

### `runtimes/jvm/bukkit`

`runtimes/jvm/bukkit` implements the SDK against Bukkit-family servers. Its responsibilities are:

- exposing the service-oriented runtime context
- translating Bukkit events into Lapis event names
- mapping handles for players, worlds, entities, items, inventories, chat, and storage
- managing the bundle directory and hot reload lifecycle

### `lapis-lazuli`

`lapis-lazuli` is the public SDK package name on both npm and PyPI. For Python, the
import path remains `lapis_lazuli`.

The SDK defines:

- `definePlugin(...)`
- lifecycle types
- service module interfaces
- handle types
- event payload types

### `create-lapis-lazuli`

`create-lapis-lazuli` is the published npm CLI package. It drives local authoring:

- `create`
- `validate`
- `build`
- `bundle`

## Runtime Flow

1. A plugin author writes TS, JS, or Python against the Lapis SDK model.
2. The CLI validates and bundles the project.
3. The runtime scans `plugins/LapisLazuli/bundles/`.
4. `runtimes/jvm/core` loads each bundle.
5. The language runtime evaluates the entrypoint.
6. The bundle receives the Lapis service context.
7. Commands, event hooks, and tasks remain active until unload.
8. On shutdown or hot reload, the runtime runs shutdown hooks and closes host registrations.

## Boundary

There are two API layers:

- the documented Lapis SDK
- the explicit `unsafe` backend escape hatch

Only the first is the intended normal authoring surface.
