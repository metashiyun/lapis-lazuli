# Architecture

Lapis Lazuli has four main layers:

- `runtime-core`: engine-agnostic bundle loading, manifest parsing, runtime dispatch, and JS execution
- `runtime-bukkit`: the Minecraft server adapter that binds the runtime to Paper-style services
- `packages/sdk`: the TypeScript contract used by plugin authors
- `packages/cli`: local tooling for creating, validating, building, and bundling plugins

## Component Responsibilities

### `runtime-core`

`runtime-core` is responsible for:

- discovering bundle directories
- parsing `lapis-plugin.json`
- validating that the entrypoint stays inside the bundle directory
- selecting the language runtime by `manifest.engine`
- loading the plugin and invoking lifecycle hooks
- closing loaded bundles during shutdown or hot reload

The only runtime engine currently registered is `JsLanguageRuntime`.

## `runtime-bukkit`

`runtime-bukkit` provides the host implementation for Minecraft servers. It is
responsible for:

- exposing logger, commands, events, scheduler, config, data directory, and Java interop
- creating the `plugins/LapisLazuli/bundles/` directory
- loading all bundles on startup
- polling the bundle directory for hot reload changes
- unloading and reloading all script bundles when the bundle snapshot changes

The tested target is Paper 1.21.x.

## `@lapis-lazuli/sdk`

The SDK is a thin TypeScript contract over the runtime host bridge. It does not
implement the server behavior itself. Its job is to provide:

- `definePlugin(...)`
- lifecycle and context interfaces
- typed command and event payloads

## `@lapis-lazuli/cli`

The CLI drives the authoring workflow:

- `create` scaffolds a plugin project
- `validate` checks the manifest and entrypoint
- `build` compiles the entrypoint with Bun
- `bundle` writes the deployable `dist/<id>/` output

## Runtime Flow

1. A plugin author writes TypeScript or JavaScript against `@lapis-lazuli/sdk`.
2. The CLI compiles the plugin into a CommonJS `main.js`.
3. The CLI writes a deployable bundle directory containing `lapis-plugin.json` and `main.js`.
4. The runtime plugin scans `plugins/LapisLazuli/bundles/`.
5. `runtime-core` loads the manifest and entrypoint for each bundle.
6. `JsLanguageRuntime` evaluates the JavaScript bundle in GraalJS.
7. The exported plugin object receives the host `context`.
8. Registered commands, events, and scheduled tasks remain active until unload.
9. On shutdown or hot reload, the runtime closes the loaded plugin and host registrations.

## JavaScript Execution Model

The JS runtime:

- loads bundles with GraalJS
- wraps the bundle as CommonJS-style `module.exports`
- exposes the host context as a plain JS object
- enables host access and class lookup for Java interop

This means the runtime is not a sandbox. A script with access to `context.javaInterop`
can load arbitrary JVM classes visible to the plugin classloader.

## Design Boundary

There are two distinct API layers in the system:

- the stable host bridge documented by Lapis Lazuli
- the unrestricted Java interop escape hatch into Bukkit / Spigot / Paper / JVM APIs

Only the first layer is a documented SDK contract. The second layer is powerful, but
it is not yet wrapped, typed, or compatibility-tested as a product surface.
