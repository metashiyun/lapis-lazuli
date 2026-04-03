# Testing Strategy

Lapis Lazuli should be validated in layers. Each layer proves assumptions used by the
next one.

## 1. Workspace Tests

```sh
bun test
```

Primary focus:

- SDK type and behavior expectations
- CLI manifest validation
- CLI build and bundle generation

## 2. JVM Runtime Tests

```sh
./gradlew :runtime-core:test
```

Primary focus:

- manifest parsing
- bundle directory loading
- runtime lifecycle behavior
- GraalJS bridge behavior
- bundling the real example plugin and loading it through `JsLanguageRuntime`

These tests verify the runtime without a live Minecraft server.

## 3. Adapter Compile Check

```sh
./gradlew :runtime-bukkit:compileKotlin
```

Primary focus:

- the Bukkit / Paper adapter still compiles against the declared Paper API dependency

## 4. Real Paper Smoke Test

```sh
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
```

Primary focus:

- the shaded runtime plugin boots on a real Paper server
- the example bundle is discovered under `plugins/LapisLazuli/bundles`
- `onEnable` executes
- `serverLoad` handlers execute
- TypeScript-defined commands are callable from the console
- hot reload works after replacing bundle code
- the server shuts down cleanly

## Recommended Gates

### Every change

```sh
bun test
./gradlew :runtime-core:test :runtime-bukkit:compileKotlin
```

### Runtime or adapter changes

```sh
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
```

### Before release

```sh
bun test
./gradlew clean :runtime-core:test :runtime-bukkit:shadowJar
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
```

## Current Coverage Limits

The real-server smoke test covers Paper only.

That means current test evidence supports this product statement:

- Paper is the validated server target
- Bukkit and Spigot are not currently verified release targets

## CI Direction

The Paper smoke test should become a CI job once Paper jar provisioning is decided. The
simplest path is to cache or pre-provision the server jar and run
`scripts/paper-smoke.sh` headlessly.
