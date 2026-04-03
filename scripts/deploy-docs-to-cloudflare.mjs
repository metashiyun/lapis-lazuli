import { createHash } from "node:crypto";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { dirname, extname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, "..");
const docsDir = resolve(repoRoot, "apps/docs");
const wranglerConfigPath = resolve(docsDir, "wrangler.jsonc");
const workerEntryName = "docs-assets-worker.mjs";
const reservedAssetConfigFiles = new Set(["_headers", "_redirects"]);
const dryRun = process.argv.includes("--dry-run");

try {
  const config = loadConfig();
  const assets = collectAssets(config.assetsDirectory);
  const uploadMetadata = buildUploadMetadata(config, assets.assetConfig);

  if (dryRun) {
    const totalBytes = assets.files.reduce((sum, file) => sum + file.size, 0);
    console.log(`Dry run: prepared ${assets.files.length} assets (${totalBytes} bytes) for ${config.workerName}.`);
    console.log(`Assets directory: ${config.assetsDirectory}`);
    console.log(`Compatibility date: ${config.compatibilityDate}`);
    console.log(`Asset binding: ${config.assetBinding}`);
    process.exit(0);
  }

  console.log(`Preparing ${assets.files.length} docs assets for ${config.workerName}...`);
  const uploadSession = await createAssetUploadSession(config, assets.manifest);

  let completionJwt = uploadSession.jwt;
  if (uploadSession.buckets.length > 0) {
    completionJwt = await uploadAssets(config, uploadSession, assets.byHash);
  } else {
    console.log("No asset uploads required. Reusing the existing asset payload.");
  }

  console.log(`Deploying ${config.workerName} via the Workers API...`);
  await deployWorker(config, uploadMetadata, completionJwt);
  console.log(`Cloudflare docs deploy completed for ${config.workerName}.`);
} catch (error) {
  console.error(formatError(error));
  process.exit(1);
}

function loadConfig() {
  const accountId = process.env.CLOUDFLARE_ACCOUNT_ID;
  const apiToken = process.env.CLOUDFLARE_API_TOKEN;

  if (!accountId) {
    throw new Error("Missing CLOUDFLARE_ACCOUNT_ID.");
  }

  if (!apiToken) {
    throw new Error("Missing CLOUDFLARE_API_TOKEN.");
  }

  const wranglerConfig = parseJsonc(readFileSync(wranglerConfigPath, "utf8"));

  if (wranglerConfig.main) {
    throw new Error("apps/docs/wrangler.jsonc defines a custom main Worker entry. deploy-docs-to-cloudflare.mjs only supports the current assets-only docs Worker.");
  }

  if (!wranglerConfig.name) {
    throw new Error("apps/docs/wrangler.jsonc is missing the Worker name.");
  }

  if (!wranglerConfig.compatibility_date) {
    throw new Error("apps/docs/wrangler.jsonc is missing compatibility_date.");
  }

  if (!wranglerConfig.assets?.directory) {
    throw new Error("apps/docs/wrangler.jsonc is missing assets.directory.");
  }

  const assetsDirectory = resolve(docsDir, wranglerConfig.assets.directory);
  if (!existsSync(assetsDirectory)) {
    throw new Error(`Assets directory does not exist: ${assetsDirectory}. Run the Astro build first.`);
  }

  return {
    accountId,
    apiToken,
    assetsDirectory,
    workerName: wranglerConfig.name,
    compatibilityDate: wranglerConfig.compatibility_date,
    compatibilityFlags: wranglerConfig.compatibility_flags,
    observability: wranglerConfig.observability,
    placement: wranglerConfig.placement,
    assetBinding: wranglerConfig.assets.binding ?? "ASSETS",
    assetSettings: {
      html_handling: wranglerConfig.assets.html_handling,
      not_found_handling: wranglerConfig.assets.not_found_handling,
    },
  };
}

