package dev.lapislazuli.runtime.bukkit

import dev.lapislazuli.runtime.core.host.Callback
import org.bukkit.event.Event
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.server.ServerLoadEvent
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldUnloadEvent

internal object BukkitEventBindings {
    private val bindings = mapOf(
        "server.ready" to Binding(ServerLoadEvent::class.java) { host, event ->
            val serverLoadEvent = event as ServerLoadEvent
            mapOf(
                "type" to "server.ready",
                "reload" to (serverLoadEvent.type == ServerLoadEvent.LoadType.RELOAD),
            )
        },
        "player.join" to Binding(PlayerJoinEvent::class.java) { host, event ->
            val joinEvent = event as PlayerJoinEvent
            mapOf(
                "type" to "player.join",
                "player" to host.wrapPlayer(joinEvent.player),
                "joinMessage" to extractMessage(joinEvent, "joinMessage", "getJoinMessage"),
                "setJoinMessage" to Callback { payload ->
                    setMessage(joinEvent, "setJoinMessage", payload?.toString())
                    null
                },
            )
        },
        "player.quit" to Binding(PlayerQuitEvent::class.java) { host, event ->
            val quitEvent = event as PlayerQuitEvent
            mapOf(
                "type" to "player.quit",
                "player" to host.wrapPlayer(quitEvent.player),
                "quitMessage" to extractMessage(quitEvent, "quitMessage", "getQuitMessage"),
                "setQuitMessage" to Callback { payload ->
                    setMessage(quitEvent, "setQuitMessage", payload?.toString())
                    null
                },
            )
        },
        "player.chat" to Binding(AsyncPlayerChatEvent::class.java) { host, event ->
            val chatEvent = event as AsyncPlayerChatEvent
            host.cancellablePayload(
                type = "player.chat",
                cancelled = chatEvent::isCancelled,
                setCancelled = { value -> chatEvent.isCancelled = value },
                values = mapOf(
                    "player" to host.wrapPlayer(chatEvent.player),
                    "message" to chatEvent.message,
                    "setMessage" to Callback { payload ->
                        chatEvent.message = payload?.toString() ?: ""
                        null
                    },
                    "recipients" to Callback {
                        chatEvent.recipients.map(host::wrapPlayer)
                    },
                ),
            )
        },
        "player.move" to Binding(PlayerMoveEvent::class.java) { host, event ->
            val moveEvent = event as PlayerMoveEvent
            host.cancellablePayload(
                type = "player.move",
                cancelled = moveEvent::isCancelled,
                setCancelled = { value -> moveEvent.isCancelled = value },
                values = mapOf(
                    "player" to host.wrapPlayer(moveEvent.player),
                    "from" to host.toHostLocation(moveEvent.from),
                    "to" to host.toHostLocation(moveEvent.to ?: moveEvent.from),
                ),
            )
        },
        "player.interact" to Binding(PlayerInteractEvent::class.java) { host, event ->
            val interactEvent = event as PlayerInteractEvent
            host.cancellablePayload(
                type = "player.interact",
                cancelled = interactEvent::isCancelled,
                setCancelled = { value -> interactEvent.isCancelled = value },
                values = mapOf(
                    "player" to host.wrapPlayer(interactEvent.player),
                    "action" to interactEvent.action.name.lowercase(),
                    "hand" to interactEvent.hand?.name?.lowercase(),
                    "item" to interactEvent.item?.let(host::wrapItem),
                    "block" to interactEvent.clickedBlock?.let(host::wrapBlock),
                    "face" to interactEvent.blockFace?.name?.lowercase(),
                ),
            )
        },
        "player.teleport" to Binding(PlayerTeleportEvent::class.java) { host, event ->
            val teleportEvent = event as PlayerTeleportEvent
            host.cancellablePayload(
                type = "player.teleport",
                cancelled = teleportEvent::isCancelled,
                setCancelled = { value -> teleportEvent.isCancelled = value },
                values = mapOf(
                    "player" to host.wrapPlayer(teleportEvent.player),
                    "from" to host.toHostLocation(teleportEvent.from),
                    "to" to host.toHostLocation(teleportEvent.to ?: teleportEvent.from),
                    "cause" to teleportEvent.cause.name.lowercase(),
                ),
            )
        },
        "block.break" to Binding(BlockBreakEvent::class.java) { host, event ->
            val breakEvent = event as BlockBreakEvent
            host.cancellablePayload(
                type = "block.break",
                cancelled = breakEvent::isCancelled,
                setCancelled = { value -> breakEvent.isCancelled = value },
                values = mapOf(
                    "player" to host.wrapPlayer(breakEvent.player),
                    "block" to host.wrapBlock(breakEvent.block),
                    "expToDrop" to breakEvent.expToDrop,
                    "setExpToDrop" to Callback { payload ->
                        breakEvent.expToDrop = (payload as? Number)?.toInt() ?: 0
                        null
                    },
                    "dropItems" to breakEvent.isDropItems,
                    "setDropItems" to Callback { payload ->
                        breakEvent.isDropItems = payload as? Boolean ?: false
                        null
                    },
                ),
            )
        },
        "block.place" to Binding(BlockPlaceEvent::class.java) { host, event ->
            val placeEvent = event as BlockPlaceEvent
            host.cancellablePayload(
                type = "block.place",
                cancelled = placeEvent::isCancelled,
                setCancelled = { value -> placeEvent.isCancelled = value },
                values = mapOf(
                    "player" to host.wrapPlayer(placeEvent.player),
                    "block" to host.wrapBlock(placeEvent.block),
                    "against" to host.wrapBlock(placeEvent.blockAgainst),
                    "item" to host.wrapItem(placeEvent.itemInHand),
                    "canBuild" to placeEvent.canBuild(),
                ),
            )
        },
        "entity.damage" to Binding(EntityDamageEvent::class.java) { host, event ->
            val damageEvent = event as EntityDamageEvent
            host.cancellablePayload(
                type = "entity.damage",
                cancelled = damageEvent::isCancelled,
                setCancelled = { value -> damageEvent.isCancelled = value },
                values = mapOf(
                    "entity" to host.wrapEntity(damageEvent.entity),
                    "damage" to damageEvent.damage,
                    "finalDamage" to damageEvent.finalDamage,
                    "cause" to damageEvent.cause.name.lowercase(),
                    "damager" to (damageEvent as? EntityDamageByEntityEvent)?.damager?.let(host::wrapEntity),
                    "setDamage" to Callback { payload ->
                        damageEvent.damage = (payload as? Number)?.toDouble() ?: 0.0
                        null
                    },
                ),
            )
        },
        "entity.death" to Binding(EntityDeathEvent::class.java) { host, event ->
            val deathEvent = event as EntityDeathEvent
            mapOf(
                "type" to "entity.death",
                "entity" to host.wrapEntity(deathEvent.entity),
                "drops" to Callback {
                    deathEvent.drops.map(host::wrapItem)
                },
                "droppedExp" to deathEvent.droppedExp,
                "setDroppedExp" to Callback { payload ->
                    deathEvent.droppedExp = (payload as? Number)?.toInt() ?: 0
                    null
                },
            )
        },
        "inventory.click" to Binding(InventoryClickEvent::class.java) { host, event ->
            val clickEvent = event as InventoryClickEvent
            host.cancellablePayload(
                type = "inventory.click",
                cancelled = clickEvent::isCancelled,
                setCancelled = { value -> clickEvent.isCancelled = value },
                values = mapOf(
                    "player" to (clickEvent.whoClicked as? org.bukkit.entity.Player)?.let(host::wrapPlayer),
                    "inventory" to host.wrapInventory(clickEvent.inventory),
                    "slot" to clickEvent.rawSlot,
                    "clickType" to clickEvent.click.name.lowercase(),
                    "currentItem" to clickEvent.currentItem?.let(host::wrapItem),
                    "cursorItem" to clickEvent.cursor?.let(host::wrapItem),
                ),
            )
        },
        "inventory.close" to Binding(InventoryCloseEvent::class.java) { host, event ->
            val closeEvent = event as InventoryCloseEvent
            mapOf(
                "type" to "inventory.close",
                "player" to (closeEvent.player as? org.bukkit.entity.Player)?.let(host::wrapPlayer),
                "inventory" to host.wrapInventory(closeEvent.inventory),
            )
        },
        "world.load" to Binding(WorldLoadEvent::class.java) { host, event ->
            val loadEvent = event as WorldLoadEvent
            mapOf(
                "type" to "world.load",
                "world" to host.wrapWorld(loadEvent.world),
            )
        },
        "world.unload" to Binding(WorldUnloadEvent::class.java) { host, event ->
            val unloadEvent = event as WorldUnloadEvent
            host.cancellablePayload(
                type = "world.unload",
                cancelled = unloadEvent::isCancelled,
                setCancelled = { value -> unloadEvent.isCancelled = value },
                values = mapOf(
                    "world" to host.wrapWorld(unloadEvent.world),
                ),
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

    private fun setMessage(target: Any, methodName: String, value: String?) {
        val setter = runCatching {
            target.javaClass.methods.firstOrNull { method ->
                method.name == methodName &&
                    method.parameterCount == 1 &&
                    method.parameterTypes[0] == String::class.java
            }
        }.getOrNull()
        runCatching { setter?.invoke(target, value) }
    }

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
        val payloadFactory: (BukkitHostServices, Event) -> Map<String, Any?>,
    )
}
