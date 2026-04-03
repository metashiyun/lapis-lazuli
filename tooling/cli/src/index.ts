#!/usr/bin/env node

import { basename } from "node:path";

import { buildProject, bundleProject, createProject, validateManifest } from "./lib.js";

function usage(): never {
  throw new Error(
    [
      "Usage:",
      "  create-lapis-lazuli <directory> [display-name] [engine]",
      "  lapis create <directory> [display-name] [engine]",
      "  lapis validate <directory>",
      "  lapis bundle <directory> [output-directory]",
      "  lapis build <directory>",
      "",
      "Supported engines: js, python",
    ].join("\n"),
  );
}

async function main(): Promise<void> {
  const args = process.argv.slice(2);
  const executable = basename(process.argv[1] ?? "");
  const knownCommands = new Set(["create", "validate", "build", "bundle"]);

  let [command, firstArg, secondArg, thirdArg] = args;

  if (executable === "create-lapis-lazuli" && (!command || !knownCommands.has(command))) {
    thirdArg = secondArg;
    secondArg = firstArg;
    firstArg = command;
    command = "create";
  }

  if (!command) {
    usage();
  }

  switch (command) {
    case "create": {
      if (!firstArg) {
        usage();
      }

      const result = await createProject(firstArg, secondArg, thirdArg as "js" | "python" | undefined);
      console.log(`Created ${result.projectDir}`);
      return;
    }
    case "validate":
    {
      if (!firstArg) {
        usage();
      }

      const result = await validateManifest(firstArg);
      console.log(`Validated ${result.manifest.id} -> ${result.entrypoint}`);
      return;
    }
    case "build": {
      if (!firstArg) {
        usage();
      }

      const result = await buildProject(firstArg);
      console.log(`Built ${result.manifest.id} -> ${result.buildDir}`);
      return;
    }
    case "bundle": {
      if (!firstArg) {
        usage();
      }

      const result = await bundleProject(firstArg, secondArg);
      console.log(`Bundled ${result.bundleDir}`);
      return;
    }
    default:
      usage();
  }
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
});
