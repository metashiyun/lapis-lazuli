# Python SDK

PyPI package:

```sh
python -m pip install lapis-lazuli
```

Import path:

```py
lapis_lazuli
```

The Python SDK is the public Python authoring surface for Lapis Lazuli. It wraps the
shared runtime capability layer with Python-first conventions instead of mirroring the
TypeScript API verbatim.

The distribution name is `lapis-lazuli`. The import path remains `lapis_lazuli`.

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

## HTTP

The Python SDK exposes HTTP through `context.http`.

```py
response = context.http.post(
    "https://example.com/api",
    headers={"content-type": "application/json"},
    body='{"hello":"lapis"}',
)

context.app.log.info(f"status={response.status}")
context.app.log.info(response.text)
```

Available helpers:

- `context.http.fetch(...)`
- `context.http.get(...)`
- `context.http.post(...)`
- `context.http.put(...)`
- `context.http.delete(...)`

## Packaging And Bundling

The CLI scaffolds Python projects with `pyproject.toml` metadata and stages
`lapis_lazuli` into deployable bundles when needed.

That keeps deployed Python bundles self-contained while authoring still starts from the
published PyPI package.
