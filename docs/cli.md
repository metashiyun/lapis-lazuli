# CLI Reference

The CLI lives in `packages/cli` and is currently invoked from source:

```sh
bun packages/cli/src/index.ts <command> ...
```

## Commands

### `create`

```sh
bun packages/cli/src/index.ts create <directory> [display-name]
```

Creates a new plugin project with:

- `package.json`
- `lapis-plugin.json`
- `src/index.ts`
- `.gitignore`

The generated project depends on `@lapis-lazuli/sdk`.

### `validate`

```sh
bun packages/cli/src/index.ts validate <directory>
```

Validation rules:

- `lapis-plugin.json` must exist
- `id`, `name`, `version`, `engine`, `main`, and `apiVersion` must be non-empty strings
- the manifest entrypoint must exist
- `engine` must be `"js"`

### `build`

```sh
bun packages/cli/src/index.ts build <directory>
```

Behavior:

- validates the manifest
- builds the entrypoint with Bun
- writes the build output to `.lapis/build`
- targets CommonJS output for the runtime

### `bundle`

```sh
bun packages/cli/src/index.ts bundle <directory> [output-directory]
```

Behavior:

- runs the build flow
- writes a deployable bundle directory
- emits:
  - `lapis-plugin.json`
  - `main.js`
- rewrites manifest `main` to `main.js`

Default output path:

```text
<project>/dist/<manifest.id>/
```

## Build Notes

The CLI currently assumes:

- Bun is available
- the runtime engine is JavaScript only
- plugins are bundled into a single deployable entrypoint

For repo-local development, the build flow also rewrites `@lapis-lazuli/sdk` imports to
the workspace source when needed so local builds work without publishing the SDK first.
