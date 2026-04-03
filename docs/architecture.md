# Architecture

Lapis Lazuli has four main layers:

- `runtime-core`: bundle loading, manifest parsing, lifecycle management, and JS/Python execution
- `runtime-bukkit`: the Bukkit-family backend implementation of the Lapis SDK
- `packages/sdk`: the public TypeScript SDK
- `packages/cli`: authoring and bundling tools

## Component Responsibilities

### `runtime-core`

`runtime-core` is responsible for:

- discovering bundles
- parsing `lapis-plugin.json`
- selecting the language runtime by `manifest.engine`
- evaluating JS and Python bundles
- passing the Lapis runtime context into lifecycle hooks
- closing loaded bundles on shutdown or hot reload

### `runtime-bukkit`

`runtime-bukkit` implements the SDK against Bukkit-family servers. Its responsibilities are:

- exposing the service-oriented runtime context
- translating Bukkit events into Lapis event names
- mapping handles for players, worlds, entities, items, inventories, chat, and storage
- managing the bundle directory and hot reload lifecycle

### `@lapis-lazuli/sdk`

The SDK is the public authoring contract. It defines:

- `definePlugin(...)`
- lifecycle types
- service module interfaces
- handle types
- event payload types

### `@lapis-lazuli/cli`

The CLI drives local authoring:

- `create`
- `validate`
- `build`
- `bundle`

## Runtime Flow

1. A plugin author writes TS, JS, or Python against the Lapis SDK model.
2. The CLI validates and bundles the project.
3. The runtime scans `plugins/LapisLazuli/bundles/`.
4. `runtime-core` loads each bundle.
5. The language runtime evaluates the entrypoint.
6. The bundle receives the Lapis service context.
7. Commands, event hooks, and tasks remain active until unload.
8. On shutdown or hot reload, the runtime runs shutdown hooks and closes host registrations.

## Boundary

There are two API layers:

- the documented Lapis SDK
- the explicit `unsafe` backend escape hatch

Only the first is the intended normal authoring surface.
