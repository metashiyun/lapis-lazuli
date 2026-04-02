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

export function renderManifest(id: string, displayName: string): string {
  return JSON.stringify(
    {
      id,
      name: displayName,
      version: "0.1.0",
      engine: "js",
      main: "./src/index.ts",
      apiVersion: "1.0",
    },
    null,
    2,
  );
}

export function renderSource(displayName: string): string {
  return `import { definePlugin } from "@lapis-lazuli/sdk";

export default definePlugin({
  name: "${displayName}",
  version: "0.1.0",
  onEnable(context) {
    context.logger.info("${displayName} enabled.");

    context.commands.register({
      name: "hello",
      description: "Send a hello message from ${displayName}.",
      execute({ sender }) {
        sender.sendMessage("Hello from ${displayName}.");
      },
    });
  },
});
`;
}

export const GITIGNORE = `dist
.lapis
node_modules
`;

