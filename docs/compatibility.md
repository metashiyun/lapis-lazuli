# Compatibility And Support Matrix

## Short Answer

Lapis Lazuli now aims to provide a Lapis-native SDK implemented on Bukkit-family
servers.

It does not claim full Bukkit, Spigot, or Paper API parity. Instead, it provides:

- a TypeScript-first public SDK
- JavaScript and Python runtime support
- a Bukkit/Paper backend implementation of the SDK
- an explicit `unsafe` escape hatch for raw backend access

## Support Matrix

| Area | Status | Notes |
| --- | --- | --- |
| TypeScript authoring | Supported | Primary API design target |
| JavaScript authoring | Supported | Same runtime context as TS |
| Python authoring | Supported | Public `lapis_lazuli` SDK with Pythonic wrappers over the shared runtime |
| `lapis-lazuli` | Active redesign | Service-oriented Lapis API |
| Bukkit-common SDK core | In progress | Runtime is shaped around capabilities common to Bukkit-family servers |
| Paper target | Supported | Compile target and smoke-tested path |
| Bukkit / Spigot validation | Unverified | Runtime contract is moving toward Bukkit-common, but release validation is still Paper-first |
| Raw Java/backend access | Supported | Exposed only through `context.unsafe` |

## What The SDK Covers Today

The public SDK now centers on:

- `app`
- `commands`
- `events`
- `tasks`
- `players`
- `worlds`
- `entities`
- `items`
- `inventory`
- `chat`
- `effects`
- `recipes`
- `bossBars`
- `scoreboards`
- `storage`
- `config`
- `unsafe`

This is a Lapis API, not a Java facade.

## What `unsafe` Means

`unsafe` keeps low-level access available when needed:

- Java class loading
- raw backend handles
- generic Java event subscription
- console command dispatch

That path is intentionally explicit so normal plugin code stays inside the Lapis SDK.
