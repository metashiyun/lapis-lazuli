declare const Bun: {
  build(options: {
    entrypoints: string[];
    root: string;
    outdir: string;
    target: "node" | "bun" | "browser";
    format: "cjs" | "esm" | "iife";
    sourcemap: "none" | "linked" | "inline" | "external";
    minify: boolean;
    splitting: boolean;
  }): Promise<{
    success: boolean;
    logs: Array<{ message: string }>;
  }>;
};
