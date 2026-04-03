# Lapis Lazuli

Lapis Lazuli is a Minecraft plugin SDK for TypeScript, JavaScript, and Python.
You write plugins against the Lapis SDK, bundle them, and run them through the
Lapis runtime plugin on Bukkit-family servers.

## Quick Start

Create a plugin project:

```sh
npx create-lapis-lazuli /absolute/path/to/my-plugin
npx create-lapis-lazuli /absolute/path/to/my-python-plugin "My Python Plugin" python
```

The generated TypeScript starter installs `lapis-lazuli` from npm and imports it
as `lapis-lazuli`. The generated Python starter installs `lapis-lazuli` from PyPI
and imports it as `lapis_lazuli`.

If you are adding Lapis to an existing plugin project instead of scaffolding a new
one, install the SDK from the public registry for your language:

```sh
npm install lapis-lazuli
python -m pip install lapis-lazuli
```

Validate and bundle it:

```sh
npx create-lapis-lazuli validate /absolute/path/to/my-plugin
npx create-lapis-lazuli bundle /absolute/path/to/my-plugin
```

Install these into your server:

```text
https://github.com/metashiyun/lapis-lazuli/releases/latest/download/lapis-runtime-bukkit.jar -> <server>/plugins/lapis-runtime-bukkit.jar
/absolute/path/to/my-plugin/dist/<plugin-id>/ -> <server>/plugins/LapisLazuli/bundles/<plugin-id>/
```

Build the runtime from source only when you are developing this repository itself:

```sh
bun install
./gradlew :runtimes:jvm:bukkit:shadowJar
```

## Development Checks

```sh
bun test
./gradlew :runtimes:jvm:core:test :runtimes:jvm:bukkit:compileKotlin
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
```

## Docs

- Docs site: [https://lapis.shiyun.org/docs](https://lapis.shiyun.org/docs)
- Agent docs: [https://lapis.shiyun.org/agents/index.md](https://lapis.shiyun.org/agents/index.md)
