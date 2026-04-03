import type { Dirent } from "node:fs";
import { copyFile, lstat, mkdir, mkdtemp, readFile, readdir, rm, stat, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { basename, dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { renderManifest, renderPackageJson, renderSource, GITIGNORE } from "./templates";

export type LapisEngine = "js" | "python";

export interface LapisManifest {
  id: string;
  name: string;
  version: string;
  engine: string;
  main: string;
  apiVersion: string;
}

export interface ValidationResult {
  manifest: LapisManifest;
  entrypoint: string;
}

const LOCAL_SDK_DIR = fileURLToPath(new URL("../../sdk", import.meta.url));
const SUPPORTED_ENGINES = new Set<LapisEngine>(["js", "python"]);
const PYTHON_BUNDLE_IGNORED_DIRECTORIES = new Set([
  ".git",
  ".lapis",
  "__pycache__",
  ".mypy_cache",
  ".pytest_cache",
  ".ruff_cache",
  ".venv",
  "dist",
  "node_modules",
  "venv",
]);
const PYTHON_BUNDLE_IGNORED_FILES = new Set([
  ".DS_Store",
]);

function assertString(value: unknown, field: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error(`Manifest field "${field}" must be a non-empty string.`);
  }

  return value;
}

export async function readManifest(projectDir: string): Promise<LapisManifest> {
  const manifestPath = join(resolve(projectDir), "lapis-plugin.json");
  const manifestText = await readFile(manifestPath, "utf8");
  const raw = JSON.parse(manifestText) as Record<string, unknown>;

  return {
    id: assertString(raw.id, "id"),
    name: assertString(raw.name, "name"),
    version: assertString(raw.version, "version"),
    engine: assertString(raw.engine, "engine"),
    main: assertString(raw.main, "main"),
    apiVersion: assertString(raw.apiVersion, "apiVersion"),
  };
}

export async function validateManifest(projectDir: string): Promise<ValidationResult> {
  const manifest = await readManifest(projectDir);
  const entrypoint = resolve(projectDir, manifest.main);
  const entrypointStats = await stat(entrypoint).catch(() => null);

  if (!entrypointStats?.isFile()) {
    throw new Error(`Entrypoint "${manifest.main}" does not exist.`);
  }

  assertSupportedEngine(manifest.engine);

  return {
    manifest,
    entrypoint,
  };
}

export async function buildProject(projectDir: string): Promise<{ buildDir: string; manifest: LapisManifest }> {
  const { manifest, entrypoint } = await validateManifest(projectDir);
  const buildDir = resolve(projectDir, ".lapis/build");

  await rm(buildDir, { recursive: true, force: true });
  await mkdir(buildDir, { recursive: true });

  if (manifest.engine === "js") {
    const cleanupLocalSdkLink = await ensureLocalSdkLink(projectDir);
    const preparedEntrypoint = await prepareEntrypointForBuild(entrypoint);

    try {
      const result = await Bun.build({
        entrypoints: [preparedEntrypoint.entrypoint],
        root: resolve(projectDir),
        outdir: buildDir,
        target: "node",
        format: "cjs",
        sourcemap: "external",
        minify: false,
        splitting: false,
      });

      if (!result.success) {
        const logs = result.logs.map((log: { message: string }) => log.message).join("\n");
        throw new Error(`Build failed.\n${logs}`);
      }
    } finally {
      await preparedEntrypoint.cleanup?.();
      await cleanupLocalSdkLink?.();
    }
  } else {
    await stagePythonProject(projectDir, buildDir);
  }

  return {
    buildDir,
    manifest,
  };
}

export async function bundleProject(
  projectDir: string,
  outputDir?: string,
): Promise<{ bundleDir: string; manifestPath: string; mainPath: string }> {
  const { buildDir, manifest } = await buildProject(projectDir);
  const bundleDir = resolve(outputDir ?? join(projectDir, "dist", manifest.id));
  const bundledMain = manifest.engine === "js" ? "main.js" : normalizeBundlePath(manifest.main);
  const mainPath = join(bundleDir, bundledMain);
  const manifestPath = join(bundleDir, "lapis-plugin.json");

  await rm(bundleDir, { recursive: true, force: true });
  await mkdir(bundleDir, { recursive: true });

  if (manifest.engine === "js") {
    const builtMain = await findBuiltEntrypoint(buildDir);

    if (!builtMain) {
      throw new Error("Bundled output did not contain a JavaScript entrypoint.");
    }

    const builtContents = await readFile(builtMain, "utf8");
    await mkdir(dirname(mainPath), { recursive: true });
    await writeFile(mainPath, builtContents, "utf8");
  } else {
    await copyDirectoryContents(buildDir, bundleDir);
  }

  await writeFile(
    manifestPath,
    JSON.stringify(
      {
        ...manifest,
        main: bundledMain,
      },
      null,
      2,
    ),
    "utf8",
  );

  return {
    bundleDir,
    manifestPath,
    mainPath,
  };
}

export async function createProject(
  destination: string,
  displayName?: string,
  engine: LapisEngine = "js",
): Promise<{ projectDir: string }> {
  const projectDir = resolve(destination);
  const id = basename(projectDir);
  const supportedEngine = assertSupportedEngine(engine);
  const pluginName = displayName ?? id
    .split(/[-_]/g)
    .filter(Boolean)
    .map((part: string) => part[0]!.toUpperCase() + part.slice(1))
    .join(" ");

  await mkdir(join(projectDir, "src"), { recursive: true });
  if (supportedEngine === "js") {
    await writeFile(join(projectDir, "package.json"), renderPackageJson(id), "utf8");
  }
  await writeFile(join(projectDir, "lapis-plugin.json"), renderManifest(id, pluginName, supportedEngine), "utf8");
  await writeFile(
    join(projectDir, "src", supportedEngine === "js" ? "index.ts" : "main.py"),
    renderSource(pluginName, supportedEngine),
    "utf8",
  );
  await writeFile(join(projectDir, ".gitignore"), GITIGNORE, "utf8");

  return { projectDir };
}

export async function createTempProject(prefix = "lapis-cli-test-"): Promise<string> {
  return mkdtemp(join(tmpdir(), prefix));
}

export async function cleanupTempProject(projectDir: string): Promise<void> {
  await rm(resolve(projectDir), { recursive: true, force: true });
}

export async function ensureDirectory(path: string): Promise<void> {
  await mkdir(resolve(path), { recursive: true });
}

export async function writeTextFile(path: string, contents: string): Promise<void> {
  await ensureDirectory(dirname(path));
  await writeFile(path, contents, "utf8");
}

async function ensureLocalSdkLink(projectDir: string): Promise<(() => Promise<void>) | null> {
  const linkPath = join(resolve(projectDir), "node_modules", "@lapis-lazuli", "sdk");
  const existing = await lstat(linkPath).catch(() => null);

  if (existing) {
    return null;
  }

  const localSdkExists = await stat(LOCAL_SDK_DIR).catch(() => null);
  if (!localSdkExists?.isDirectory()) {
    return null;
  }

  await mkdir(dirname(linkPath), { recursive: true });
  await symlink(LOCAL_SDK_DIR, linkPath, "dir");

  return async () => {
    await rm(linkPath, { recursive: true, force: true });
  };
}

async function prepareEntrypointForBuild(
  entrypoint: string,
): Promise<{ entrypoint: string; cleanup?: () => Promise<void> }> {
  const source = await readFile(entrypoint, "utf8");
  const localSdkEntry = join(LOCAL_SDK_DIR, "src", "index.ts");
  const localSdkExists = await stat(localSdkEntry).catch(() => null);

  if (!localSdkExists?.isFile() || !source.includes("@lapis-lazuli/sdk")) {
    return { entrypoint };
  }

  const rewrittenImportPath = normalizeImportPath(relative(dirname(entrypoint), localSdkEntry));
  const rewrittenSource = source.replace(
    /(["'])@lapis-lazuli\/sdk\1/g,
    (_match: string, quote: string) => `${quote}${rewrittenImportPath}${quote}`,
  );
  const rewrittenEntrypoint = join(dirname(entrypoint), `.lapis-${basename(entrypoint)}`);

  await writeFile(rewrittenEntrypoint, rewrittenSource, "utf8");

  return {
    entrypoint: rewrittenEntrypoint,
    cleanup: async () => {
      await rm(rewrittenEntrypoint, { force: true });
    },
  };
}

function normalizeImportPath(importPath: string): string {
  const normalized = importPath.replaceAll("\\", "/");
  return normalized.startsWith(".") ? normalized : `./${normalized}`;
}

async function findBuiltEntrypoint(buildDir: string): Promise<string | null> {
  const entries = await readdir(buildDir, { withFileTypes: true });

  for (const entry of entries) {
    const absolutePath = join(buildDir, entry.name);

    if (entry.isDirectory()) {
      const nested = await findBuiltEntrypoint(absolutePath);
      if (nested) {
        return nested;
      }
      continue;
    }

    if (entry.isFile() && entry.name.endsWith(".js") && !entry.name.endsWith(".js.map")) {
      return absolutePath;
    }
  }

  return null;
}

function assertSupportedEngine(engine: string): LapisEngine {
  if (!SUPPORTED_ENGINES.has(engine as LapisEngine)) {
    throw new Error(
      `Unsupported engine "${engine}". Supported engines: ${Array.from(SUPPORTED_ENGINES).map((value) => `"${value}"`).join(", ")}.`,
    );
  }

  return engine as LapisEngine;
}

function normalizeBundlePath(path: string): string {
  return path.replaceAll("\\", "/").replace(/^\.\//, "");
}

async function stagePythonProject(projectDir: string, buildDir: string): Promise<void> {
  await copyDirectoryContents(resolve(projectDir), buildDir, shouldIncludePythonBundlePath);
}

function shouldIncludePythonBundlePath(relativePath: string, entry: Dirent): boolean {
  void relativePath;

  if (entry.isDirectory()) {
    return !PYTHON_BUNDLE_IGNORED_DIRECTORIES.has(entry.name);
  }

  return !PYTHON_BUNDLE_IGNORED_FILES.has(entry.name);
}

async function copyDirectoryContents(
  sourceDir: string,
  targetDir: string,
  shouldInclude: (relativePath: string, entry: Dirent) => boolean = () => true,
): Promise<void> {
  async function visit(currentSourceDir: string): Promise<void> {
    const entries = await readdir(currentSourceDir, { withFileTypes: true });

    for (const entry of entries) {
      const sourcePath = join(currentSourceDir, entry.name);
      const relativePath = normalizeBundlePath(relative(sourceDir, sourcePath));

      if (!shouldInclude(relativePath, entry)) {
        continue;
      }

      const targetPath = join(targetDir, relativePath);

      if (entry.isDirectory()) {
        await mkdir(targetPath, { recursive: true });
        await visit(sourcePath);
        continue;
      }

      if (entry.isFile()) {
        await mkdir(dirname(targetPath), { recursive: true });
        await copyFile(sourcePath, targetPath);
      }
    }
  }

  await visit(sourceDir);
}
