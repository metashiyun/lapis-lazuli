package dev.lapislazuli.runtime.core.js

import dev.lapislazuli.runtime.core.bundle.BundleManifest
import dev.lapislazuli.runtime.core.bundle.ScriptBundle
import dev.lapislazuli.runtime.core.host.Callback
import dev.lapislazuli.runtime.core.host.ConfigStore
import dev.lapislazuli.runtime.core.host.DataDirectory
import dev.lapislazuli.runtime.core.host.HostServices
import dev.lapislazuli.runtime.core.host.Registration
import dev.lapislazuli.runtime.core.host.RuntimeLogger
import dev.lapislazuli.runtime.core.host.TaskHandle
import dev.lapislazuli.runtime.core.runtime.LoadedPlugin
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
    fun enablesBundleAndInvokesHostBridge() {
        val bundleDir = tempDir.resolve("hello")
        Files.createDirectories(bundleDir)
        Files.writeString(
            bundleDir.resolve("main.js"),
            """
                module.exports = {
                  default: {
                    name: "Hello",
                    onEnable(context) {
                      context.logger.info("enabled");
                      context.config.set("message", "Hello");
                      context.dataDir.writeText("hello.txt", "world");
                      context.commands.register({
                        name: "hello",
                        execute({ sender }) {
                          if (!sender.javaHandle) {
                            throw new Error("Missing sender handle");
                          }
                          sender.sendMessage("Hello " + sender.name);
                          return true;
                        },
                      });
                      context.events.on("playerJoin", (event) => {
                        context.logger.info("join:" + event.playerName + ":" + Boolean(event.playerHandle) + ":" + Boolean(event.javaEvent));
                      });
                      context.events.onJava("org.bukkit.event.player.PlayerJoinEvent", (event) => {
                        context.logger.info("java:" + event.name);
                      });
                      context.server.dispatchCommand("say hello");
                      context.server.broadcast("broadcast hello");
                      context.logger.info("server:" + Boolean(context.server.bukkit) + ":" + Boolean(context.server.plugin) + ":" + Boolean(context.server.console));
                    },
                    onDisable(context) {
                      context.logger.info("disabled");
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
        assertTrue(Files.exists(tempDir.resolve("data").resolve("hello.txt")))
        assertEquals(
            listOf("enabled", "server:true:true:true"),
            hostServices.logMessages,
        )
        assertEquals(listOf("say hello"), hostServices.dispatchedCommands)
        assertEquals(listOf("broadcast hello"), hostServices.broadcastMessages)
        assertEquals("org.bukkit.event.player.PlayerJoinEvent", hostServices.javaEventClassName)

        val result = hostServices.commandCallback.invoke(
            mapOf(
                "sender" to mapOf(
                    "name" to "Alice",
                    "type" to "player",
                    "uuid" to "1234",
                    "javaHandle" to Any(),
                    "sendMessage" to Callback { payload ->
                        hostServices.sentMessages += payload.toString()
                        null
                    },
                ),
                "args" to emptyList<String>(),
                "label" to "hello",
            ),
        )

        assertEquals(true, result)
        assertEquals(listOf("Hello Alice"), hostServices.sentMessages)

        hostServices.eventCallback.invoke(
            mapOf(
                "playerName" to "Bob",
                "playerHandle" to Any(),
                "javaEvent" to Any(),
            ),
        )
        hostServices.javaEventCallback.invoke(mapOf("name" to "BobEvent"))
        assertEquals(
            listOf("enabled", "server:true:true:true", "join:Bob:true:true", "java:BobEvent"),
            hostServices.logMessages,
        )

        plugin.close()
        assertEquals(
            listOf("enabled", "server:true:true:true", "join:Bob:true:true", "java:BobEvent", "disabled"),
            hostServices.logMessages,
        )
    }

    private class FakeHostServices(
        private val dataRoot: Path,
    ) : HostServices {
        val configValues = linkedMapOf<String, Any?>()
        val logMessages = mutableListOf<String>()
        val sentMessages = mutableListOf<String>()
        val dispatchedCommands = mutableListOf<String>()
        val broadcastMessages = mutableListOf<String>()
        lateinit var commandCallback: Callback
        lateinit var eventCallback: Callback
        lateinit var javaEventCallback: Callback
        var javaEventClassName: String? = null

        init {
            Files.createDirectories(dataRoot)
        }

        override fun logger(): RuntimeLogger =
            object : RuntimeLogger {
                override fun info(message: String) {
                    logMessages += message
                }

                override fun warn(message: String) {
                    logMessages += "warn:$message"
                }

                override fun error(message: String, error: Throwable?) {
                    logMessages += "error:$message"
                }
            }

        override fun registerCommand(
            name: String,
            description: String,
            usage: String,
            aliases: List<String>,
            execute: Callback,
        ): Registration {
            commandCallback = execute
            return Registration {}
        }

        override fun registerEvent(eventKey: String, handler: Callback): Registration {
            eventCallback = handler
            return Registration {}
        }

        override fun registerJavaEvent(eventClassName: String, handler: Callback): Registration {
            javaEventClassName = eventClassName
            javaEventCallback = handler
            return Registration {}
        }

        override fun runNow(task: Callback): TaskHandle {
            task.invoke(null)
            return TaskHandle {}
        }

        override fun runLater(delayTicks: Long, task: Callback): TaskHandle = TaskHandle {}

        override fun runTimer(delayTicks: Long, intervalTicks: Long, task: Callback): TaskHandle = TaskHandle {}

        override fun config(): ConfigStore =
            object : ConfigStore {
                override fun get(path: String): Any? = configValues[path]

                override fun set(path: String, value: Any?) {
                    configValues[path] = value
                }

                override fun save() {
                }

                override fun reload() {
                }

                override fun keys(): List<String> = configValues.keys.toList()
            }

        override fun dataDirectory(): DataDirectory =
            object : DataDirectory {
                override fun path(): String = dataRoot.toString()

                override fun resolve(vararg segments: String): String =
                    segments.fold(dataRoot) { path, segment -> path.resolve(segment) }.toString()

                override fun readText(relativePath: String): String =
                    Files.readString(dataRoot.resolve(relativePath))

                override fun writeText(relativePath: String, contents: String) {
                    val target = dataRoot.resolve(relativePath)
                    target.parent?.let(Files::createDirectories)
                    Files.writeString(target, contents)
                }

                override fun exists(relativePath: String): Boolean =
                    Files.exists(dataRoot.resolve(relativePath))

                override fun mkdirs(relativePath: String) {
                    Files.createDirectories(dataRoot.resolve(relativePath))
                }
            }

        override fun javaType(className: String): Class<*> = Class.forName(className)

        override fun serverHandle(): Any = Any()

        override fun pluginHandle(): Any = Any()

        override fun consoleSenderHandle(): Any = Any()

        override fun dispatchConsoleCommand(command: String): Boolean {
            dispatchedCommands += command
            return true
        }

        override fun broadcastMessage(message: String): Int {
            broadcastMessages += message
            return 1
        }

        override fun close() {
        }
    }
}
