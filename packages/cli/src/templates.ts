type TemplateEngine = "js" | "python";

export function renderPackageJson(name: string): string {
  return JSON.stringify(
    {
      name,
      private: true,
      type: "module",
      scripts: {
        build: "lapis build .",
        bundle: "lapis bundle .",
      },
      dependencies: {
        "@lapis-lazuli/sdk": "^0.1.0",
      },
    },
    null,
    2,
  );
}

export function renderManifest(id: string, displayName: string, engine: TemplateEngine = "js"): string {
  return JSON.stringify(
    {
      id,
      name: displayName,
      version: "0.1.0",
      engine,
      main: engine === "js" ? "./src/index.ts" : "./src/main.py",
      apiVersion: "1.0",
    },
    null,
    2,
  );
}

export function renderSource(displayName: string, engine: TemplateEngine = "js"): string {
  if (engine === "python") {
    return `name = "${displayName}"
version = "0.1.0"


def on_enable(context):
    context.app.log.info("${displayName} enabled.")

    def execute(command):
        command.sender.sendMessage("Hello from ${displayName}.")

    context.commands.register({
        "name": "hello",
        "description": "Send a hello message from ${displayName}.",
        "execute": execute,
    })


def on_disable(context):
    context.app.log.info("${displayName} disabled.")
`;
  }

  return `import { definePlugin } from "@lapis-lazuli/sdk";

export default definePlugin({
  name: "${displayName}",
  version: "0.1.0",
  onEnable(context) {
    context.app.log.info("${displayName} enabled.");

    context.commands.register({
      name: "hello",
      description: "Send a hello message from ${displayName}.",
      execute({ sender }) {
        sender.sendMessage("Hello from ${displayName}.");
      },
    });
  },
  onDisable(context) {
    context.app.log.info("${displayName} disabled.");
  },
});
`;
}

export const GITIGNORE = `dist
.lapis
node_modules
`;
