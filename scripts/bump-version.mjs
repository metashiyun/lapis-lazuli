import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const [, , nextVersion] = process.argv;

if (!nextVersion) {
  console.error("Usage: node scripts/bump-version.mjs <version>");
  console.error('Example: node scripts/bump-version.mjs 0.1.0');
  process.exit(1);
}

if (!/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(nextVersion)) {
  console.error(`Invalid version "${nextVersion}". Expected semver-like value such as 0.1.0 or 0.1.0-SNAPSHOT.`);
  process.exit(1);
}

const root = process.cwd();

const fileUpdates = [
  {
    path: "build.gradle.kts",
    update(text) {
      return replaceOne(text, /^\s*version\s*=\s*"[^"]+"/m, `version = "${nextVersion}"`);
    },
  },
  {
    path: "sdks/typescript/package.json",
    update(text) {
      return updatePackageJsonVersion(text, nextVersion);
    },
  },
  {
    path: "tooling/cli/package.json",
    update(text) {
      return updatePackageJsonVersion(text, nextVersion);
    },
  },
  {
    path: "sdks/python/pyproject.toml",
    update(text) {
      return replaceOne(text, /^\s*version\s*=\s*"[^"]+"/m, `version = "${nextVersion}"`);
    },
  },
  {
    path: "sdks/python/src/lapis_lazuli/__init__.py",
    update(text) {
      return replaceOne(text, /^__version__\s*=\s*"[^"]+"/m, `__version__ = "${nextVersion}"`);
    },
  },
];

for (const fileUpdate of fileUpdates) {
  const absolutePath = resolve(root, fileUpdate.path);
  const current = readFileSync(absolutePath, "utf8");
  const updated = fileUpdate.update(current);

  if (updated !== current) {
    writeFileSync(absolutePath, updated, "utf8");
  }

  console.log(`${fileUpdate.path} -> ${nextVersion}`);
}

function replaceOne(text, pattern, replacement) {
  if (!pattern.test(text)) {
    throw new Error(`Could not find version field matching ${pattern}`);
  }

  return text.replace(pattern, replacement);
}

function updatePackageJsonVersion(text, version) {
  const json = JSON.parse(text);
  json.version = version;

  return `${JSON.stringify(json, null, 2)}\n`;
}
