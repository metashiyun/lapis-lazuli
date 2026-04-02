import { definePlugin } from "@lapis-lazuli/sdk";

export default definePlugin({
  name: "Hello TS",
  version: "0.1.0",
  onEnable(context) {
    context.logger.info("Hello TS enabled.");

    context.commands.register({
      name: "hello",
      description: "Send a greeting.",
      execute({ sender }) {
        sender.sendMessage("Hello from TypeScript.");
      },
    });

    context.events.on("playerJoin", ({ playerName }) => {
      context.logger.info(`Player joined: ${playerName}`);
    });
  },
  onDisable(context) {
    context.logger.info("Hello TS disabled.");
  },
});
