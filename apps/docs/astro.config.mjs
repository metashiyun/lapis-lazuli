import mdx from "@astrojs/mdx";
import { defineConfig } from "astro/config";
import remarkDocLinks from "./src/lib/remark-doc-links.mjs";

export default defineConfig({
  integrations: [mdx()],
  markdown: {
    remarkPlugins: [remarkDocLinks]
  },
  output: "static"
});
