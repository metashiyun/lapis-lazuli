import path from "node:path";

const REPO_BASE_URL = "https://github.com/metashiyun/lapis-lazuli";
const DOCS_BASE_URL = `${REPO_BASE_URL}/tree/main/docs`;
const DOCS_PREFIX = "../../../../docs/";
const HOME_DOC_REPO_PATH = "README.md";

const DOC_GROUPS = [
  {
    id: "start",
    label: "Start Here",
    description: "Project orientation, architecture, and the current support envelope."
  },
  {
    id: "api",
    label: "API Reference",
    description: "Reference material for the runtime contract, SDKs, CLI, and bundle layout."
  },
  {
    id: "guides",
    label: "Guides",
    description: "End-to-end workflows for authoring, bundling, and validating plugins."
  }
] as const;

type DocGroupId = (typeof DOC_GROUPS)[number]["id"];

type MarkdownModule = {
  default: any;
};

type DocConfig = {
  group: DocGroupId;
  order: number;
};

const DOC_CONFIG: Record<string, DocConfig> = {
  "architecture.md": { group: "start", order: 10 },
  "compatibility.md": { group: "start", order: 20 },
  "api/runtime-host-api.md": { group: "api", order: 30 },
  "api/typescript-sdk.md": { group: "api", order: 40 },
  "cli.md": { group: "api", order: 50 },
  "bundle-format.md": { group: "api", order: 60 },
  "python-sdk.md": { group: "api", order: 70 },
  "authoring.md": { group: "guides", order: 80 },
  "testing.md": { group: "guides", order: 90 }
};

export type DocEntry = {
  group: DocGroupId;
  groupLabel: string;
  markdownUrl: string;
  order: number;
  repoPath: string;
  raw: string;
  slug: string;
  sourceUrl: string;
  title: string;
  description: string;
  url: string;
  Content: any;
};

export type DocsHomeEntry = {
  markdownUrl: string;
  repoPath: string;
  raw: string;
  sourceUrl: string;
  title: string;
  description: string;
  url: string;
  Content: any;
};

export type DocGroup = (typeof DOC_GROUPS)[number] & {
  docs: DocEntry[];
};

const compiledDocModules = import.meta.glob("../../../../docs/**/*.{md,mdx}", {
  eager: true
}) as Record<string, MarkdownModule>;

const rawDocModules = import.meta.glob("../../../../docs/**/*.{md,mdx}", {
  eager: true,
  import: "default",
  query: "?raw"
}) as Record<string, string>;

const groupLookup = new Map(
  DOC_GROUPS.map((group) => [group.id, group])
);

function toRepoPath(modulePath: string) {
  return modulePath.replace(DOCS_PREFIX, "");
}

function toSlug(repoPath: string) {
  return repoPath.replace(/\.(md|mdx)$/, "");
}

function toUrl(slug: string) {
  return `/docs/${slug}.md`;
}

function toMarkdownUrl(slug: string) {
  return `/agents/${slug}.md`;
}

function splitHref(href: string) {
  const match = href.match(/^([^?#]*)(.*)$/);

  return {
    pathname: match?.[1] ?? href,
    suffix: match?.[2] ?? ""
  };
}

function isAbsoluteHref(pathname: string) {
  return (
    pathname.length === 0 ||
    pathname.startsWith("/") ||
    pathname.startsWith("#") ||
    pathname.startsWith("?") ||
    /^[a-z][a-z0-9+.-]*:/i.test(pathname)
  );
}

function toDocsRouteFromRepoPath(repoPath: string) {
  if (!repoPath.startsWith("docs/")) {
    return null;
  }

  const docsPath = repoPath.slice("docs/".length);

  if (/^README\.(md|mdx)$/i.test(docsPath)) {
    return "/docs";
  }

  return toUrl(toSlug(docsPath));
}

function toAgentsRouteFromRepoPath(repoPath: string) {
  if (!repoPath.startsWith("docs/")) {
    return null;
  }

  const docsPath = repoPath.slice("docs/".length);

  if (/^README\.(md|mdx)$/i.test(docsPath)) {
    return "/agents/index.md";
  }

  return toMarkdownUrl(toSlug(docsPath));
}

function rewriteMarkdownHref(repoPath: string, href: string, target: "agents" | "docs") {
  const { pathname, suffix } = splitHref(href);

  if (isAbsoluteHref(pathname)) {
    return href;
  }

  const sourcePath = `docs/${repoPath}`;
  const resolvedRepoPath = path.posix.normalize(
    path.posix.join(path.posix.dirname(sourcePath), pathname)
  );
  const docsRoute =
    target === "docs"
      ? toDocsRouteFromRepoPath(resolvedRepoPath)
      : toAgentsRouteFromRepoPath(resolvedRepoPath);

  if (docsRoute) {
    return `${docsRoute}${suffix}`;
  }

  return `${REPO_BASE_URL}/blob/main/${resolvedRepoPath}${suffix}`;
}

function rewriteMarkdownLinks(raw: string, repoPath: string, target: "agents" | "docs") {
  return raw.replace(/(!)?\[([^\]]+)\]\(([^)]+)\)/g, (match, image, label, href) => {
    if (image) {
      return match;
    }

    return `[${label}](${rewriteMarkdownHref(repoPath, href, target)})`;
  });
}

function stripMarkdown(text: string) {
  return text
    .replace(/`([^`]+)`/g, "$1")
    .replace(/\[([^\]]+)\]\([^)]+\)/g, "$1")
    .replace(/\*\*([^*]+)\*\*/g, "$1")
    .replace(/\*([^*]+)\*/g, "$1")
    .replace(/_/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

function extractTitle(raw: string, repoPath: string) {
  const headingMatch = raw.match(/^#\s+(.+)$/m);

  if (headingMatch) {
    return stripMarkdown(headingMatch[1]);
  }

  return repoPath
    .replace(/\.(md|mdx)$/, "")
    .split("/")
    .at(-1)
    ?.split("-")
    .map((part) => `${part.slice(0, 1).toUpperCase()}${part.slice(1)}`)
    .join(" ") ?? "Documentation";
}

function extractDescription(raw: string) {
  const lines = raw.split(/\r?\n/);
  const paragraphs: string[] = [];
  let current: string[] = [];
  let inCodeBlock = false;

  for (const line of lines) {
    const trimmed = line.trim();

    if (trimmed.startsWith("```")) {
      inCodeBlock = !inCodeBlock;
      continue;
    }

    if (inCodeBlock) {
      continue;
    }

    if (!trimmed) {
      if (current.length > 0) {
        paragraphs.push(current.join(" "));
        current = [];
      }
      continue;
    }

    if (trimmed.startsWith("#")) {
      continue;
    }

    if (trimmed.startsWith("- ") || /^\d+\./.test(trimmed)) {
      if (current.length > 0) {
        paragraphs.push(current.join(" "));
        current = [];
      }
      continue;
    }

    current.push(trimmed.replace(/^>\s?/, ""));
  }

  if (current.length > 0) {
    paragraphs.push(current.join(" "));
  }

  return stripMarkdown(paragraphs[0] ?? "Technical reference and implementation notes.");
}

