# Bundle Format

A deployable Lapis Lazuli bundle is a directory discovered by the runtime under:

```text
plugins/LapisLazuli/bundles/<bundle-id>/
```

## Required Files

Every bundle must contain:

- `lapis-plugin.json`
- the runtime entrypoint referenced by `lapis-plugin.json`

The default CLI output is:

- `lapis-plugin.json`
- `main.js`

## Manifest Schema

Example:

```json
{
  "id": "hello-ts",
  "name": "Hello TS",
  "version": "0.1.0",
  "engine": "js",
  "main": "main.js",
  "apiVersion": "1.0"
}
```

## Fields

| Field | Required | Meaning |
| --- | --- | --- |
| `id` | Yes | Stable bundle identifier |
| `name` | Yes | Human-readable plugin name |
| `version` | Yes | Bundle version string |
| `engine` | Yes | Runtime engine key. Supported values are `"js"`, `"node"`, and `"python"` |
| `main` | Yes | Relative path to the bundle entrypoint |
| `apiVersion` | Yes | Lapis Lazuli API version marker |
| `dependencies` | No | Parsed by the manifest model, but not currently enforced |
| `softDependencies` | No | Parsed by the manifest model, but not currently enforced |

## Loader Rules

The loader currently enforces:

- the manifest file must exist
- the entrypoint file must exist
- the resolved entrypoint path must stay inside the bundle directory

Unknown JSON fields are ignored by manifest parsing.

## Runtime-Owned Paths

The Bukkit adapter also uses these bundle-local paths:

- `config.yml`
- `data/`

These are created and managed by the runtime host services.

Hot reload ignores `config.yml` and `data/` changes so that plugin persistence does not
trigger reload loops.

## Packaging Contract

The CLI bundles source projects into the runtime format by:

1. compiling `js` and `node` entrypoints with Bun, or staging Python source files
2. copying the built JS output to `main.js` for `js` and `node` bundles
3. rewriting the manifest `main` field to the runtime entrypoint path

That means authors can keep `main` pointed at `./src/index.ts` in source control while
still shipping a runtime-ready bundle.
