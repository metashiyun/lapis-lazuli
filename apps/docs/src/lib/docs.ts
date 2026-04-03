const REPO_BASE_URL = "https://github.com/metashiyun/lapis-lazuli";
const DOCS_BASE_URL = `${REPO_BASE_URL}/tree/main/docs`;
const DOCS_PREFIX = "../../../../docs/";

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
  order: number;
  repoPath: string;
  slug: string;
  sourceUrl: string;
  title: string;
  description: string;
  url: string;
  Content: any;
};

export type DocGroup = (typeof DOC_GROUPS)[number] & {
  docs: DocEntry[];
};

const compiledDocModules = import.meta.glob("../../../../docs/**/*.md", {
  eager: true
}) as Record<string, MarkdownModule>;

const rawDocModules = import.meta.glob("../../../../docs/**/*.md", {
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
  return repoPath.replace(/\.md$/, "");
}

function toUrl(slug: string) {
  return `/docs/${slug}.md`;
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
    .replace(/\.md$/, "")
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

const docs = Object.entries(compiledDocModules)
  .map(([modulePath, module]) => {
    const repoPath = toRepoPath(modulePath);

    if (repoPath === "README.md") {
      return null;
    }

    const raw = rawDocModules[modulePath] ?? "";
    const config = inferConfig(repoPath);
    const group = groupLookup.get(config.group);

    if (!group) {
      throw new Error(`Unknown docs group for ${repoPath}`);
    }

    const slug = toSlug(repoPath);

    return {
      group: config.group,
      groupLabel: group.label,
      order: config.order,
      repoPath,
      slug,
      sourceUrl: `${REPO_BASE_URL}/blob/main/docs/${repoPath}`,
      title: extractTitle(raw, repoPath),
      description: extractDescription(raw),
      url: toUrl(slug),
      Content: module.default
    } satisfies DocEntry;
  })
  .filter((doc): doc is DocEntry => doc !== null)
  .sort((left, right) => left.order - right.order || left.title.localeCompare(right.title));

const docsBySlug = new Map(docs.map((doc) => [doc.slug, doc]));

export const repoBaseUrl = REPO_BASE_URL;
export const repoDocsUrl = DOCS_BASE_URL;

export function getDocs() {
  return docs;
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
