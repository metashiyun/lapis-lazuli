package dev.lapislazuli.runtime.bukkit

import dev.lapislazuli.runtime.core.bundle.ScriptBundle
import dev.lapislazuli.runtime.core.host.AppDescriptor
import dev.lapislazuli.runtime.core.host.Callback
import dev.lapislazuli.runtime.core.host.ConfigStore
import dev.lapislazuli.runtime.core.host.DataDirectory
import dev.lapislazuli.runtime.core.host.HostBlock
import dev.lapislazuli.runtime.core.host.HostEntity
import dev.lapislazuli.runtime.core.host.HostInventory
import dev.lapislazuli.runtime.core.host.HostItem
import dev.lapislazuli.runtime.core.host.HostItemSpec
import dev.lapislazuli.runtime.core.host.HostLocation
import dev.lapislazuli.runtime.core.host.HostPlayer
import dev.lapislazuli.runtime.core.host.HostServices
import dev.lapislazuli.runtime.core.host.HostWorld
import dev.lapislazuli.runtime.core.host.KeyValueStore
import dev.lapislazuli.runtime.core.host.Registration
import dev.lapislazuli.runtime.core.host.RuntimeLogger
import dev.lapislazuli.runtime.core.host.TaskHandle
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.command.SimpleCommandMap
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataHolder
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.lang.reflect.Field
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.ArrayDeque
import java.util.UUID
import java.util.logging.Level
import kotlin.math.floor

