# Python SDK Status

## Current Status

Lapis Lazuli does not currently ship a Python SDK.

There is also no Python runtime engine in this repository.

## Evidence In The Current Codebase

Today the repository contains:

- `packages/sdk`: the TypeScript SDK
- `packages/cli`: the Bun-based CLI
- `runtime-core`: a runtime registry that loads `JsLanguageRuntime`
- `runtime-bukkit`: the Minecraft adapter

There is no Python package under `packages/`, and the CLI only accepts:

```json
{
  "engine": "js"
}
```

## Practical Meaning

Current supported authoring modes are:

- TypeScript, compiled to JavaScript
- JavaScript directly

Unsupported authoring mode:

- Python

## What A Real Python SDK Would Require

A serious Python story would need at least:

1. a Python authoring package
2. a new runtime engine registration in `runtime-core`
3. bundle and packaging rules for Python entrypoints
4. host bridge parity with the JS runtime
5. tests for authoring, loading, lifecycle, and server integration

Until that exists, the documentation should describe Python support as not implemented.
