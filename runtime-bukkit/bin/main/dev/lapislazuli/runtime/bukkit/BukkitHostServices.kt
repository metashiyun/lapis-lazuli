package dev.lapislazuli.runtime.bukkit

import dev.lapislazuli.runtime.core.bundle.ScriptBundle
import dev.lapislazuli.runtime.core.host.Callback
import dev.lapislazuli.runtime.core.host.ConfigStore
import dev.lapislazuli.runtime.core.host.DataDirectory
import dev.lapislazuli.runtime.core.host.HostServices
import dev.lapislazuli.runtime.core.host.Registration
import dev.lapislazuli.runtime.core.host.RuntimeLogger
import dev.lapislazuli.runtime.core.host.TaskHandle
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.command.SimpleCommandMap
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.lang.reflect.Field
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.ArrayDeque
import java.util.logging.Level

internal class BukkitHostServices(
    private val plugin: JavaPlugin,
    private val bundle: ScriptBundle,
) : HostServices {
    private val logger: RuntimeLogger = BundleLogger(plugin, bundle.manifest.id)
    private val commandMap: SimpleCommandMap = resolveCommandMap()
    private val configFile: File = bundle.bundleDirectory.resolve("config.yml").toFile()
    private val dataDirectory: Path = bundle.bundleDirectory.resolve("data")
    private val registrations = ArrayDeque<AutoCloseable>()
    private var configuration: YamlConfiguration

    init {
        Files.createDirectories(dataDirectory)
        if (!configFile.exists()) {
            configFile.parentFile?.mkdirs()
            Files.writeString(
                configFile.toPath(),
                "",
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
        }
        configuration = YamlConfiguration.loadConfiguration(configFile)
    }

    override fun logger(): RuntimeLogger = logger

    override fun registerCommand(
        name: String,
        description: String,
        usage: String,
        aliases: List<String>,
        execute: Callback,
    ): Registration {
        val command = ScriptCommand(name, description, usage, aliases, execute)
        commandMap.register(plugin.name.lowercase(), command)

        val registration = Registration {
            command.unregister(commandMap)
            unregisterKnownCommand(name)
            aliases.forEach(::unregisterKnownCommand)
        }

        registrations.addFirst(registration)
        return registration
    }

    override fun registerEvent(eventKey: String, handler: Callback): Registration {
        val binding = BukkitEventBindings.require(eventKey)
        val listener = object : Listener {}
        val executor = EventExecutor { _, event -> executeEventCallback(binding, handler, event) }

        plugin.server.pluginManager.registerEvent(
            binding.eventType,
            listener,
            EventPriority.NORMAL,
            executor,
            plugin,
            true,
        )

        val registration = Registration { HandlerList.unregisterAll(listener) }
        registrations.addFirst(registration)
        return registration
    }

    override fun runNow(task: Callback): TaskHandle {
        val bukkitTask = Bukkit.getScheduler().runTask(plugin, Runnable { invokeTask(task) })
        return taskHandle(bukkitTask::cancel)
    }

    override fun runLater(delayTicks: Long, task: Callback): TaskHandle {
        val bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable { invokeTask(task) }, delayTicks)
        return taskHandle(bukkitTask::cancel)
    }

    override fun runTimer(delayTicks: Long, intervalTicks: Long, task: Callback): TaskHandle {
        val bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { invokeTask(task) }, delayTicks, intervalTicks)
        return taskHandle(bukkitTask::cancel)
    }

    override fun config(): ConfigStore =
        object : ConfigStore {
            override fun get(path: String): Any? = configuration.get(path)

            override fun set(path: String, value: Any?) {
                configuration.set(path, value)
            }

            override fun save() {
                configuration.save(configFile)
            }

            override fun reload() {
                configuration = YamlConfiguration.loadConfiguration(configFile)
            }

            override fun keys(): List<String> = configuration.getKeys(true).toList()
        }

    override fun dataDirectory(): DataDirectory =
        object : DataDirectory {
            override fun path(): String = dataDirectory.toString()

            override fun resolve(vararg segments: String): String = resolvePath(*segments).toString()

            override fun readText(relativePath: String): String = Files.readString(resolvePath(relativePath))

            override fun writeText(relativePath: String, contents: String) {
                val target = resolvePath(relativePath)
                target.parent?.let(Files::createDirectories)
                Files.writeString(
                    target,
                    contents,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                )
            }

            override fun exists(relativePath: String): Boolean = Files.exists(resolvePath(relativePath))

            override fun mkdirs(relativePath: String) {
                Files.createDirectories(
                    if (relativePath.isBlank()) dataDirectory else resolvePath(relativePath),
                )
            }
        }

    override fun javaType(className: String): Class<*> =
        Class.forName(className, true, plugin.javaClass.classLoader)

    override fun close() {
        while (registrations.isNotEmpty()) {
            runCatching { registrations.removeFirst().close() }
                .onFailure { error -> logger.error("Failed to clean up bundle resources.", error) }
        }
    }

    private fun taskHandle(cancel: () -> Unit): TaskHandle {
        val handle = TaskHandle { cancel() }
        registrations.addFirst(handle)
        return handle
    }

    private fun resolvePath(vararg segments: String): Path {
        val resolved = segments
            .filter(String::isNotBlank)
            .fold(dataDirectory) { path, segment -> path.resolve(segment) }
            .normalize()

        require(resolved.startsWith(dataDirectory.normalize())) {
            "Resolved path escapes the bundle data directory."
        }

        return resolved
    }

    private fun invokeTask(task: Callback) {
        runCatching { task.invoke(null) }
            .onFailure { error -> logger.error("Scheduled task failed for bundle ${bundle.manifest.id}.", error) }
    }

    private fun executeEventCallback(binding: BukkitEventBindings.Binding, handler: Callback, event: Event) {
        runCatching { handler.invoke(binding.payloadFactory(event)) }
            .onFailure { error -> logger.error("Event callback failed for bundle ${bundle.manifest.id}.", error) }
    }

    private fun unregisterKnownCommand(name: String) {
        runCatching {
            val field: Field = commandMap.javaClass.getDeclaredField("knownCommands")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val knownCommands = field.get(commandMap) as MutableMap<String, Command>
            knownCommands.remove(name)
            knownCommands.remove("${plugin.name.lowercase()}:$name")
        }.onFailure { error ->
            logger.error("Failed to unregister command $name.", error)
        }
    }

    private fun resolveCommandMap(): SimpleCommandMap {
        val field = plugin.server.javaClass.getDeclaredField("commandMap")
        field.isAccessible = true
        return field.get(plugin.server) as SimpleCommandMap
    }

    private class BundleLogger(
        private val plugin: JavaPlugin,
        private val bundleId: String,
    ) : RuntimeLogger {
        override fun info(message: String) {
            plugin.logger.info(prefix(message))
        }

        override fun warn(message: String) {
            plugin.logger.warning(prefix(message))
        }

        override fun error(message: String, error: Throwable?) {
            if (error == null) {
                plugin.logger.severe(prefix(message))
                return
            }

            plugin.logger.log(Level.SEVERE, prefix(message), error)
        }

        private fun prefix(message: String): String = "[$bundleId] $message"
    }

    private class ScriptCommand(
        name: String,
        description: String,
        usage: String,
        aliases: List<String>,
        private val execute: Callback,
    ) : Command(
        name,
        description.ifBlank { "" },
        usage.ifBlank { "/$name" },
        aliases,
    ) {
        override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean =
            runCatching {
                when (val result = execute.invoke(createPayload(sender, commandLabel, args.toList()))) {
                    is Boolean -> result
                    is String -> {
                        if (result.isNotBlank()) {
                            sender.sendMessage(result)
                        }
                        true
                    }
                    else -> true
                }
            }.getOrElse {
                sender.sendMessage("An internal error occurred while running this command.")
                false
            }

        private fun createPayload(
            sender: CommandSender,
            commandLabel: String,
            args: List<String>,
        ): Map<String, Any?> =
            mapOf(
                "sender" to mapOf(
                    "name" to sender.name,
                    "type" to when (sender) {
                        is Player -> "player"
                        is ConsoleCommandSender -> "console"
                        else -> "other"
                    },
                    "uuid" to (sender as? Player)?.uniqueId?.toString(),
                    "sendMessage" to Callback { payload ->
                        sender.sendMessage(payload?.toString() ?: "")
                        null
                    },
                ),
                "args" to args,
                "label" to commandLabel,
            )
    }
}
