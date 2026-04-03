from lapis_lazuli import define_plugin


def on_enable(context):
    context.app.log.info("Hello Python enabled.")
    context.storage.plugin.set("greeting", "Hello from Python.")
    context.storage.files.write_text("greeting.txt", "Hello from Python.\n")

    def execute(command):
        sender = command.sender
        sender.send_message(["Hello from ", {"text": "Python"}, "."])
        if sender.player:
            sender.player.action_bar("Lapis says hi")
        return True

    context.commands.register(
        name="hello",
        execute=execute,
        description="Send a greeting and highlight the caller.",
    )

    def on_player_join(event):
        event.set_join_message(["Welcome ", {"text": event.player.name}, " to the server."])
        context.effects.play_sound(
            "entity.player.levelup",
            player=event.player,
            volume=0.7,
            pitch=1.1,
        )
        context.app.log.info(f"Player joined: {event.player.name}")

    context.events.on("player.join", on_player_join)

    def on_server_ready(_event):
        online_count = len(context.players.online())
        context.chat.broadcast(f"Hello Python is ready for {online_count} online player(s).")

    context.events.on("server.ready", on_server_ready)


def on_disable(context):
    context.app.log.info("Hello Python disabled.")


plugin = define_plugin(
    name="Hello Python",
    version="0.1.0",
    on_enable=on_enable,
    on_disable=on_disable,
)
