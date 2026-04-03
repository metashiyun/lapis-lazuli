# Python SDK

Package name:

```py
lapis-lazuli-sdk
```

Import path:

```py
lapis_lazuli
```

The Python SDK is the public Python authoring surface for Lapis Lazuli. It wraps the
shared runtime capability layer with Python-first conventions instead of mirroring the
TypeScript API verbatim.

## Minimal Example

```py
from lapis_lazuli import Plugin

plugin = Plugin("Example Plugin", version="0.1.0")


@plugin.startup
def on_enable(context):
    context.app.log.info("Enabled.")

    def execute(command):
        command.sender.send_message("Hello from Lapis.")

    context.commands.register(
        "hello",
        execute,
        description="Send a greeting.",
    )
```

## Main Design Rules

- Prefer `snake_case` names such as `send_message`, `on_shutdown`, and `dispatch_command`
- Wrap runtime handles and event payloads so common plugin work feels natural in Python
- Keep DTOs plain and lightweight; `Location` and `TitleOptions` are dataclasses
- Keep raw backend access under `context.unsafe`

## Packaging And Bundling

The CLI can scaffold Python projects with `pyproject.toml` metadata and bundles the
workspace `lapis_lazuli` package into Python bundles for repo-local development.

That keeps Python bundles self-contained without requiring a published package during
local iteration.
