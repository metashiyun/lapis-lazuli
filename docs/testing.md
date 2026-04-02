# Testing Plan

Lapis Lazuli should be validated at three levels, with each higher level proving assumptions made by the lower one.

## 1. Fast Unit Tests

- `bun test`
- Focus:
  - SDK authoring API behavior
  - CLI manifest validation and bundle generation

## 2. Runtime Integration Tests

- `./gradlew :runtime-core:test`
- Focus:
  - manifest parsing and bundle loading
  - bundle lifecycle isolation
  - GraalJS host bridge behavior
  - bundling the real example plugin and loading it through `JsLanguageRuntime`

These tests verify the runtime without a live Minecraft server and should run on every change.

## 3. Real Paper Server Smoke Test

- `PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke`
- Focus:
  - shaded runtime plugin boots on a real Paper server
  - bundled script plugin is discovered under `plugins/LapisLazuli/bundles`
  - `onEnable` runs
  - `serverLoad` event handlers run on real server startup
  - commands registered from TypeScript are executable from the Paper console
  - server shuts down cleanly with the runtime installed

## Recommended Gate Usage

- Every commit:
  - `bun test`
  - `./gradlew :runtime-core:test :runtime-bukkit:compileKotlin`
- Before merging runtime/plugin loader changes:
  - `PAPER_SERVER_JAR=... bun run test:paper-smoke`
- Before release:
  - `bun test`
  - `./gradlew clean :runtime-core:test :runtime-bukkit:shadowJar`
  - `PAPER_SERVER_JAR=... bun run test:paper-smoke`

## CI Direction

The real Paper smoke test should become a CI job once server jar provisioning is decided. The simplest path is to provide a pre-downloaded Paper jar through CI cache or an artifact step, then run `scripts/paper-smoke.sh` headlessly.

