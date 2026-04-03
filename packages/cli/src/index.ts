#!/usr/bin/env bun

import { buildProject, bundleProject, createProject, validateManifest } from "./lib";

function usage(): never {
  throw new Error(
    [
      "Usage:",
      "  lapis create <directory> [display-name]",
      "  lapis validate <directory>",
      "  lapis bundle <directory> [output-directory]",
      "  lapis build <directory>",
    ].join("\n"),
  );
}

async function main(): Promise<void> {
  const [command, firstArg, secondArg] = process.argv.slice(2);

  if (!command) {
    usage();
  }

  switch (command) {
    case "create": {
      if (!firstArg) {
        usage();
      }

      const result = await createProject(firstArg, secondArg);
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
