import { getAgentsIndexMarkdown } from "../../lib/docs";

export function GET() {
  return new Response(getAgentsIndexMarkdown(), {
    headers: {
      "Content-Type": "text/markdown; charset=utf-8"
    }
  });
}
