# Python SDK Status

## Current Status

Lapis Lazuli now supports Python plugin authoring and a Python runtime engine.

What it still does not ship is a dedicated Python package equivalent to
`@lapis-lazuli/sdk`.

## Evidence In The Current Codebase

Today the repository contains:

- `packages/sdk`: the TypeScript SDK
- `packages/cli`: the Bun-based CLI
- `runtime-core`: runtime registration for both `JsLanguageRuntime` and `PythonLanguageRuntime`
- `runtime-bukkit`: the Minecraft adapter
- `examples/hello-python`: a reference Python plugin

There is still no Python package under `packages/`, but the CLI now accepts:

```json
{
  "engine": "js | python"
}
```

## Practical Meaning

Current supported authoring modes are:

- TypeScript, compiled to JavaScript
- JavaScript directly
- Python

Current limitation:

- Python support exists as a runtime and CLI packaging mode, not as a separate typed SDK package

## What A Fuller Python SDK Would Require

A more complete Python SDK story would still need at least:

1. a Python authoring package
2. richer Python-first type stubs and authoring docs
3. bundle and packaging rules for Python entrypoints
4. host bridge parity with the JS runtime
5. tests for authoring, loading, lifecycle, and server integration

Until that exists, the documentation should describe Python support as implemented but
still early-stage.
