# CLI Reference

The CLI lives in `tooling/cli` and is currently invoked from source:

```sh
bun tooling/cli/src/index.ts <command> ...
```

## Commands

### `create`

```sh
bun tooling/cli/src/index.ts create <directory> [display-name] [engine]
```

Supported engines:

- `js`
- `python`

The generated TypeScript starter depends on `@lapis-lazuli/sdk` and uses the current
service-oriented API.

The generated Python starter includes `pyproject.toml` metadata and imports the
`lapis_lazuli` package.

### `validate`

```sh
bun tooling/cli/src/index.ts validate <directory>
```

Validation checks:

- `lapis-plugin.json` exists
- required manifest fields are non-empty strings
- the manifest entrypoint exists
- `engine` is one of the supported runtime engines

### `build`

```sh
bun tooling/cli/src/index.ts build <directory>
```

Behavior:

- validates the manifest
- bundles JS/TS entrypoints with Bun into `.lapis/build`
- stages Python projects into `.lapis/build`
- vendors the workspace `lapis_lazuli` package into Python builds for repo-local use

### `bundle`

```sh
bun tooling/cli/src/index.ts bundle <directory> [output-directory]
```

Behavior:

- runs the build flow
- writes a deployable bundle directory
- rewrites manifest `main` to the bundled output path

For repo-local development, JS builds rewrite `@lapis-lazuli/sdk` imports to the local
workspace package so publishing is not required during development.

Python builds follow the same principle by copying the workspace `lapis_lazuli` package
into the staged bundle when the project does not already provide one.
