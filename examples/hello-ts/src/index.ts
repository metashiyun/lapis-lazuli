import { definePlugin } from "lapis-lazuli";

export default definePlugin({
  name: "Hello TS",
  version: "0.1.0",
  onEnable(context) {
    context.app.log.info("Hello TS enabled.");
    context.storage.plugin.set("greeting", "Hello from TypeScript.");
    context.storage.files.writeText("greeting.txt", "Hello from TypeScript.\n");

    context.commands.register({
      name: "hello",
      description: "Send a greeting and highlight the caller.",
      execute({ sender }) {
        sender.sendMessage(["Hello from ", { text: "TypeScript" }, "."]);
        sender.player?.actionBar("Lapis says hi");
        return true;
      },
    });

    context.events.on("player.join", (event) => {
      event.setJoinMessage(["Welcome ", { text: event.player.name }, " to the server."]);
      context.effects.playSound({
        player: event.player,
        sound: "entity.player.levelup",
        volume: 0.7,
        pitch: 1.1,
      });
      context.app.log.info(`Player joined: ${event.player.name}`);
    });

    context.events.on("server.ready", () => {
      const onlineCount = context.players.online().length;
      context.chat.broadcast(`Hello TS is ready for ${onlineCount} online player(s).`);
    });
  },
  onDisable(context) {
    context.app.log.info("Hello TS disabled.");
  },
});
