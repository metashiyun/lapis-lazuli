package org.shiyun.lapis.runtimes.jvm.core.node

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.shiyun.lapis.runtimes.jvm.core.bundle.BundleManifest
import org.shiyun.lapis.runtimes.jvm.core.bundle.ScriptBundle
import org.shiyun.lapis.runtimes.jvm.core.runtime.LoadedPlugin
import org.shiyun.lapis.runtimes.jvm.core.testsupport.FakeHostServices
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class NodeLanguageRuntimeTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun enablesBundleAndInvokesNodeBridge() {
        assumeTrue(nodeAvailable())

        val bundleDir = tempDir.resolve("hello-node")
        Files.createDirectories(bundleDir)
        Files.writeString(
            bundleDir.resolve("main.js"),
            """
                module.exports = {
                  default: {
                    name: "Hello Node",
                    onEnable(context) {
                      context.app.log.info("enabled");
                      context.app.onShutdown(() => {
                        context.app.log.info("shutdown");
                      });
                      context.config.set("message", "Hello");
                      context.storage.plugin.set("count", 4);
                      context.storage.files.writeText("hello.txt", "world");

                      const player = context.players.require("Alice");
                      const menu = context.inventory.create({
                        id: "starter-kit",
                        title: "Starter Menu",
                        size: 9,
                      });
                      const item = context.items.create({
                        type: "stone",
                        amount: 2,
                        name: "Starter",
                        lore: ["Line A", "Line B"],
                        enchantments: { sharpness: 1 },
                      });
                      menu.set(0, item);
                      menu.open(player);

                      const world = context.worlds.require("world");
                      world.setTime(2000);
                      const spawned = context.entities.spawn({
                        type: "zombie",
                        location: { world: "world", x: 1, y: 64, z: 1 },
                      });
                      context.app.log.info("spawned:" + spawned.type);

                      context.effects.playSound({
                        player,
                        sound: "entity.player.levelup",
                        volume: 0.5,
                        pitch: 1.25,
                      });
                      context.effects.spawnParticle({
                        particle: "flame",
                        location: { world: "world", x: 2, y: 65, z: 2 },
                        count: 3,
                        extra: 0.2,
                        players: [player],
                      });
                      context.effects.applyPotion({
                        player,
                        effect: "jump",
                        durationTicks: 100,
                        amplifier: 1,
                      });
                      context.effects.clearPotion(player, "jump");

                      const recipe = context.recipes.register({
                        kind: "shaped",
                        id: "starter_frame",
                        result: { type: "stone", amount: 1 },
                        shape: ["AB", "BA"],
                        ingredients: {
                          A: "dirt",
                          B: { type: "stick", amount: 1 },
                        },
                      });
                      context.app.log.info("recipe:" + recipe.id);

                      const bossBar = context.bossBars.create({
                        id: "status",
                        title: "Ready",
                        color: "green",
                        style: "solid",
                        progress: 0.5,
                      });
                      bossBar.addPlayer(player);
                      bossBar.setTitle("Ready!");
                      bossBar.setColor("blue");
                      bossBar.setStyle("segmented_10");

                      const scoreboard = context.scoreboards.create({
                        id: "hud",
                        title: "Stats",
                      });
                      scoreboard.setLine(2, "Online");
                      scoreboard.setLine(1, "1");
                      scoreboard.show(player);

                      context.commands.register({
                        name: "hello",
                        permission: "lapis.hello",
                        execute({ sender }) {
                          sender.sendMessage("Hello " + sender.name);
                          sender.player?.actionBar("Wave");
                          return true;
                        },
                      });

                      context.events.on("player.join", (event) => {
                        event.setJoinMessage("Welcome " + event.player.name);
                        context.app.log.info("join:" + event.player.name);
                      });

                      context.chat.broadcast("broadcast hello");
                      context.unsafe.backend.dispatchCommand("say hello");
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
                id = "hello-node",
                name = "Hello Node",
                version = "1.0.0",
                engine = "node",
                main = "main.js",
                apiVersion = "1.0",
            ),
        )
        val hostServices = FakeHostServices(tempDir.resolve("data"), engineName = "node")
        val plugin: LoadedPlugin = NodeLanguageRuntime(nodeCommand()).load(bundle, hostServices)

        plugin.enable()

        assertEquals("Hello", hostServices.configValues["message"])
        assertEquals(4, hostServices.storageValues["count"])
        assertTrue(Files.exists(tempDir.resolve("data").resolve("hello.txt")))
        assertTrue(hostServices.broadcastMessages.contains("broadcast hello"))
        assertTrue(hostServices.dispatchedCommands.contains("say hello"))
        assertTrue(hostServices.logMessages.contains("enabled"))
        assertTrue(hostServices.logMessages.contains("spawned:zombie"))
        assertTrue(hostServices.logMessages.contains("recipe:starter_frame"))
        assertEquals(1, hostServices.playedSounds.size)
        assertEquals("player:Alice", hostServices.playedSounds.single().target)
        assertEquals(1, hostServices.spawnedParticles.size)
        assertEquals(listOf("Alice"), hostServices.spawnedParticles.single().players)
        assertEquals(1, hostServices.bossBars.size)
        assertEquals("Ready!", hostServices.bossBars.single().title())
        assertEquals("blue", hostServices.bossBars.single().color())
        assertEquals("segmented_10", hostServices.bossBars.single().style())
        assertEquals(1, hostServices.scoreboards.size)
        assertEquals(mapOf(2 to "Online", 1 to "1"), hostServices.scoreboards.single().lines())

        val result = hostServices.commandCallback.invoke(
            mapOf(
                "sender" to mapOf(
                    "name" to "Alice",
                    "type" to "player",
                    "id" to hostServices.player.id(),
                    "player" to hostServices.player,
                    "sendMessage" to org.shiyun.lapis.runtimes.jvm.core.host.Callback { payload ->
                        hostServices.sentMessages += payload.toString()
                        null
                    },
                    "hasPermission" to org.shiyun.lapis.runtimes.jvm.core.host.Callback { true },
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

        hostServices.eventCallbacks.getValue("player.join").invoke(hostServices.playerJoinPayload("Bob"))
        assertTrue(hostServices.logMessages.contains("join:Bob"))

        plugin.close()
        assertTrue(hostServices.logMessages.contains("disabled"))
        assertTrue(hostServices.logMessages.contains("shutdown"))
    }

    @Test
    fun fetchesHttpThroughNodeRuntime() {
        assumeTrue(nodeAvailable())

        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/echo") { exchange ->
            val requestBody = exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            val responseBody = "echo:${exchange.requestHeaders.getFirst("x-lapis")}:$requestBody"
            val responseBytes = responseBody.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/plain")
            exchange.sendResponseHeaders(201, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }
        }
        server.start()

        try {
            val bundleDir = tempDir.resolve("hello-http")
            Files.createDirectories(bundleDir)
            Files.writeString(
                bundleDir.resolve("main.js"),
                """
                    module.exports = {
                      default: {
                        name: "Hello Http",
                        async onEnable(context) {
                          const response = await context.http.fetch("${'$'}{url}", {
                            method: "POST",
                            headers: {
                              "x-lapis": "node",
                            },
                            body: "ping",
                          });
                          context.app.log.info("http:" + response.status + ":" + response.ok + ":" + response.body);
                        },
                      },
                    };
                """.trimIndent().replace("\${url}", "http://127.0.0.1:${server.address.port}/echo"),
            )

            val bundle = ScriptBundle(
                bundleDirectory = bundleDir,
                manifestPath = bundleDir.resolve("lapis-plugin.json"),
                mainFile = bundleDir.resolve("main.js"),
                manifest = BundleManifest(
                    id = "hello-http",
                    name = "Hello Http",
                    version = "1.0.0",
                    engine = "node",
                    main = "main.js",
                    apiVersion = "1.0",
                ),
            )
            val hostServices = FakeHostServices(tempDir.resolve("data-http"), engineName = "node")
            val plugin: LoadedPlugin = NodeLanguageRuntime(nodeCommand()).load(bundle, hostServices)

            plugin.enable()

            assertTrue(hostServices.logMessages.contains("http:201:true:echo:node:ping"))
            plugin.close()
        } finally {
            server.stop(0)
        }
    }

    private fun nodeCommand(): String =
        System.getenv("LAPIS_NODE_COMMAND")
            ?.takeIf(String::isNotBlank)
            ?: "node"

    private fun nodeAvailable(): Boolean =
        runCatching {
            val process = ProcessBuilder(nodeCommand(), "--version").start()
            process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0
        }.getOrDefault(false)
}
