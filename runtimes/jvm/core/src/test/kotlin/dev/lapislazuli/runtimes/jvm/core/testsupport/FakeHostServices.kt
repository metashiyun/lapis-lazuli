package dev.lapislazuli.runtimes.jvm.core.testsupport

import dev.lapislazuli.runtimes.jvm.core.host.AppDescriptor
import dev.lapislazuli.runtimes.jvm.core.host.Callback
import dev.lapislazuli.runtimes.jvm.core.host.ConfigStore
import dev.lapislazuli.runtimes.jvm.core.host.DataDirectory
import dev.lapislazuli.runtimes.jvm.core.host.HostBlock
import dev.lapislazuli.runtimes.jvm.core.host.HostBossBar
import dev.lapislazuli.runtimes.jvm.core.host.HostEntity
import dev.lapislazuli.runtimes.jvm.core.host.HostInventory
import dev.lapislazuli.runtimes.jvm.core.host.HostItem
import dev.lapislazuli.runtimes.jvm.core.host.HostItemSpec
import dev.lapislazuli.runtimes.jvm.core.host.HostLocation
import dev.lapislazuli.runtimes.jvm.core.host.HostPlayer
import dev.lapislazuli.runtimes.jvm.core.host.HostRecipeSpec
import dev.lapislazuli.runtimes.jvm.core.host.HostScoreboard
import dev.lapislazuli.runtimes.jvm.core.host.HostServices
import dev.lapislazuli.runtimes.jvm.core.host.HostWorld
import dev.lapislazuli.runtimes.jvm.core.host.KeyValueStore
import dev.lapislazuli.runtimes.jvm.core.host.Registration
import dev.lapislazuli.runtimes.jvm.core.host.RuntimeLogger
import dev.lapislazuli.runtimes.jvm.core.host.TaskHandle
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class FakeHostServices(
    private val dataRoot: Path,
) : HostServices {
    val configValues = linkedMapOf<String, Any?>()
    val storageValues = linkedMapOf<String, Any?>()
    val logMessages = mutableListOf<String>()
    val sentMessages = mutableListOf<String>()
    val dispatchedCommands = mutableListOf<String>()
    val broadcastMessages = mutableListOf<String>()
    val playedSounds = mutableListOf<PlayedSound>()
    val spawnedParticles = mutableListOf<SpawnedParticle>()
    val appliedPotionEffects = linkedMapOf<String, MutableMap<String, AppliedPotionEffect>>()
    val registeredRecipes = mutableListOf<HostRecipeSpec>()
    val bossBars = mutableListOf<FakeBossBarHandle>()
    val scoreboards = mutableListOf<FakeScoreboardHandle>()
    val eventCallbacks = linkedMapOf<String, Callback>()
    val shutdownCallbacks = mutableListOf<Callback>()
    lateinit var commandCallback: Callback
    lateinit var javaEventCallback: Callback
    var javaEventClassName: String? = null
    val player = FakePlayerHandle(this, "Alice", UUID.fromString("00000000-0000-0000-0000-000000001234"))
    val world = FakeWorldHandle(this, "world")
    val entities = linkedMapOf<String, FakeEntityHandle>()

    init {
        Files.createDirectories(dataRoot)
    }

    override fun app(): AppDescriptor =
        AppDescriptor(
            id = "hello",
            name = "Hello",
            version = "1.0.0",
            engine = "js",
            apiVersion = "1.0",
            backend = "fake-bukkit",
            runtime = "fake-runtime",
        )

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

    override fun onShutdown(handler: Callback): Registration {
        shutdownCallbacks += handler
        return Registration { shutdownCallbacks.remove(handler) }
    }

    override fun registerCommand(
        name: String,
        description: String,
        usage: String,
        aliases: List<String>,
        permission: String?,
        execute: Callback,
    ): Registration {
        commandCallback = execute
        return Registration {}
    }

    override fun registerEvent(eventKey: String, handler: Callback): Registration {
        eventCallbacks[eventKey] = handler
        if (eventKey == "server.ready") {
            handler.invoke(serverReadyPayload())
        }
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

    override fun runLater(delayTicks: Long, task: Callback): TaskHandle {
        task.invoke(null)
        return TaskHandle {}
    }

    override fun runTimer(delayTicks: Long, intervalTicks: Long, task: Callback): TaskHandle {
        task.invoke(null)
        return TaskHandle {}
    }

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

    override fun storage(): KeyValueStore = mapStore(storageValues)

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
                Files.createDirectories(
                    if (relativePath.isBlank()) dataRoot else dataRoot.resolve(relativePath),
                )
            }
        }

    override fun onlinePlayers(): List<HostPlayer> = listOf(player)

    override fun findPlayer(query: String): HostPlayer? =
        when (query) {
            player.name(), player.id() -> player
            else -> null
        }

    override fun worlds(): List<HostWorld> = listOf(world)

    override fun findWorld(name: String): HostWorld? = world.takeIf { it.name() == name }

    override fun findEntity(id: String): HostEntity? = entities[id]

    override fun spawnEntity(worldName: String, entityType: String, location: HostLocation): HostEntity {
        val entity = FakeEntityHandle(this, UUID.randomUUID(), entityType.lowercase(), location.copy(world = worldName))
        entities[entity.id()] = entity
        return entity
    }

    override fun createItem(spec: HostItemSpec): HostItem =
        FakeItemHandle(
            type = spec.type.lowercase(),
            amountValue = spec.amount,
            displayNameValue = spec.displayName,
            loreValue = spec.lore.toMutableList(),
            enchantmentsValue = spec.enchantments.toMutableMap(),
        )

    override fun createInventory(id: String?, title: String, size: Int): HostInventory =
        FakeInventoryHandle(id, title, size)

    override fun playSound(location: HostLocation, sound: String, volume: Float, pitch: Float) {
        playedSounds += PlayedSound(
            target = "location:${location.world}:${location.x},${location.y},${location.z}",
            sound = sound,
            volume = volume,
            pitch = pitch,
        )
    }

    override fun playSound(player: HostPlayer, sound: String, volume: Float, pitch: Float) {
        playedSounds += PlayedSound(
            target = "player:${player.name()}",
            sound = sound,
            volume = volume,
            pitch = pitch,
        )
    }

    override fun spawnParticle(
        location: HostLocation,
        particle: String,
        count: Int,
        offsetX: Double,
        offsetY: Double,
        offsetZ: Double,
        extra: Double,
        players: List<HostPlayer>,
    ) {
        spawnedParticles += SpawnedParticle(
            particle = particle,
            location = location,
            count = count,
            offsetX = offsetX,
            offsetY = offsetY,
            offsetZ = offsetZ,
            extra = extra,
            players = players.map(HostPlayer::name),
        )
    }

    override fun applyPotionEffect(
        player: HostPlayer,
        effect: String,
        durationTicks: Int,
        amplifier: Int,
        ambient: Boolean,
        particles: Boolean,
        icon: Boolean,
    ): Boolean {
        val effects = appliedPotionEffects.getOrPut(player.id()) { linkedMapOf() }
        effects[effect] = AppliedPotionEffect(effect, durationTicks, amplifier, ambient, particles, icon)
        return true
    }

    override fun clearPotionEffect(player: HostPlayer, effect: String?) {
        val effects = appliedPotionEffects[player.id()] ?: return
        if (effect == null) {
            effects.clear()
        } else {
            effects.remove(effect)
        }
    }

    override fun registerRecipe(spec: HostRecipeSpec): Registration {
        registeredRecipes += spec
        return Registration { registeredRecipes.remove(spec) }
    }

    override fun createBossBar(id: String?, title: String, color: String, style: String): HostBossBar =
        FakeBossBarHandle(id, title, color, style).also(bossBars::add)

    override fun createScoreboard(id: String?, title: String): HostScoreboard =
        FakeScoreboardHandle(id, title).also(scoreboards::add)

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
        shutdownCallbacks.toList().forEach { it.invoke(null) }
    }

    fun playerJoinPayload(name: String = "Bob"): Map<String, Any?> =
        mapOf(
            "type" to "player.join",
            "player" to FakePlayerHandle(this, name, UUID.fromString("00000000-0000-0000-0000-00000000b0b0")),
            "joinMessage" to "$name joined",
            "setJoinMessage" to Callback { null },
        )

    fun serverReadyPayload(reload: Boolean = false): Map<String, Any?> =
        mapOf(
            "type" to "server.ready",
            "reload" to reload,
        )

    private fun mapStore(backing: MutableMap<String, Any?>): KeyValueStore =
        object : KeyValueStore {
            override fun get(path: String): Any? = backing[path]

            override fun set(path: String, value: Any?) {
                backing[path] = value
            }

            override fun delete(path: String) {
                backing.remove(path)
            }

            override fun save() {
            }

            override fun reload() {
            }

            override fun keys(): List<String> = backing.keys.toList()
        }

    data class PlayedSound(
        val target: String,
        val sound: String,
        val volume: Float,
        val pitch: Float,
    )

    data class SpawnedParticle(
        val particle: String,
        val location: HostLocation,
        val count: Int,
        val offsetX: Double,
        val offsetY: Double,
        val offsetZ: Double,
        val extra: Double,
        val players: List<String>,
    )

    data class AppliedPotionEffect(
        val effect: String,
        val durationTicks: Int,
        val amplifier: Int,
        val ambient: Boolean,
        val particles: Boolean,
        val icon: Boolean,
    )

    class FakePlayerHandle(
        private val host: FakeHostServices,
        private val playerName: String,
        private val uuid: UUID,
    ) : HostPlayer {
        private val inventory = FakeInventoryHandle("player-$playerName", "$playerName Inventory", 36)
        private val tagStore = linkedMapOf<String, Any?>()
        private var currentLocation = HostLocation("world", 0.0, 64.0, 0.0)

        override fun id(): String = uuid.toString()

        override fun name(): String = playerName

        override fun worldName(): String = currentLocation.world ?: "world"

        override fun location(): HostLocation = currentLocation

        override fun sendMessage(message: String) {
            host.sentMessages += message
        }

        override fun sendActionBar(message: String) {
            host.sentMessages += "action:$message"
        }

        override fun showTitle(title: String, subtitle: String, fadeInTicks: Int, stayTicks: Int, fadeOutTicks: Int) {
            host.sentMessages += "title:$title|$subtitle"
        }

        override fun hasPermission(permission: String): Boolean = true

        override fun teleport(location: HostLocation): Boolean {
            currentLocation = location
            return true
        }

        override fun inventory(): HostInventory = inventory

        override fun tags(): KeyValueStore = host.mapStore(tagStore)

        override fun backendHandle(): Any = this
    }

    class FakeWorldHandle(
        private val host: FakeHostServices,
        private val worldName: String,
    ) : HostWorld {
        private var currentTime = 0L
        private var stormingState = false

        override fun name(): String = worldName

        override fun environment(): String = "normal"

        override fun players(): List<HostPlayer> = listOf(host.player)

        override fun entities(): List<HostEntity> = host.entities.values.toList()

        override fun spawnLocation(): HostLocation = HostLocation(worldName, 0.0, 64.0, 0.0)

        override fun time(): Long = currentTime

        override fun setTime(time: Long) {
            currentTime = time
        }

        override fun storming(): Boolean = stormingState

        override fun setStorming(storming: Boolean) {
            stormingState = storming
        }

        override fun blockAt(location: HostLocation): HostBlock =
            FakeBlockHandle(location.copy(world = worldName), "stone")

        override fun backendHandle(): Any = this
    }

    class FakeEntityHandle(
        private val host: FakeHostServices,
        private val uuid: UUID,
        private val entityType: String,
        private var currentLocation: HostLocation,
    ) : HostEntity {
        private val tagStore = linkedMapOf<String, Any?>()

        override fun id(): String = uuid.toString()

        override fun type(): String = entityType

        override fun customName(): String? = null

        override fun worldName(): String = currentLocation.world ?: "world"

        override fun location(): HostLocation = currentLocation

        override fun teleport(location: HostLocation): Boolean {
            currentLocation = location
            return true
        }

        override fun remove() {
            host.entities.remove(id())
        }

        override fun tags(): KeyValueStore = host.mapStore(tagStore)

        override fun backendHandle(): Any = this
    }

    class FakeBlockHandle(
        private var currentLocation: HostLocation,
        private var material: String,
    ) : HostBlock {
        override fun type(): String = material

        override fun location(): HostLocation = currentLocation

        override fun worldName(): String = currentLocation.world ?: "world"

        override fun setType(type: String): Boolean {
            material = type
            return true
        }

        override fun backendHandle(): Any = this
    }

    class FakeItemHandle(
        private var type: String,
        private var amountValue: Int,
        private var displayNameValue: String?,
        private var loreValue: MutableList<String>,
        private var enchantmentsValue: MutableMap<String, Int>,
    ) : HostItem {
        private val tagStore = linkedMapOf<String, Any?>()

        override fun type(): String = type

        override fun amount(): Int = amountValue

        override fun setAmount(amount: Int) {
            amountValue = amount
        }

        override fun displayName(): String? = displayNameValue

        override fun setDisplayName(displayName: String?) {
            displayNameValue = displayName
        }

        override fun lore(): List<String> = loreValue.toList()

        override fun setLore(lines: List<String>) {
            loreValue = lines.toMutableList()
        }

        override fun enchantments(): Map<String, Int> = enchantmentsValue.toMap()

        override fun setEnchantment(key: String, level: Int) {
            enchantmentsValue[key] = level
        }

        override fun removeEnchantment(key: String) {
            enchantmentsValue.remove(key)
        }

        override fun cloneItem(): HostItem =
            FakeItemHandle(type, amountValue, displayNameValue, loreValue.toMutableList(), enchantmentsValue.toMutableMap())

        override fun tags(): KeyValueStore = object : KeyValueStore {
            override fun get(path: String): Any? = tagStore[path]

            override fun set(path: String, value: Any?) {
                tagStore[path] = value
            }

            override fun delete(path: String) {
                tagStore.remove(path)
            }

            override fun save() {
            }

            override fun reload() {
            }

            override fun keys(): List<String> = tagStore.keys.toList()
        }

        override fun backendHandle(): Any = this
    }

    class FakeInventoryHandle(
        private val inventoryId: String?,
        private val inventoryTitle: String,
        private val inventorySize: Int,
    ) : HostInventory {
        private val slots = mutableMapOf<Int, HostItem>()
        var lastOpenedBy: String? = null

        override fun id(): String? = inventoryId

        override fun title(): String = inventoryTitle

        override fun size(): Int = inventorySize

        override fun getItem(slot: Int): HostItem? = slots[slot]

        override fun setItem(slot: Int, item: HostItem?) {
            if (item == null) {
                slots.remove(slot)
            } else {
                slots[slot] = item
            }
        }

        override fun addItem(item: HostItem) {
            val nextSlot = (0 until inventorySize).firstOrNull { !slots.containsKey(it) } ?: return
            slots[nextSlot] = item
        }

        override fun clear() {
            slots.clear()
        }

        override fun clearSlot(slot: Int) {
            slots.remove(slot)
        }

        override fun open(player: HostPlayer) {
            lastOpenedBy = player.name()
        }

        override fun backendHandle(): Any = this
    }

    inner class FakeBossBarHandle(
        private val bossBarId: String?,
        private var currentTitle: String,
        private var currentColor: String,
        private var currentStyle: String,
    ) : HostBossBar {
        private val viewers = linkedMapOf<String, HostPlayer>()
        private var currentProgress = 1.0

        override fun id(): String? = bossBarId

        override fun title(): String = currentTitle

        override fun setTitle(title: String) {
            currentTitle = title
        }

        override fun progress(): Double = currentProgress

        override fun setProgress(progress: Double) {
            currentProgress = progress
        }

        override fun color(): String = currentColor

        override fun setColor(color: String) {
            currentColor = color
        }

        override fun style(): String = currentStyle

        override fun setStyle(style: String) {
            currentStyle = style
        }

        override fun players(): List<HostPlayer> = viewers.values.toList()

        override fun addPlayer(player: HostPlayer) {
            viewers[player.id()] = player
        }

        override fun removePlayer(player: HostPlayer) {
            viewers.remove(player.id())
        }

        override fun removeAllPlayers() {
            viewers.clear()
        }

        override fun backendHandle(): Any = this

        override fun close() {
            removeAllPlayers()
        }
    }

    inner class FakeScoreboardHandle(
        private val scoreboardId: String?,
        private var currentTitle: String,
    ) : HostScoreboard {
        private val entries = linkedMapOf<Int, String>()
        private val viewersById = linkedMapOf<String, HostPlayer>()

        override fun id(): String? = scoreboardId

        override fun title(): String = currentTitle

        override fun setTitle(title: String) {
            currentTitle = title
        }

        override fun setLine(score: Int, text: String) {
            entries[score] = text
        }

        override fun removeLine(score: Int) {
            entries.remove(score)
        }

        override fun clear() {
            entries.clear()
        }

        override fun viewers(): List<HostPlayer> = viewersById.values.toList()

        override fun show(player: HostPlayer) {
            viewersById[player.id()] = player
        }

        override fun hide(player: HostPlayer) {
            viewersById.remove(player.id())
        }

        override fun backendHandle(): Any = this

        override fun close() {
            viewersById.clear()
            entries.clear()
        }

        fun lines(): Map<Int, String> = entries.toMap()
    }
}
