package dev.lapislazuli.runtimes.jvm.core.host

interface HostServices : AutoCloseable {
    fun app(): AppDescriptor

    fun logger(): RuntimeLogger

    @Throws(Exception::class)
    fun onShutdown(handler: Callback): Registration

    @Throws(Exception::class)
    fun registerCommand(
        name: String,
        description: String,
        usage: String,
        aliases: List<String>,
        permission: String?,
        execute: Callback,
    ): Registration

    @Throws(Exception::class)
    fun registerEvent(eventKey: String, handler: Callback): Registration

    @Throws(Exception::class)
    fun registerJavaEvent(eventClassName: String, handler: Callback): Registration

    @Throws(Exception::class)
    fun runNow(task: Callback): TaskHandle

    @Throws(Exception::class)
    fun runLater(delayTicks: Long, task: Callback): TaskHandle

    @Throws(Exception::class)
    fun runTimer(delayTicks: Long, intervalTicks: Long, task: Callback): TaskHandle

    fun config(): ConfigStore

    fun storage(): KeyValueStore

    fun dataDirectory(): DataDirectory

    fun onlinePlayers(): List<HostPlayer>

    fun findPlayer(query: String): HostPlayer?

    fun worlds(): List<HostWorld>

    fun findWorld(name: String): HostWorld?

    fun findEntity(id: String): HostEntity?

    @Throws(Exception::class)
    fun spawnEntity(worldName: String, entityType: String, location: HostLocation): HostEntity

    @Throws(Exception::class)
    fun createItem(spec: HostItemSpec): HostItem

    @Throws(Exception::class)
    fun createInventory(id: String?, title: String, size: Int): HostInventory

    @Throws(Exception::class)
    fun playSound(location: HostLocation, sound: String, volume: Float, pitch: Float)

    @Throws(Exception::class)
    fun playSound(player: HostPlayer, sound: String, volume: Float, pitch: Float)

    @Throws(Exception::class)
    fun spawnParticle(
        location: HostLocation,
        particle: String,
        count: Int,
        offsetX: Double,
        offsetY: Double,
        offsetZ: Double,
        extra: Double,
        players: List<HostPlayer>,
    )

    @Throws(Exception::class)
    fun applyPotionEffect(
        player: HostPlayer,
        effect: String,
        durationTicks: Int,
        amplifier: Int,
        ambient: Boolean,
        particles: Boolean,
        icon: Boolean,
    ): Boolean

    @Throws(Exception::class)
    fun clearPotionEffect(player: HostPlayer, effect: String?)

    @Throws(Exception::class)
    fun registerRecipe(spec: HostRecipeSpec): Registration

    @Throws(Exception::class)
    fun createBossBar(
        id: String?,
        title: String,
        color: String,
        style: String,
    ): HostBossBar

    @Throws(Exception::class)
    fun createScoreboard(id: String?, title: String): HostScoreboard

    @Throws(Exception::class)
    fun javaType(className: String): Class<*>

    fun serverHandle(): Any

    fun pluginHandle(): Any

    fun consoleSenderHandle(): Any

    @Throws(Exception::class)
    fun dispatchConsoleCommand(command: String): Boolean

    @Throws(Exception::class)
    fun broadcastMessage(message: String): Int

    @Throws(Exception::class)
    override fun close()
}
