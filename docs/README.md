# Documentation

This directory is the technical documentation set for Lapis Lazuli.

## Start Here

- [../README.md](../README.md): repository overview and current status
- [architecture.md](architecture.md): component model and runtime flow
- [compatibility.md](compatibility.md): support matrix and current platform guarantees

## Reference

- [api/runtime-host-api.md](api/runtime-host-api.md): runtime host bridge available to JS bundles
- [api/typescript-sdk.md](api/typescript-sdk.md): `@lapis-lazuli/sdk` API definition
- [cli.md](cli.md): CLI command reference
- [bundle-format.md](bundle-format.md): bundle directory and manifest schema
- [python-sdk.md](python-sdk.md): Python SDK implementation status

## Guides

- [authoring.md](authoring.md): create, build, bundle, install, and update a plugin
- [testing.md](testing.md): validation strategy and release gates

## Recommended Reading Order

1. Read [compatibility.md](compatibility.md) to understand the current support envelope.
2. Read [api/runtime-host-api.md](api/runtime-host-api.md) and [api/typescript-sdk.md](api/typescript-sdk.md) before designing plugin APIs.
3. Use [authoring.md](authoring.md) and [cli.md](cli.md) while building plugins.
4. Use [testing.md](testing.md) before release work.
