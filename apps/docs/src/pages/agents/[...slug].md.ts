import { getDocBySlug, getDocs, getRenderedMarkdown } from "../../lib/docs";

export function getStaticPaths() {
  return getDocs().map((doc) => ({
    params: {
      slug: doc.slug
    }
  }));
}

export function GET({ params }: { params: { slug?: string } }) {
  const slug = params.slug ?? "";
  const doc = getDocBySlug(slug);

  if (!doc) {
    return new Response("Not found", {
      status: 404,
      headers: {
        "Content-Type": "text/plain; charset=utf-8"
      }
    });
  }

  return new Response(getRenderedMarkdown(doc.repoPath, doc.raw, "agents"), {
    headers: {
      "Content-Type": "text/markdown; charset=utf-8"
    }
  });
}
