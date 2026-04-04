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
      expect(await Bun.file(join(pythonDir, "pyproject.toml")).exists()).toBe(true);
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
          'from lapis_lazuli import Plugin',
          "from helpers import greet",
          "",
          'plugin = Plugin("Hello Python", version="0.1.0")',
          "",
          "@plugin.startup",
          "def on_enable(context):",
          '    context.app.log.info("Hello Python enabled.")',
          "",
          "    def execute(command):",
          "        command.sender.send_message(greet(command.sender.name))",
          "",
          '    context.commands.register("hello", execute, description="Send a greeting.")',
          "",
        ].join("\n"),
      );

      const validation = await validateManifest(pythonDir);
      const result = await bundleProject(pythonDir, join(projectDir, "python-output"));

      expect(validation.manifest.engine).toBe("python");
      expect(result.bundleDir.endsWith(join("python-output"))).toBe(true);
      expect(await Bun.file(result.mainPath).exists()).toBe(true);
      expect(await Bun.file(join(result.bundleDir, "src", "helpers.py")).exists()).toBe(true);
      expect(await Bun.file(join(result.bundleDir, "lapis_lazuli", "__init__.py")).exists()).toBe(true);
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

  it("creates and bundles a Node project", async () => {
    const projectDir = await createTempProject();
    const nodeDir = join(projectDir, "hello-node");

    try {
      await createProject(nodeDir, "Hello Node", "node");

      const validation = await validateManifest(nodeDir);
      const result = await bundleProject(nodeDir, join(projectDir, "node-output"));

      expect(validation.manifest.engine).toBe("node");
      expect(await Bun.file(result.mainPath).exists()).toBe(true);
      expect(await Bun.file(result.manifestPath).json()).toEqual(
        expect.objectContaining({
          id: "hello-node",
          engine: "node",
          main: "main.js",
        }),
      );
    } finally {
      await cleanupTempProject(projectDir);
    }
  });
});
