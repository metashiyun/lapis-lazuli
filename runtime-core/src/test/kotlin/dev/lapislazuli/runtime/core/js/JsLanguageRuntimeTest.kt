package dev.lapislazuli.runtime.core.js

import dev.lapislazuli.runtime.core.bundle.BundleManifest
import dev.lapislazuli.runtime.core.bundle.ScriptBundle
import dev.lapislazuli.runtime.core.host.Callback
import dev.lapislazuli.runtime.core.runtime.LoadedPlugin
import dev.lapislazuli.runtime.core.testsupport.FakeHostServices
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class JsLanguageRuntimeTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun enablesBundleAndInvokesServiceOrientedHostBridge() {
        val bundleDir = tempDir.resolve("hello")
        Files.createDirectories(bundleDir)
        Files.writeString(
            bundleDir.resolve("main.js"),
            """
                module.exports = {
                  default: {
                    name: "Hello",
                    onEnable(context) {
                      context.app.log.info("enabled");
                      context.config.set("message", "Hello");
                      context.storage.plugin.set("count", 2);
                      context.storage.files.writeText("hello.txt", "world");
                      
                      const item = context.items.create({
                        type: "stone",
                        amount: 2,
                        name: { text: "Starter" },
                        lore: ["Line A", { text: "Line B" }],
                        enchantments: { sharpness: 1 },
                      });
                      const menu = context.inventory.create({
                        id: "starter-kit",
                        title: ["Starter ", { text: "Menu" }],
                        size: 9,
                      });
                      menu.set(0, item);
                      menu.open(context.players.require("Alice"));
                      
                      const world = context.worlds.require("world");
                      world.setTime(1000);
                      const spawned = context.entities.spawn({
                        type: "zombie",
                        location: { world: "world", x: 1, y: 64, z: 1 },
                      });
                      context.app.log.info("spawned:" + spawned.type);
                      
                      context.commands.register({
                        name: "hello",
                        permission: "lapis.hello",
                        execute({ sender }) {
                          if (!sender.player) {
                            throw new Error("Missing player sender");
                          }
                          sender.sendMessage("Hello " + sender.name);
                          sender.player.actionBar("Wave");
                          sender.player.showTitle("Welcome", { subtitle: "Friend" });
                          return true;
                        },
                      });
                      
                      context.events.on("player.join", (event) => {
                        context.app.log.info("join:" + event.player.name);
                      });
                      context.unsafe.events.onJava("org.bukkit.event.player.PlayerJoinEvent", (event) => {
                        context.app.log.info("java:" + event.name);
                      });
                      context.chat.broadcast("broadcast hello");
                      context.unsafe.backend.dispatchCommand("say hello");
                      context.app.log.info("app:" + context.app.id + ":" + context.app.backend);
                    },
                    onDisable(context) {
                      context.app.log.info("disabled");
                    },
                  },
                };
            """.trimIndent(),
        )

        val bundle = ScriptBundle(
            bundleDirectory = bundleDir,
            manifestPath = bundleDir.resolve("lapis-plugin.json"),
            mainFile = bundleDir.resolve("main.js"),
            manifest = BundleManifest(
                id = "hello",
                name = "Hello",
                version = "1.0.0",
                engine = "js",
                main = "main.js",
                apiVersion = "1.0",
            ),
        )
        val hostServices = FakeHostServices(tempDir.resolve("data"))
        val plugin: LoadedPlugin = JsLanguageRuntime().load(bundle, hostServices)

        plugin.enable()

        assertEquals("Hello", hostServices.configValues["message"])
        assertEquals(2, hostServices.storageValues["count"])
        assertTrue(Files.exists(tempDir.resolve("data").resolve("hello.txt")))
        assertEquals(listOf("broadcast hello"), hostServices.broadcastMessages)
        assertEquals(listOf("say hello"), hostServices.dispatchedCommands)
        assertEquals("org.bukkit.event.player.PlayerJoinEvent", hostServices.javaEventClassName)
        assertTrue(hostServices.logMessages.contains("enabled"))
        assertTrue(hostServices.logMessages.contains("spawned:zombie"))
        assertTrue(hostServices.logMessages.contains("app:hello:fake-bukkit"))

        val result = hostServices.commandCallback.invoke(
            mapOf(
                "sender" to mapOf(
                    "name" to "Alice",
                    "type" to "player",
                    "id" to hostServices.player.id(),
                    "player" to hostServices.player,
                    "sendMessage" to Callback { payload ->
                        hostServices.sentMessages += payload.toString()
                        null
                    },
                    "hasPermission" to Callback { true },
                    "unsafe" to mapOf("handle" to Any()),
                ),
                "args" to emptyList<String>(),
                "label" to "hello",
                "command" to "hello",
            ),
        )

        assertEquals(true, result)
        assertTrue(hostServices.sentMessages.contains("Hello Alice"))
        assertTrue(hostServices.sentMessages.contains("action:Wave"))
        assertTrue(hostServices.sentMessages.contains("title:Welcome|Friend"))

        hostServices.eventCallbacks.getValue("player.join").invoke(hostServices.playerJoinPayload("Bob"))
        hostServices.javaEventCallback.invoke(mapOf("name" to "BobEvent"))
        assertTrue(hostServices.logMessages.contains("join:Bob"))
        assertTrue(hostServices.logMessages.contains("java:BobEvent"))

        plugin.close()
        assertTrue(hostServices.logMessages.contains("disabled"))
    }
}
