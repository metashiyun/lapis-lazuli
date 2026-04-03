package org.shiyun.lapislazuli.runtime.bukkit

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.Event
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.ServerLoadEvent

internal object BukkitEventBindings {
    private val plainTextSerializer = PlainTextComponentSerializer.plainText()

    private val bindings = mapOf(
        "playerJoin" to Binding(PlayerJoinEvent::class.java) { event ->
            val joinEvent = event as PlayerJoinEvent
            mapOf(
                "type" to "playerJoin",
                "playerName" to joinEvent.player.name,
                "playerUuid" to joinEvent.player.uniqueId.toString(),
                "joinMessage" to serializeComponent(joinEvent.joinMessage()),
            )
        },
        "playerQuit" to Binding(PlayerQuitEvent::class.java) { event ->
            val quitEvent = event as PlayerQuitEvent
            mapOf(
                "type" to "playerQuit",
                "playerName" to quitEvent.player.name,
                "playerUuid" to quitEvent.player.uniqueId.toString(),
                "quitMessage" to serializeComponent(quitEvent.quitMessage()),
            )
        },
        "serverLoad" to Binding(ServerLoadEvent::class.java) { event ->
            val serverLoadEvent = event as ServerLoadEvent
            mapOf(
                "type" to "serverLoad",
                "reload" to (serverLoadEvent.type == ServerLoadEvent.LoadType.RELOAD),
            )
        },
    )

    fun require(key: String): Binding =
        bindings[key] ?: error("Unsupported event key \"$key\".")

    private fun serializeComponent(component: Component?): String? =
        component?.let(plainTextSerializer::serialize)

    data class Binding(
        val eventType: Class<out Event>,
        val payloadFactory: (Event) -> Map<String, Any?>,
    )
}
