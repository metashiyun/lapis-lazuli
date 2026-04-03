package org.shiyun.lapislazuli.runtime.core.js

import org.shiyun.lapislazuli.runtime.core.bundle.BundleManifest
import org.shiyun.lapislazuli.runtime.core.bundle.ScriptBundle
import org.shiyun.lapislazuli.runtime.core.host.Callback
import org.shiyun.lapislazuli.runtime.core.host.ConfigStore
import org.shiyun.lapislazuli.runtime.core.host.DataDirectory
import org.shiyun.lapislazuli.runtime.core.host.HostServices
import org.shiyun.lapislazuli.runtime.core.host.Registration
import org.shiyun.lapislazuli.runtime.core.host.RuntimeLogger
import org.shiyun.lapislazuli.runtime.core.host.TaskHandle
import org.shiyun.lapislazuli.runtime.core.runtime.LoadedPlugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
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
                          sender.sendMessage("Hello " + sender.name);
                          return true;
                        },
                      });
                      context.events.on("playerJoin", (event) => {
                        context.logger.info("join:" + event.playerName);
                      });
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
        assertEquals(listOf("enabled"), hostServices.logMessages)

        val result = hostServices.commandCallback.invoke(
            mapOf(
                "sender" to mapOf(
                    "name" to "Alice",
                    "type" to "player",
                    "uuid" to "1234",
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

        hostServices.eventCallback.invoke(mapOf("playerName" to "Bob"))
        assertEquals(listOf("enabled", "join:Bob"), hostServices.logMessages)

        plugin.close()
        assertEquals(listOf("enabled", "join:Bob", "disabled"), hostServices.logMessages)
    }

    @Test
    fun supportsModernBundleSyntaxUsedByRealPlugins() {
        val bundleDir = tempDir.resolve("modern")
        Files.createDirectories(bundleDir)
        Files.writeString(
            bundleDir.resolve("main.js"),
            """
                const maybe = { getMessage() { return "hello"; } };
                const query = { page: "1" };
                const value = maybe.getMessage?.() ?? "fallback";
                const normalized = "https://example.com///".replace(/\/+$/, "");
                const entries = Object.entries(query).sort(([left], [right]) => left.localeCompare(right));
                module.exports = {
                  default: {
                    name: "Modern",
                    onEnable(context) {
                      context.logger.info(value + ":" + normalized + ":" + entries[0][0]);
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
                id = "modern",
                name = "Modern",
                version = "1.0.0",
                engine = "js",
                main = "main.js",
                apiVersion = "1.0",
            ),
        )
        val hostServices = FakeHostServices(tempDir.resolve("modern-data"))
        val plugin = JsLanguageRuntime().load(bundle, hostServices)

        plugin.enable()

        assertEquals(listOf("hello:https://example.com:page"), hostServices.logMessages)
        plugin.close()
    }

    @Test
    fun supportsJavaStaticMethodInterop() {
        val bundleDir = tempDir.resolve("java-static")
        Files.createDirectories(bundleDir)
        Files.writeString(
            bundleDir.resolve("main.js"),
            """
                module.exports = {
                  default: {
                    name: "Java Static",
                    onEnable(context) {
                      const Duration = context.javaInterop.type("java.time.Duration");
                      const URI = context.javaInterop.type("java.net.URI");
                      const duration = Duration.ofMillis(2500);
                      const uri = URI.create("https://example.com/demo");
                      context.logger.info(String(duration.toMillis()) + ":" + uri.getHost());
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
                id = "java-static",
                name = "Java Static",
                version = "1.0.0",
                engine = "js",
                main = "main.js",
                apiVersion = "1.0",
            ),
        )
        val hostServices = FakeHostServices(tempDir.resolve("java-static-data"))
        val plugin = JsLanguageRuntime().load(bundle, hostServices)

        plugin.enable()

        assertEquals(listOf("2500:example.com"), hostServices.logMessages)
        plugin.close()
    }

    @Test
    fun reportsSyntaxErrorsAgainstTheOriginalBundleFile() {
        val bundleDir = tempDir.resolve("broken")
        Files.createDirectories(bundleDir)
        Files.writeString(
            bundleDir.resolve("main.js"),
            """
                module.exports = {
                  const broken = ;
                };
            """.trimIndent(),
        )

        val bundle = ScriptBundle(
            bundleDirectory = bundleDir,
            manifestPath = bundleDir.resolve("lapis-plugin.json"),
            mainFile = bundleDir.resolve("main.js"),
            manifest = BundleManifest(
                id = "broken",
                name = "Broken",
                version = "1.0.0",
                engine = "js",
                main = "main.js",
                apiVersion = "1.0",
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            JsLanguageRuntime().load(bundle, FakeHostServices(tempDir.resolve("data")))
        }

        assertTrue(error.message!!.contains("Failed to parse JavaScript bundle"))
        assertTrue(error.message!!.contains("main.js:2"))
        assertTrue(error.message!!.contains("const broken = ;"))
        assertTrue(error.message!!.contains("^"))
    }

    private class FakeHostServices(
        private val dataRoot: Path,
    ) : HostServices {
        val configValues = linkedMapOf<String, Any?>()
        val logMessages = mutableListOf<String>()
        val sentMessages = mutableListOf<String>()
        lateinit var commandCallback: Callback
        lateinit var eventCallback: Callback

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

        override fun close() {
        }
    }
}
