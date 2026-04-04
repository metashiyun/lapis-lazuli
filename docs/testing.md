# Testing Strategy

Lapis Lazuli is validated in layers.

## 1. Workspace Tests

```sh
bun test
```

Primary focus:

- SDK type surface
- CLI scaffolding
- build and bundle generation

## 2. JVM Runtime Tests

```sh
./gradlew :runtimes:jvm:core:test
```

Primary focus:

- bundle loading
- lifecycle handling
- JS, Node, and Python runtime context behavior
- example bundle integration

Node-specific runtime tests require a working `node` command in the environment.

## 3. Backend Compile Check

```sh
./gradlew :runtimes:jvm:bukkit:compileKotlin
```

Primary focus:

- the Bukkit/Paper backend still compiles against the declared API dependency

## 4. Real Paper Smoke Test

```sh
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
```

Primary focus:

- runtime plugin startup
- bundle discovery
- `server.ready` handling
- command execution
- hot reload
- clean shutdown

## Recommended Gates

Every change:

```sh
bun test
./gradlew :runtimes:jvm:core:test :runtimes:jvm:bukkit:compileKotlin
```

Runtime or backend changes:

```sh
PAPER_SERVER_JAR=/absolute/path/to/paper.jar bun run test:paper-smoke
```

## Coverage Limits

Paper is still the validated real-server target. Bukkit-family broad compatibility is an
active design goal, not a fully validated release claim yet.
