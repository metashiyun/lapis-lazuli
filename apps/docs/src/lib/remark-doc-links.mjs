import path from "node:path";

const REPO_BASE_URL = "https://github.com/metashiyun/lapis-lazuli";
const DOCS_SEGMENT = "/docs/";

function getSourceRepoPath(file) {
  const filePath = file.history?.[0] ?? file.path ?? "";
  const normalizedPath = filePath.replaceAll("\\", "/");
  const docsIndex = normalizedPath.lastIndexOf(DOCS_SEGMENT);

  if (docsIndex === -1) {
    return null;
  }

  return normalizedPath.slice(docsIndex + DOCS_SEGMENT.length);
}

function splitHref(href) {
  const match = href.match(/^([^?#]*)(.*)$/);

  return {
    pathname: match?.[1] ?? href,
    suffix: match?.[2] ?? ""
  };
}

function isAbsoluteHref(pathname) {
  return (
    pathname.length === 0 ||
    pathname.startsWith("/") ||
    pathname.startsWith("#") ||
    pathname.startsWith("?") ||
    /^[a-z][a-z0-9+.-]*:/i.test(pathname)
  );
}

function toDocsRoute(repoPath) {
  if (!repoPath.startsWith("docs/")) {
    return null;
  }

  const docsPath = repoPath.slice("docs/".length);

  if (/^README\.(md|mdx)$/i.test(docsPath)) {
    return "/docs";
  }

  return `/docs/${docsPath.replace(/\.(md|mdx)$/i, "")}.md`;
}

function rewriteHref(href, sourceRepoPath) {
  const { pathname, suffix } = splitHref(href);

  if (isAbsoluteHref(pathname)) {
    return href;
  }

  const sourcePath = `docs/${sourceRepoPath}`;
  const resolvedRepoPath = path.posix.normalize(
    path.posix.join(path.posix.dirname(sourcePath), pathname)
  );
  const docsRoute = toDocsRoute(resolvedRepoPath);

  if (docsRoute) {
    return `${docsRoute}${suffix}`;
  }

  return `${REPO_BASE_URL}/blob/main/${resolvedRepoPath}${suffix}`;
}

function visit(node, callback) {
  callback(node);

  if (!Array.isArray(node.children)) {
    return;
  }

  for (const child of node.children) {
    visit(child, callback);
  }
}

export default function remarkDocLinks() {
  return function transform(tree, file) {
    const sourceRepoPath = getSourceRepoPath(file);

    if (!sourceRepoPath) {
      return;
    }

    visit(tree, (node) => {
      if (node.type === "link" && typeof node.url === "string") {
        node.url = rewriteHref(node.url, sourceRepoPath);
      }
    });
  };
}
