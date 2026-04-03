import { lstat, mkdir, mkdtemp, readFile, readdir, rm, stat, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { basename, dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { renderManifest, renderPackageJson, renderSource, GITIGNORE } from "./templates";

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

  if (manifest.engine !== "js") {
    throw new Error(`Unsupported engine "${manifest.engine}". Only "js" is available in v1.`);
  }

  return {
    manifest,
    entrypoint,
  };
}

export async function buildProject(projectDir: string): Promise<{ buildDir: string; manifest: LapisManifest }> {
  const { manifest, entrypoint } = await validateManifest(projectDir);
  const buildDir = resolve(projectDir, ".lapis/build");
  const cleanupLocalSdkLink = await ensureLocalSdkLink(projectDir);
  const preparedEntrypoint = await prepareEntrypointForBuild(entrypoint);

  await rm(buildDir, { recursive: true, force: true });
  await mkdir(buildDir, { recursive: true });

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
      const logs = result.logs.map((log) => log.message).join("\n");
      throw new Error(`Build failed.\n${logs}`);
    }
  } finally {
    await preparedEntrypoint.cleanup?.();
    await cleanupLocalSdkLink?.();
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
  const mainPath = join(bundleDir, "main.js");
  const manifestPath = join(bundleDir, "lapis-plugin.json");
  const builtMain = await findBuiltEntrypoint(buildDir);

  if (!builtMain) {
    throw new Error("Bundled output did not contain a JavaScript entrypoint.");
  }

  await rm(bundleDir, { recursive: true, force: true });
  await mkdir(bundleDir, { recursive: true });

  const builtContents = await readFile(builtMain, "utf8");
  await writeFile(mainPath, builtContents, "utf8");
  await writeFile(
    manifestPath,
    JSON.stringify(
      {
        ...manifest,
        main: "main.js",
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
): Promise<{ projectDir: string }> {
  const projectDir = resolve(destination);
  const id = basename(projectDir);
  const pluginName = displayName ?? id
    .split(/[-_]/g)
    .filter(Boolean)
    .map((part) => part[0]!.toUpperCase() + part.slice(1))
    .join(" ");

  await mkdir(join(projectDir, "src"), { recursive: true });
  await writeFile(join(projectDir, "package.json"), renderPackageJson(id), "utf8");
  await writeFile(join(projectDir, "lapis-plugin.json"), renderManifest(id, pluginName), "utf8");
  await writeFile(join(projectDir, "src", "index.ts"), renderSource(pluginName), "utf8");
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
    (match, quote: string) => `${quote}${rewrittenImportPath}${quote}`,
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
