name = "Hello Python"
version = "0.1.0"


def on_enable(context):
    context.app.log.info("Hello Python enabled.")

    def execute(command):
        command.sender.sendMessage("Hello from Python.")

    context.commands.register({
        "name": "hello",
        "description": "Send a greeting.",
        "execute": execute,
    })

    def on_player_join(event):
        context.app.log.info(f"Player joined: {event.player.name}")

    context.events.on("player.join", on_player_join)
    context.events.on("server.ready", lambda event: context.app.log.info("Server ready event observed."))


def on_disable(context):
    context.app.log.info("Hello Python disabled.")