internal class BukkitHostServices(
    private val plugin: JavaPlugin,
    private val bundle: ScriptBundle,
) : HostServices {
    private val logger: RuntimeLogger = BundleLogger(plugin, bundle.manifest.id)
    private val commandMap: SimpleCommandMap = resolveCommandMap()
    private val configFile: File = bundle.bundleDirectory.resolve("config.yml").toFile()
    private val storageFile: File = bundle.bundleDirectory.resolve("storage.yml").toFile()
    private val dataDirectory: Path = bundle.bundleDirectory.resolve("data")
    private val registrations = ArrayDeque<AutoCloseable>()
    private val shutdownCallbacks = ArrayDeque<Callback>()
    private var configuration: YamlConfiguration
    private var storageConfiguration: YamlConfiguration

    init {
        Files.createDirectories(dataDirectory)
        ensureFile(configFile.toPath())
        ensureFile(storageFile.toPath())
        configuration = YamlConfiguration.loadConfiguration(configFile)
        storageConfiguration = YamlConfiguration.loadConfiguration(storageFile)
    }

    override fun app(): AppDescriptor =
        AppDescriptor(
            id = bundle.manifest.id,
            name = bundle.manifest.name,
            version = bundle.manifest.version,
            engine = bundle.manifest.engine,
            apiVersion = bundle.manifest.apiVersion,
            backend = "bukkit",
            runtime = "lapis-lazuli-bukkit",
        )

    override fun logger(): RuntimeLogger = logger

    override fun onShutdown(handler: Callback): Registration {
        shutdownCallbacks.addFirst(handler)
        val registration = Registration { shutdownCallbacks.remove(handler) }
        registrations.addFirst(registration)
        return registration
    }

    override fun registerCommand(
        name: String,
        description: String,
        usage: String,
        aliases: List<String>,
        permission: String?,
        execute: Callback,
    ): Registration {
        val command = ScriptCommand(name, description, usage, aliases, permission, execute)
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
        return registerEvent(binding.eventType) { event ->
            executeEventCallback(binding.payloadFactory(this, event), handler)
        }
    }

    override fun registerJavaEvent(eventClassName: String, handler: Callback): Registration {
        val eventType = resolveEventType(eventClassName)
        return registerEvent(eventType) { event ->
            executeEventCallback(event, handler)
        }
    }

    private fun registerEvent(
        eventType: Class<out Event>,
        callback: (Event) -> Unit,
    ): Registration {
        val listener = object : Listener {}
        val executor = EventExecutor { _, event -> callback(event) }

        plugin.server.pluginManager.registerEvent(
            eventType,
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
        return trackTaskHandle(TaskHandle { bukkitTask.cancel() })
    }

    override fun runLater(delayTicks: Long, task: Callback): TaskHandle {
        val bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable { invokeTask(task) }, delayTicks)
        return trackTaskHandle(TaskHandle { bukkitTask.cancel() })
    }

    override fun runTimer(delayTicks: Long, intervalTicks: Long, task: Callback): TaskHandle {
        val bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { invokeTask(task) }, delayTicks, intervalTicks)
        return trackTaskHandle(TaskHandle { bukkitTask.cancel() })
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

    override fun storage(): KeyValueStore =
        object : KeyValueStore {
            override fun get(path: String): Any? = storageConfiguration.get(path)

            override fun set(path: String, value: Any?) {
                storageConfiguration.set(path, value)
            }

            override fun delete(path: String) {
                storageConfiguration.set(path, null)
            }

            override fun save() {
                storageConfiguration.save(storageFile)
            }

            override fun reload() {
                storageConfiguration = YamlConfiguration.loadConfiguration(storageFile)
            }

            override fun keys(): List<String> = storageConfiguration.getKeys(true).toList()
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

    override fun onlinePlayers(): List<HostPlayer> = plugin.server.onlinePlayers.map(::wrapPlayer)

    override fun findPlayer(query: String): HostPlayer? {
        val byName = plugin.server.getPlayerExact(query) ?: plugin.server.getPlayer(query)
        if (byName != null) {
            return wrapPlayer(byName)
        }

        val uuid = runCatching { UUID.fromString(query) }.getOrNull() ?: return null
        return plugin.server.getPlayer(uuid)?.let(::wrapPlayer)
    }

    override fun worlds(): List<HostWorld> = plugin.server.worlds.map(::wrapWorld)

    override fun findWorld(name: String): HostWorld? = plugin.server.getWorld(name)?.let(::wrapWorld)

    override fun findEntity(id: String): HostEntity? {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        return plugin.server.worlds
            .asSequence()
            .flatMap { it.entities.asSequence() }
            .firstOrNull { it.uniqueId == uuid }
            ?.let(::wrapEntity)
    }

    override fun spawnEntity(worldName: String, entityType: String, location: HostLocation): HostEntity {
        val world = plugin.server.getWorld(worldName) ?: error("Unknown world \"$worldName\".")
        val type = runCatching { EntityType.valueOf(normalizeEnumKey(entityType)) }
            .getOrElse { throw IllegalArgumentException("Unsupported entity type \"$entityType\".", it) }
        val spawned = world.spawnEntity(toBukkitLocation(location, world), type)
        return wrapEntity(spawned)
    }

    override fun createItem(spec: HostItemSpec): HostItem {
        val material = resolveMaterial(spec.type)
        val itemStack = ItemStack(material, spec.amount.coerceAtLeast(1))
        val meta = itemStack.itemMeta
        if (meta != null) {
            applyItemSpec(meta, spec)
            itemStack.itemMeta = meta
        }
        spec.enchantments.forEach { (key, level) ->
            resolveEnchantment(key)?.let { enchantment ->
                itemStack.addUnsafeEnchantment(enchantment, level)
            }
        }
        return wrapItem(itemStack)
    }

    override fun createInventory(id: String?, title: String, size: Int): HostInventory {
        require(size in 9..54 && size % 9 == 0) {
            "Inventory size must be a multiple of 9 between 9 and 54."
        }
        val holder = LapisInventoryHolder(id, title)
        val inventory = Bukkit.createInventory(holder, size, title)
        holder.bind(inventory)
        return wrapInventory(inventory)
    }

    override fun javaType(className: String): Class<*> =
        Class.forName(className, true, plugin.javaClass.classLoader)

    override fun serverHandle(): Any = plugin.server

    override fun pluginHandle(): Any = plugin

    override fun consoleSenderHandle(): Any = plugin.server.consoleSender

    override fun dispatchConsoleCommand(command: String): Boolean =
        plugin.server.dispatchCommand(plugin.server.consoleSender, command)

    override fun broadcastMessage(message: String): Int = plugin.server.broadcastMessage(message)

    override fun close() {
        while (shutdownCallbacks.isNotEmpty()) {
            runCatching { shutdownCallbacks.removeFirst().invoke(null) }
                .onFailure { error -> logger.error("Shutdown callback failed for bundle ${bundle.manifest.id}.", error) }
        }

        while (registrations.isNotEmpty()) {
            runCatching { registrations.removeFirst().close() }
                .onFailure { error -> logger.error("Failed to clean up bundle resources.", error) }
        }
    }

    internal fun wrapPlayer(player: Player): HostPlayer = BukkitPlayerHandle(player)

    internal fun wrapWorld(world: World): HostWorld = BukkitWorldHandle(world)

    internal fun wrapEntity(entity: Entity): HostEntity = BukkitEntityHandle(entity)

    internal fun wrapBlock(block: Block): HostBlock = BukkitBlockHandle(block)

    internal fun wrapItem(itemStack: ItemStack): HostItem = BukkitItemHandle(itemStack)

    internal fun wrapInventory(inventory: Inventory): HostInventory = BukkitInventoryHandle(inventory)

    internal fun toHostLocation(location: Location): HostLocation =
        HostLocation(
            world = location.world?.name,
            x = location.x,
            y = location.y,
            z = location.z,
            yaw = location.yaw,
            pitch = location.pitch,
        )

    internal fun cancellablePayload(
        type: String,
        cancelled: () -> Boolean,
        setCancelled: (Boolean) -> Unit,
        values: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "type" to type,
            "cancelled" to cancelled(),
            "cancel" to Callback {
                setCancelled(true)
                null
            },
            "uncancel" to Callback {
                setCancelled(false)
                null
            },
        ).apply { putAll(values) }

    private fun applyItemSpec(meta: ItemMeta, spec: HostItemSpec) {
        meta.setDisplayName(spec.displayName)
        meta.lore = spec.lore.takeIf(List<String>::isNotEmpty)
    }

    private fun ensureFile(path: Path) {
        if (!Files.exists(path)) {
            path.parent?.let(Files::createDirectories)
            Files.writeString(path, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }
    }

    private fun trackTaskHandle(handle: TaskHandle): TaskHandle {
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

    private fun executeEventCallback(payload: Any?, handler: Callback) {
        runCatching { handler.invoke(payload) }
            .onFailure { error -> logger.error("Event callback failed for bundle ${bundle.manifest.id}.", error) }
    }

    private fun resolveEventType(eventClassName: String): Class<out Event> {
        val rawClass = Class.forName(eventClassName, true, plugin.javaClass.classLoader)
        require(Event::class.java.isAssignableFrom(rawClass)) {
            "Class $eventClassName is not a Bukkit event type."
        }

        @Suppress("UNCHECKED_CAST")
        return rawClass as Class<out Event>
    }

    private fun resolveMaterial(type: String): Material =
        runCatching { Material.valueOf(normalizeEnumKey(type)) }
            .getOrElse { throw IllegalArgumentException("Unsupported material \"$type\".", it) }

    private fun resolveEnchantment(key: String): Enchantment? =
        Enchantment.getByName(normalizeEnumKey(key))

    private fun normalizeEnumKey(value: String): String =
        value.trim().replace('-', '_').replace(' ', '_').uppercase()

    private fun toBukkitLocation(location: HostLocation, world: World? = null): Location {
        val resolvedWorld = world ?: location.world?.let(plugin.server::getWorld)
            ?: error("Location requires a world.")
        return Location(
            resolvedWorld,
            location.x,
            location.y,
            location.z,
            location.yaw,
            location.pitch,
        )
    }

    private fun itemTitle(itemMeta: ItemMeta): String? =
        invokeNoArg(itemMeta, "displayName")?.let(::toMessageString)
            ?: invokeNoArg(itemMeta, "getDisplayName")?.toString()

    private fun itemLore(itemMeta: ItemMeta): List<String> =
        (invokeNoArg(itemMeta, "lore") as? List<*>)?.mapNotNull(::toMessageString)
            ?: (invokeNoArg(itemMeta, "getLore") as? List<*>)?.mapNotNull { it?.toString() }
            ?: emptyList()

    private fun entityName(entity: Entity): String? =
        invokeNoArg(entity, "customName")?.let(::toMessageString)
            ?: invokeNoArg(entity, "getCustomName")?.toString()

    private fun inventoryId(inventory: Inventory): String? =
        (inventory.holder as? LapisInventoryHolder)?.id

    private fun inventoryTitle(inventory: Inventory): String =
        (inventory.holder as? LapisInventoryHolder)?.title ?: inventory.type.name

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

    private fun yamlStringStore(
        read: () -> String?,
        write: (String?) -> Unit,
    ): KeyValueStore =
        object : KeyValueStore {
            override fun get(path: String): Any? = load().get(path)

            override fun set(path: String, value: Any?) {
                val config = load()
                config.set(path, value)
                save(config)
            }

            override fun delete(path: String) {
                val config = load()
                config.set(path, null)
                save(config)
            }

            override fun save() {
            }

            override fun reload() {
            }

            override fun keys(): List<String> = load().getKeys(true).toList()

            private fun load(): YamlConfiguration {
                val yaml = YamlConfiguration()
                read()?.takeIf(String::isNotBlank)?.let(yaml::loadFromString)
                return yaml
            }

            private fun save(config: YamlConfiguration) {
                write(
                    config.saveToString().takeIf(String::isNotBlank),
                )
            }
        }

    private fun persistentHolderStore(holder: PersistentDataHolder): KeyValueStore {
        val key = NamespacedKey(plugin, "${bundle.manifest.id}-store")
        return yamlStringStore(
            read = {
                holder.persistentDataContainer.get(key, PersistentDataType.STRING)
            },
            write = { value ->
                if (value == null) {
                    holder.persistentDataContainer.remove(key)
                } else {
                    holder.persistentDataContainer.set(key, PersistentDataType.STRING, value)
                }
            },
        )
    }

    private fun itemStore(itemStack: ItemStack): KeyValueStore =
        yamlStringStore(
            read = {
                itemStack.itemMeta?.persistentDataContainer?.get(itemDataKey(), PersistentDataType.STRING)
            },
            write = fun(value: String?) {
                val meta = itemStack.itemMeta ?: return
                if (value == null) {
                    meta.persistentDataContainer.remove(itemDataKey())
                } else {
                    meta.persistentDataContainer.set(itemDataKey(), PersistentDataType.STRING, value)
                }
                itemStack.itemMeta = meta
            },
        )

    private fun itemDataKey(): NamespacedKey = NamespacedKey(plugin, "${bundle.manifest.id}-item-store")

    private fun invokeNoArg(target: Any, methodName: String): Any? =
        runCatching { target.javaClass.getMethod(methodName).invoke(target) }.getOrNull()

    private fun toMessageString(value: Any?): String? {
        if (value == null) {
            return null
        }

        if (value is CharSequence) {
            return value.toString()
        }

        val componentClass = runCatching {
            Class.forName("net.kyori.adventure.text.Component", true, value.javaClass.classLoader)
        }.getOrNull() ?: return value.toString()

        if (!componentClass.isInstance(value)) {
            return value.toString()
        }

        val serializerClass = runCatching {
            Class.forName(
                "net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer",
                true,
                value.javaClass.classLoader,
            )
        }.getOrNull() ?: return value.toString()

        val serializer = runCatching { serializerClass.getMethod("plainText").invoke(null) }.getOrNull()
            ?: return value.toString()
        return runCatching {
            serializerClass.getMethod("serialize", componentClass).invoke(serializer, value) as? String
        }.getOrDefault(value.toString())
    }

    private inner class BundleLogger(
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

    private inner class ScriptCommand(
        name: String,
        description: String,
        usage: String,
        aliases: List<String>,
        permission: String?,
        private val execute: Callback,
    ) : Command(
        name,
        description.ifBlank { "" },
        usage.ifBlank { "/$name" },
        aliases,
    ) {
        init {
            this.permission = permission?.ifBlank { null }
        }

        override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean =
            runCatching {
                when (val result = execute.invoke(createPayload(sender, commandLabel, args.toList(), name))) {
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
            name: String,
        ): Map<String, Any?> {
            val player = sender as? Player
            return mapOf(
                "sender" to mapOf(
                    "name" to sender.name,
                    "type" to when (sender) {
                        is Player -> "player"
                        is ConsoleCommandSender -> "console"
                        else -> "other"
                    },
                    "id" to player?.uniqueId?.toString(),
                    "player" to player?.let(::wrapPlayer),
                    "sendMessage" to Callback { payload ->
                        sender.sendMessage(payload?.toString() ?: "")
                        null
                    },
                    "hasPermission" to Callback { payload ->
                        sender.hasPermission(payload?.toString() ?: "")
                    },
                    "unsafe" to mapOf("handle" to sender),
                ),
                "args" to args,
                "label" to commandLabel,
                "command" to name,
            )
        }
    }

    private inner class BukkitPlayerHandle(
        internal val player: Player,
    ) : HostPlayer {
        override fun id(): String = player.uniqueId.toString()

        override fun name(): String = player.name

        override fun worldName(): String = player.world.name

        override fun location(): HostLocation = toHostLocation(player.location)

        override fun sendMessage(message: String) {
            player.sendMessage(message)
        }

        override fun sendActionBar(message: String) {
            runCatching {
                player.javaClass.getMethod("sendActionBar", String::class.java).invoke(player, message)
            }.getOrElse {
                player.sendMessage(message)
            }
        }

        override fun showTitle(title: String, subtitle: String, fadeInTicks: Int, stayTicks: Int, fadeOutTicks: Int) {
            player.sendTitle(title, subtitle, fadeInTicks, stayTicks, fadeOutTicks)
        }

        override fun hasPermission(permission: String): Boolean = player.hasPermission(permission)

        override fun teleport(location: HostLocation): Boolean = player.teleport(toBukkitLocation(location))

        override fun inventory(): HostInventory = wrapInventory(player.inventory)

        override fun tags(): KeyValueStore = persistentHolderStore(player)

        override fun backendHandle(): Any = player
    }

    private inner class BukkitWorldHandle(
        private val world: World,
    ) : HostWorld {
        override fun name(): String = world.name

        override fun environment(): String = world.environment.name.lowercase()

        override fun players(): List<HostPlayer> = world.players.map(::wrapPlayer)

        override fun entities(): List<HostEntity> = world.entities.map(::wrapEntity)

        override fun spawnLocation(): HostLocation = toHostLocation(world.spawnLocation)

        override fun time(): Long = world.time

        override fun setTime(time: Long) {
            world.time = time
        }

        override fun storming(): Boolean = world.hasStorm()

        override fun setStorming(storming: Boolean) {
            world.setStorm(storming)
        }

        override fun blockAt(location: HostLocation): HostBlock {
            val bukkitLocation = toBukkitLocation(location, world)
            val block = world.getBlockAt(floor(bukkitLocation.x).toInt(), floor(bukkitLocation.y).toInt(), floor(bukkitLocation.z).toInt())
            return wrapBlock(block)
        }

        override fun backendHandle(): Any = world
    }

    private inner class BukkitEntityHandle(
        private val entity: Entity,
    ) : HostEntity {
        override fun id(): String = entity.uniqueId.toString()

        override fun type(): String = entity.type.name.lowercase()

        override fun customName(): String? = entityName(entity)

        override fun worldName(): String = entity.world.name

        override fun location(): HostLocation = toHostLocation(entity.location)

        override fun teleport(location: HostLocation): Boolean = entity.teleport(toBukkitLocation(location))

        override fun remove() {
            entity.remove()
        }

        override fun tags(): KeyValueStore? =
            (entity as? PersistentDataHolder)?.let(::persistentHolderStore)

        override fun backendHandle(): Any = entity
    }

    private inner class BukkitBlockHandle(
        private val block: Block,
    ) : HostBlock {
        override fun type(): String = block.type.name.lowercase()

        override fun location(): HostLocation = toHostLocation(block.location)

        override fun worldName(): String = block.world.name

        override fun setType(type: String): Boolean {
            block.type = resolveMaterial(type)
            return true
        }

        override fun backendHandle(): Any = block
    }

    private inner class BukkitItemHandle(
        internal val itemStack: ItemStack,
    ) : HostItem {
        override fun type(): String = itemStack.type.name.lowercase()

        override fun amount(): Int = itemStack.amount

        override fun setAmount(amount: Int) {
            itemStack.amount = amount.coerceAtLeast(1)
        }

        override fun displayName(): String? = itemStack.itemMeta?.let(::itemTitle)

        override fun setDisplayName(displayName: String?) {
            val meta = itemStack.itemMeta ?: return
            meta.setDisplayName(displayName)
            itemStack.itemMeta = meta
        }

        override fun lore(): List<String> = itemStack.itemMeta?.let(::itemLore) ?: emptyList()

        override fun setLore(lines: List<String>) {
            val meta = itemStack.itemMeta ?: return
            meta.lore = lines.takeIf(List<String>::isNotEmpty)
            itemStack.itemMeta = meta
        }

        override fun enchantments(): Map<String, Int> =
            itemStack.enchantments.entries.associate { (enchantment, level) ->
                enchantment.key.key to level
            }

        override fun setEnchantment(key: String, level: Int) {
            resolveEnchantment(key)?.let { enchantment ->
                itemStack.addUnsafeEnchantment(enchantment, level)
            }
        }

        override fun removeEnchantment(key: String) {
            resolveEnchantment(key)?.let(itemStack::removeEnchantment)
        }

        override fun cloneItem(): HostItem = wrapItem(itemStack.clone())

        override fun tags(): KeyValueStore? = itemStore(itemStack)

        override fun backendHandle(): Any = itemStack
    }

    private inner class BukkitInventoryHandle(
        private val inventory: Inventory,
    ) : HostInventory {
        override fun id(): String? = inventoryId(inventory)

        override fun title(): String = inventoryTitle(inventory)

        override fun size(): Int = inventory.size

        override fun getItem(slot: Int): HostItem? = inventory.getItem(slot)?.let(::wrapItem)

        override fun setItem(slot: Int, item: HostItem?) {
            inventory.setItem(slot, (item as? BukkitItemHandle)?.itemStack)
        }

        override fun addItem(item: HostItem) {
            inventory.addItem((item as BukkitItemHandle).itemStack)
        }

        override fun clear() {
            inventory.clear()
        }

        override fun clearSlot(slot: Int) {
            inventory.setItem(slot, null)
        }

        override fun open(player: HostPlayer) {
            (player as BukkitPlayerHandle).player.openInventory(inventory)
        }

        override fun backendHandle(): Any = inventory
    }

    private inner class LapisInventoryHolder(
        val id: String?,
        val title: String,
    ) : InventoryHolder {
        private var inventory: Inventory? = null

        fun bind(inventory: Inventory) {
            this.inventory = inventory
        }

        override fun getInventory(): Inventory = requireNotNull(inventory) {
            "Inventory holder not bound."
        }
    }
}
