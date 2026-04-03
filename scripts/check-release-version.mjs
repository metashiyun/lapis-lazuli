import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const [, , rawTag] = process.argv;

if (!rawTag) {
  console.error("Usage: node scripts/check-release-version.mjs <tag>");
  process.exit(1);
}

const match = /^v(\d+\.\d+\.\d+)$/.exec(rawTag);

if (!match) {
  console.error(`Release tag must match vX.Y.Z. Received: ${rawTag}`);
  process.exit(1);
}

const expectedVersion = match[1];
const repoRoot = process.cwd();

const checks = [
  {
    file: "build.gradle.kts",
    readVersion() {
      return readVersionFromRegex("build.gradle.kts", /^\s*version\s*=\s*"([^"]+)"/m);
    },
  },
  {
    file: "sdks/typescript/package.json",
    readVersion() {
      return JSON.parse(readText("sdks/typescript/package.json")).version;
    },
  },
  {
    file: "sdks/python/pyproject.toml",
    readVersion() {
      return readVersionFromRegex("sdks/python/pyproject.toml", /^\s*version\s*=\s*"([^"]+)"/m);
    },
  },
  {
    file: "sdks/python/src/lapis_lazuli/__init__.py",
    readVersion() {
      return readVersionFromRegex(
        "sdks/python/src/lapis_lazuli/__init__.py",
        /^__version__\s*=\s*"([^"]+)"/m,
      );
    },
  },
  {
    file: "tooling/cli/package.json",
    readVersion() {
      return JSON.parse(readText("tooling/cli/package.json")).version;
    },
  },
];

const mismatches = checks
  .map((check) => ({ file: check.file, version: check.readVersion() }))
  .filter((check) => check.version !== expectedVersion);

if (mismatches.length > 0) {
  console.error(`Release tag ${rawTag} does not match all releasable versions.`);

  for (const mismatch of mismatches) {
    console.error(`- ${mismatch.file}: ${mismatch.version}`);
  }

  process.exit(1);
}

console.log(`Verified release version ${expectedVersion} across ${checks.length} files.`);

function readText(relativePath) {
  return readFileSync(resolve(repoRoot, relativePath), "utf8");
}

function readVersionFromRegex(relativePath, pattern) {
  const text = readText(relativePath);
  const result = pattern.exec(text);

  if (!result) {
    throw new Error(`Could not read version from ${relativePath}`);
  }

  return result[1];
}
