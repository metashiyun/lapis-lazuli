# CLI Reference

The published CLI package is `create-lapis-lazuli`.

Published usage:

```sh
npx create-lapis-lazuli <directory>
```

## Commands

### `create`

```sh
npx create-lapis-lazuli <directory> [display-name] [engine]
```

Supported engines:

- `js`
- `python`

The generated TypeScript starter depends on `lapis-lazuli` and uses the current
service-oriented API. It imports from the published package name
`lapis-lazuli`, not a local file path.

The generated Python starter includes `pyproject.toml` metadata, installs the PyPI
package `lapis-lazuli`, and imports `lapis_lazuli`.

### `validate`

```sh
npx create-lapis-lazuli validate <directory>
```

Validation checks:

- `lapis-plugin.json` exists
- required manifest fields are non-empty strings
- the manifest entrypoint exists
- `engine` is one of the supported runtime engines

### `build`

```sh
npx create-lapis-lazuli build <directory>
```

Behavior:

- validates the manifest
- bundles JS/TS entrypoints with Bun into `.lapis/build`
- stages Python projects into `.lapis/build`
- vendors the `lapis_lazuli` package into Python builds when needed

### `bundle`

```sh
npx create-lapis-lazuli bundle <directory> [output-directory]
```

Behavior:

- runs the build flow
- writes a deployable bundle directory
- rewrites manifest `main` to the bundled output path

JS and Python bundle output is designed to stay self-contained for deployment.
