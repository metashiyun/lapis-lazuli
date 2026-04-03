package dev.lapislazuli.runtime.bukkit

import org.bukkit.event.Event
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.ServerLoadEvent

internal object BukkitEventBindings {
    private val bindings = mapOf(
        "playerJoin" to Binding(PlayerJoinEvent::class.java) { event ->
            val joinEvent = event as PlayerJoinEvent
            mapOf(
                "type" to "playerJoin",
                "playerName" to joinEvent.player.name,
                "playerUuid" to joinEvent.player.uniqueId.toString(),
                "playerHandle" to joinEvent.player,
                "javaEvent" to joinEvent,
                "joinMessage" to extractMessage(joinEvent, "joinMessage", "getJoinMessage"),
            )
        },
        "playerQuit" to Binding(PlayerQuitEvent::class.java) { event ->
            val quitEvent = event as PlayerQuitEvent
            mapOf(
                "type" to "playerQuit",
                "playerName" to quitEvent.player.name,
                "playerUuid" to quitEvent.player.uniqueId.toString(),
                "playerHandle" to quitEvent.player,
                "javaEvent" to quitEvent,
                "quitMessage" to extractMessage(quitEvent, "quitMessage", "getQuitMessage"),
            )
        },
        "serverLoad" to Binding(ServerLoadEvent::class.java) { event ->
            val serverLoadEvent = event as ServerLoadEvent
            mapOf(
                "type" to "serverLoad",
                "reload" to (serverLoadEvent.type == ServerLoadEvent.LoadType.RELOAD),
                "javaEvent" to serverLoadEvent,
            )
        },
    )

    fun require(key: String): Binding =
        bindings[key] ?: error("Unsupported event key \"$key\".")

    private fun extractMessage(target: Any, modernMethod: String, legacyMethod: String): String? =
        invokeNoArg(target, modernMethod)?.let(::toMessageString)
            ?: invokeNoArg(target, legacyMethod)?.toString()

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

    data class Binding(
        val eventType: Class<out Event>,
        val payloadFactory: (Event) -> Map<String, Any?>,
    )
}