function inferConfig(repoPath: string): DocConfig {
  if (repoPath in DOC_CONFIG) {
    return DOC_CONFIG[repoPath];
  }

  if (repoPath.startsWith("api/")) {
    return { group: "api", order: 900 };
  }

  return { group: "guides", order: 900 };
}

const docSources = Object.entries(compiledDocModules).map(([modulePath, module]) => {
  const repoPath = toRepoPath(modulePath);
  const raw = rawDocModules[modulePath] ?? "";
  const slug = toSlug(repoPath);

  return {
    repoPath,
    raw,
    slug,
    sourceUrl: `${REPO_BASE_URL}/blob/main/docs/${repoPath}`,
    title: extractTitle(raw, repoPath),
    description: extractDescription(raw),
    Content: module.default
  };
});

const docsHomeSource = docSources.find((doc) => doc.repoPath === HOME_DOC_REPO_PATH);

if (!docsHomeSource) {
  throw new Error(`Unable to resolve docs home source "${HOME_DOC_REPO_PATH}".`);
}

const docsHome = {
  ...docsHomeSource,
  markdownUrl: "/agents/index.md",
  url: "/docs"
} satisfies DocsHomeEntry;

const docs = docSources
  .filter((doc) => doc.repoPath !== HOME_DOC_REPO_PATH)
  .map((doc) => {
    const config = inferConfig(doc.repoPath);
    const group = groupLookup.get(config.group);

    if (!group) {
      throw new Error(`Unknown docs group for ${doc.repoPath}`);
    }

    return {
      group: config.group,
      groupLabel: group.label,
      markdownUrl: toMarkdownUrl(doc.slug),
      order: config.order,
      repoPath: doc.repoPath,
      raw: doc.raw,
      slug: doc.slug,
      sourceUrl: doc.sourceUrl,
      title: doc.title,
      description: doc.description,
      url: toUrl(doc.slug),
      Content: doc.Content
    } satisfies DocEntry;
  })
  .sort((left, right) => left.order - right.order || left.title.localeCompare(right.title));

const docsBySlug = new Map(docs.map((doc) => [doc.slug, doc]));

export const repoBaseUrl = REPO_BASE_URL;
export const repoDocsUrl = DOCS_BASE_URL;

export function getDocs() {
  return docs;
}

export function getDocsHome() {
  return docsHome;
}

export function getRenderedMarkdown(repoPath: string, raw: string, target: "agents" | "docs") {
  return rewriteMarkdownLinks(raw, repoPath, target);
}

export function getAgentsIndexMarkdown() {
  const intro = getRenderedMarkdown(docsHome.repoPath, docsHome.raw, "agents").trimEnd();
  const groups = getDocsByGroup()
    .map((group) => {
      const items = group.docs
        .map((doc) => `- [${doc.title}](${doc.markdownUrl}): ${doc.description}`)
        .join("\n");

      return `## ${group.label}\n\n${items}`;
    })
    .join("\n\n");

  return [
    intro,
    "",
    "## Pages",
    "",
    "- [Overview](/agents/index.md): index and package names.",
    "",
    groups
  ].join("\n");
}

export function getDocsByGroup() {
  return DOC_GROUPS.map((group) => ({
    ...group,
    docs: docs.filter((doc) => doc.group === group.id)
  })) satisfies DocGroup[];
}

export function getDocBySlug(slug: string) {
  return docsBySlug.get(slug);
}

export function getAdjacentDocs(slug: string) {
  const index = docs.findIndex((doc) => doc.slug === slug);

  if (index === -1) {
    return {
      previous: undefined,
      next: undefined
    };
  }

  return {
    previous: docs[index - 1],
    next: docs[index + 1]
  };
}
