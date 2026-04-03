import { definePlugin } from "@lapis-lazuli/sdk";

export default definePlugin({
  name: "Hello TS",
  version: "0.1.0",
  onEnable(context) {
    context.app.log.info("Hello TS enabled.");
    context.storage.plugin.set("greeting", "Hello from storage.");

    context.commands.register({
      name: "hello",
      description: "Send a greeting.",
      execute({ sender }) {
        sender.sendMessage("Hello from TypeScript.");
      },
    });

    context.events.on("player.join", ({ player }) => {
      context.app.log.info(`Player joined: ${player.name}`);
    });

    context.events.on("server.ready", () => {
      context.app.log.info("Server ready event observed.");
    });
  },
  onDisable(context) {
    context.app.log.info("Hello TS disabled.");
  },
});
