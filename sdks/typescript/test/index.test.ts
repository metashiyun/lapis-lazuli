import { describe, expect, it } from "bun:test";
import { definePlugin } from "../src/index";

describe("definePlugin", () => {
  it("returns the same plugin definition", () => {
    const plugin = definePlugin({
      name: "hello",
      version: "1.0.0",
    });

    expect(plugin.name).toBe("hello");
    expect(plugin.version).toBe("1.0.0");
  });
});

