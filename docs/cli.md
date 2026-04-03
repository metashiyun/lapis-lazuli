# CLI Reference

The CLI lives in `packages/cli` and is currently invoked from source:

```sh
bun packages/cli/src/index.ts <command> ...
```

## Commands

### `create`

```sh
bun packages/cli/src/index.ts create <directory> [display-name] [engine]
```

Supported engines:

- `js`
- `python`

The generated TypeScript starter depends on `@lapis-lazuli/sdk` and uses the current
service-oriented API.

### `validate`

```sh
bun packages/cli/src/index.ts validate <directory>
```

Validation checks:

- `lapis-plugin.json` exists
- required manifest fields are non-empty strings
- the manifest entrypoint exists
- `engine` is one of the supported runtime engines

### `build`

```sh
bun packages/cli/src/index.ts build <directory>
```

Behavior:

- validates the manifest
- bundles JS/TS entrypoints with Bun into `.lapis/build`
- stages Python projects into `.lapis/build`

### `bundle`

```sh
bun packages/cli/src/index.ts bundle <directory> [output-directory]
```

Behavior:

- runs the build flow
- writes a deployable bundle directory
- rewrites manifest `main` to the bundled output path

For repo-local development, JS builds rewrite `@lapis-lazuli/sdk` imports to the local
workspace package so publishing is not required during development.