function collectAssets(assetsDirectory) {
  const files = [];
  const assetConfig = {};

  walkDirectory(assetsDirectory, "");

  if (files.length === 0) {
    throw new Error(`No assets were found in ${assetsDirectory}.`);
  }

  const manifest = Object.fromEntries(
    files.map((file) => [
      file.manifestPath,
      {
        hash: file.hash,
        size: file.size,
      },
    ]),
  );

  const byHash = new Map(files.map((file) => [file.hash, file]));

  return {
    files,
    manifest,
    byHash,
    assetConfig,
  };

  function walkDirectory(currentDirectory, basePath) {
    const entries = readdirSync(currentDirectory, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name));

    for (const entry of entries) {
      const absolutePath = resolve(currentDirectory, entry.name);
      const relativePath = basePath ? `${basePath}/${entry.name}` : entry.name;

      if (entry.isDirectory()) {
        walkDirectory(absolutePath, relativePath);
        continue;
      }

      if (!entry.isFile()) {
        continue;
      }

      if (!basePath && reservedAssetConfigFiles.has(entry.name)) {
        assetConfig[entry.name] = readFileSync(absolutePath, "utf8");
        continue;
      }

      const buffer = readFileSync(absolutePath);
      const contentBase64 = buffer.toString("base64");
      const extension = extname(relativePath).slice(1);
      const hash = createHash("sha256").update(contentBase64 + extension).digest("hex").slice(0, 32);
      const manifestPath = `/${relativePath}`;

      files.push({
        absolutePath,
        contentBase64,
        contentType: guessContentType(relativePath),
        hash,
        manifestPath,
        size: buffer.length,
      });
    }
  }
}

function buildUploadMetadata(config, assetConfig) {
  const uploadMetadata = {
    main_module: workerEntryName,
    compatibility_date: config.compatibilityDate,
    bindings: [
      {
        name: config.assetBinding,
        type: "assets",
      },
    ],
  };

  if (config.compatibilityFlags?.length) {
    uploadMetadata.compatibility_flags = config.compatibilityFlags;
  }

  if (config.observability) {
    uploadMetadata.observability = config.observability;
  }

  if (config.placement) {
    uploadMetadata.placement = config.placement;
  }

  const mergedAssetConfig = {
    ...assetConfig,
    ...pickDefined(config.assetSettings),
  };

  uploadMetadata.assets = Object.keys(mergedAssetConfig).length > 0 ? { config: mergedAssetConfig } : {};
  return uploadMetadata;
}

async function createAssetUploadSession(config, manifest) {
  console.log("Starting the Cloudflare asset upload session...");
  const result = await cloudflareJson(
    `/accounts/${config.accountId}/workers/scripts/${config.workerName}/assets-upload-session`,
    {
      method: "POST",
      headers: {
        "content-type": "application/json",
      },
      body: JSON.stringify({ manifest }),
    },
    config.apiToken,
  );

  if (!Array.isArray(result.buckets) || typeof result.jwt !== "string") {
    throw new Error("Cloudflare did not return a valid assets upload session.");
  }

  return result;
}

async function uploadAssets(config, uploadSession, assetsByHash) {
  console.log(`Uploading ${uploadSession.buckets.length} asset bucket(s)...`);
  let completionJwt = null;

  for (const [index, bucket] of uploadSession.buckets.entries()) {
    const form = new FormData();

    for (const hash of bucket) {
      const asset = assetsByHash.get(hash);
      if (!asset) {
        throw new Error(`Cloudflare requested asset hash ${hash}, but it was not found in the manifest.`);
      }

      const file = new File([asset.contentBase64], asset.hash, {
        type: asset.contentType,
      });

      form.append(asset.hash, file, asset.hash);
    }

    console.log(`Uploading bucket ${index + 1}/${uploadSession.buckets.length}...`);
    const result = await cloudflareJson(
      `/accounts/${config.accountId}/workers/assets/upload?base64=true`,
      {
        method: "POST",
        body: form,
      },
      uploadSession.jwt,
    );

    if (typeof result.jwt === "string") {
      completionJwt = result.jwt;
    }
  }

  if (typeof completionJwt !== "string") {
    throw new Error("Cloudflare asset upload completed without returning a completion JWT.");
  }

  return completionJwt;
}

