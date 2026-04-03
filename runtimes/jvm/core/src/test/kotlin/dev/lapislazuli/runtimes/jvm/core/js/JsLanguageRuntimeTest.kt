package dev.lapislazuli.runtimes.jvm.core.js

import dev.lapislazuli.runtimes.jvm.core.bundle.BundleManifest
import dev.lapislazuli.runtimes.jvm.core.bundle.ScriptBundle
import dev.lapislazuli.runtimes.jvm.core.host.Callback
import dev.lapislazuli.runtimes.jvm.core.host.HostShapedRecipeSpec
import dev.lapislazuli.runtimes.jvm.core.runtime.LoadedPlugin
import dev.lapislazuli.runtimes.jvm.core.testsupport.FakeHostServices
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
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

                      const player = context.players.require("Alice");
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
                        effect: "speed",
                        durationTicks: 200,
                        amplifier: 1,
                      });
                      context.effects.applyPotion({
                        player,
                        effect: "jump",
                        durationTicks: 100,
                        amplifier: 0,
                        ambient: true,
                        particles: false,
                        icon: false,
                      });
                      context.effects.clearPotion(player, "speed");

                      const scratchRecipe = context.recipes.register({
                        kind: "shapeless",
                        id: "scratch_mix",
                        result: { type: "torch", amount: 2 },
                        ingredients: ["coal", { type: "stick", amount: 1 }],
                      });
                      scratchRecipe.unregister();
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
        assertTrue(hostServices.logMessages.contains("recipe:starter_frame"))
        assertTrue(hostServices.logMessages.contains("app:hello:fake-bukkit"))
        assertEquals(1, hostServices.playedSounds.size)
        assertEquals("player:Alice", hostServices.playedSounds.single().target)
        assertEquals("entity.player.levelup", hostServices.playedSounds.single().sound)
        assertEquals(1, hostServices.spawnedParticles.size)
        assertEquals("flame", hostServices.spawnedParticles.single().particle)
        assertEquals(listOf("Alice"), hostServices.spawnedParticles.single().players)
        assertEquals(1, hostServices.registeredRecipes.size)
        val recipe = assertInstanceOf(HostShapedRecipeSpec::class.java, hostServices.registeredRecipes.single())
        assertEquals("starter_frame", recipe.id)
        val activeEffects = hostServices.appliedPotionEffects[hostServices.player.id()]
        assertNotNull(activeEffects)
        assertFalse(activeEffects!!.containsKey("speed"))
        assertEquals(1, activeEffects.size)
        assertEquals(100, activeEffects.getValue("jump").durationTicks)
        assertEquals(1, hostServices.bossBars.size)
        assertEquals("status", hostServices.bossBars.single().id())
        assertEquals("Ready!", hostServices.bossBars.single().title())
        assertEquals("blue", hostServices.bossBars.single().color())
        assertEquals("segmented_10", hostServices.bossBars.single().style())
        assertEquals(1, hostServices.bossBars.single().players().size)
        assertEquals(1, hostServices.scoreboards.size)
        assertEquals("hud", hostServices.scoreboards.single().id())
        assertEquals("Stats", hostServices.scoreboards.single().title())
        assertEquals(mapOf(2 to "Online", 1 to "1"), hostServices.scoreboards.single().lines())
        assertEquals(1, hostServices.scoreboards.single().viewers().size)

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
