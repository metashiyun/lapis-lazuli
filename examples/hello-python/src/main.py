from lapis_lazuli import Plugin

plugin = Plugin("Hello Python", version="0.1.0")


@plugin.startup
def on_enable(context):
    context.app.log.info("Hello Python enabled.")
    context.storage.plugin.set("greeting", "Hello from storage.")

    def execute(command):
        command.sender.send_message("Hello from Python.")

    context.commands.register(
        "hello",
        execute,
        description="Send a greeting.",
    )

    def on_player_join(event):
        context.app.log.info(f"Player joined: {event.player.name}")

    context.events.on("player.join", on_player_join)
    context.events.on(
        "server.ready",
        lambda event: context.app.log.info("Server ready event observed."),
    )


@plugin.shutdown
def on_disable(context):
    context.app.log.info("Hello Python disabled.")