async function deployWorker(config, uploadMetadata, completionJwt) {
  const workerScript = new File([buildWorkerScript(config.assetBinding)], workerEntryName, {
    type: "application/javascript+module",
  });

  const metadata = {
    ...uploadMetadata,
    assets: {
      ...uploadMetadata.assets,
      jwt: completionJwt,
    },
  };

  const form = new FormData();
  form.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));
  form.append(workerEntryName, workerScript, workerEntryName);

  await cloudflareJson(
    `/accounts/${config.accountId}/workers/scripts/${config.workerName}`,
    {
      method: "PUT",
      body: form,
    },
    config.apiToken,
  );
}

async function cloudflareJson(pathname, init, token) {
  const response = await fetch(`https://api.cloudflare.com/client/v4${pathname}`, {
    ...init,
    headers: {
      ...(init.headers ?? {}),
      Authorization: `Bearer ${token}`,
    },
  });

  const payload = await readJson(response);
  if (!response.ok || payload?.success === false) {
    throw new Error(formatCloudflareFailure(response.status, payload));
  }

  return payload?.result ?? payload;
}

async function readJson(response) {
  const text = await response.text();
  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch (error) {
    throw new Error(`Cloudflare returned a non-JSON response (${response.status}): ${text}`);
  }
}

function parseJsonc(text) {
  let result = "";
  let inString = false;
  let inLineComment = false;
  let inBlockComment = false;
  let previousChar = "";

  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    const nextChar = text[index + 1];

    if (inLineComment) {
      if (char === "\n") {
        inLineComment = false;
        result += char;
      }
      continue;
    }

    if (inBlockComment) {
      if (char === "*" && nextChar === "/") {
        inBlockComment = false;
        index += 1;
      }
      continue;
    }

    if (!inString && char === "/" && nextChar === "/") {
      inLineComment = true;
      index += 1;
      continue;
    }

    if (!inString && char === "/" && nextChar === "*") {
      inBlockComment = true;
      index += 1;
      continue;
    }

    if (char === '"' && previousChar !== "\\") {
      inString = !inString;
    }

    result += char;
    previousChar = char;
  }

  return JSON.parse(result.replace(/,\s*([}\]])/g, "$1"));
}

function buildWorkerScript(assetBinding) {
  return `export default {
  async fetch(request, env) {
    return env.${assetBinding}.fetch(request);
  },
};
`;
}

function guessContentType(relativePath) {
  const extension = extname(relativePath).toLowerCase();
  return (
    {
      ".avif": "image/avif",
      ".css": "text/css; charset=utf-8",
      ".csv": "text/csv; charset=utf-8",
      ".gif": "image/gif",
      ".html": "text/html; charset=utf-8",
      ".ico": "image/x-icon",
      ".jpeg": "image/jpeg",
      ".jpg": "image/jpeg",
      ".js": "application/javascript; charset=utf-8",
      ".json": "application/json; charset=utf-8",
      ".map": "application/json; charset=utf-8",
      ".mjs": "application/javascript; charset=utf-8",
      ".otf": "font/otf",
      ".pdf": "application/pdf",
      ".png": "image/png",
      ".svg": "image/svg+xml",
      ".txt": "text/plain; charset=utf-8",
      ".ttf": "font/ttf",
      ".wasm": "application/wasm",
      ".webmanifest": "application/manifest+json; charset=utf-8",
      ".webp": "image/webp",
      ".woff": "font/woff",
      ".woff2": "font/woff2",
      ".xml": "application/xml; charset=utf-8",
    }[extension] ?? "application/octet-stream"
  );
}

function pickDefined(object) {
  return Object.fromEntries(Object.entries(object).filter(([, value]) => value !== undefined));
}

function formatCloudflareFailure(status, payload) {
  const cloudflareErrors = Array.isArray(payload?.errors) ? payload.errors : [];
  const details = cloudflareErrors
    .map((error) => {
      if (error?.message && error?.code) {
        return `${error.message} [code: ${error.code}]`;
      }

      return error?.message ?? JSON.stringify(error);
    })
    .join("; ");

  if (details) {
    return `Cloudflare API request failed with status ${status}: ${details}`;
  }

  return `Cloudflare API request failed with status ${status}.`;
}

function formatError(error) {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}
