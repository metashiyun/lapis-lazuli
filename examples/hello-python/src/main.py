name = "Hello Python"
version = "0.1.0"


def on_enable(context):
    context.logger.info("Hello Python enabled.")

    def execute(command):
        command.sender.sendMessage("Hello from Python.")

    context.commands.register("hello", execute, "Send a greeting.")

    def on_player_join(event):
        context.logger.info(f"Player joined: {event.playerName}")

    context.events.on("playerJoin", on_player_join)
    context.events.on("serverLoad", lambda event: context.logger.info("Server load event observed."))


def on_disable(context):
    context.logger.info("Hello Python disabled.")
