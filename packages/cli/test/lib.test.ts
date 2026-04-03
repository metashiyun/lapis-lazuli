import { describe, expect, it } from "bun:test";
import { join } from "node:path";
import {
  bundleProject,
  cleanupTempProject,
  createProject,
  createTempProject,
  validateManifest,
  writeTextFile,
} from "../src/lib";

describe("CLI library", () => {
  it("creates and validates a project", async () => {
    const projectDir = await createTempProject();

    try {
      await createProject(join(projectDir, "hello-ts"), "Hello TS");
      const result = await validateManifest(join(projectDir, "hello-ts"));

      expect(result.manifest.id).toBe("hello-ts");
      expect(result.manifest.name).toBe("Hello TS");
    } finally {
      await cleanupTempProject(projectDir);
    }
  });

  it("bundles a project into a deployable folder", async () => {
    const projectDir = await createTempProject();
    const exampleDir = join(import.meta.dir, "../../../examples/hello-ts");

    try {
      const result = await bundleProject(exampleDir, join(projectDir, "output"));

      expect(result.bundleDir.endsWith(join("output"))).toBe(true);
      expect(await Bun.file(result.mainPath).exists()).toBe(true);
      expect(await Bun.file(result.manifestPath).json()).toEqual(
        expect.objectContaining({
          id: "hello-ts",
          main: "main.js",
        }),
      );
    } finally {
      await cleanupTempProject(projectDir);
    }
  });

  it("creates and bundles a Python project", async () => {
    const projectDir = await createTempProject();
    const pythonDir = join(projectDir, "hello-python");

    try {
      await createProject(pythonDir, "Hello Python", "python");
      await writeTextFile(
        join(pythonDir, "src", "helpers.py"),
        [
          "def greet(name):",
          '    return f"Hello {name}"',
          "",
        ].join("\n"),
      );
      await writeTextFile(
        join(pythonDir, "src", "main.py"),
        [
          'from helpers import greet',
          "",
          'name = "Hello Python"',
          "",
          "def on_enable(context):",
          '    context.app.log.info("Hello Python enabled.")',
          "",
          "    def execute(command):",
          "        command.sender.sendMessage(greet(command.sender.name))",
          "",
          '    context.commands.register({',
          '        "name": "hello",',
          '        "description": "Send a greeting.",',
          '        "execute": execute,',
          "    })",
          "",
        ].join("\n"),
      );

      const validation = await validateManifest(pythonDir);
      const result = await bundleProject(pythonDir, join(projectDir, "python-output"));

      expect(validation.manifest.engine).toBe("python");
      expect(result.bundleDir.endsWith(join("python-output"))).toBe(true);
      expect(await Bun.file(result.mainPath).exists()).toBe(true);
      expect(await Bun.file(join(result.bundleDir, "src", "helpers.py")).exists()).toBe(true);
      expect(await Bun.file(result.manifestPath).json()).toEqual(
        expect.objectContaining({
          id: "hello-python",
          engine: "python",
          main: "src/main.py",
        }),
      );
    } finally {
      await cleanupTempProject(projectDir);
    }
  });
});
