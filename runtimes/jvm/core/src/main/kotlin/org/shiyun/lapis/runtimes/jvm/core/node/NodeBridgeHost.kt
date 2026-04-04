package org.shiyun.lapis.runtimes.jvm.core.node

import org.shiyun.lapis.runtimes.jvm.core.host.AppDescriptor
import org.shiyun.lapis.runtimes.jvm.core.host.Callback
import org.shiyun.lapis.runtimes.jvm.core.host.ConfigStore
import org.shiyun.lapis.runtimes.jvm.core.host.DataDirectory
import org.shiyun.lapis.runtimes.jvm.core.host.HostBlock
import org.shiyun.lapis.runtimes.jvm.core.host.HostBossBar
import org.shiyun.lapis.runtimes.jvm.core.host.HostEntity
import org.shiyun.lapis.runtimes.jvm.core.host.HostExactItemIngredient
import org.shiyun.lapis.runtimes.jvm.core.host.HostInventory
import org.shiyun.lapis.runtimes.jvm.core.host.HostItem
import org.shiyun.lapis.runtimes.jvm.core.host.HostItemSpec
import org.shiyun.lapis.runtimes.jvm.core.host.HostLocation
import org.shiyun.lapis.runtimes.jvm.core.host.HostMaterialIngredient
import org.shiyun.lapis.runtimes.jvm.core.host.HostPlayer
import org.shiyun.lapis.runtimes.jvm.core.host.HostRecipeIngredient
import org.shiyun.lapis.runtimes.jvm.core.host.HostRecipeSpec
import org.shiyun.lapis.runtimes.jvm.core.host.HostScoreboard
import org.shiyun.lapis.runtimes.jvm.core.host.HostServices
import org.shiyun.lapis.runtimes.jvm.core.host.HostShapedRecipeSpec
import org.shiyun.lapis.runtimes.jvm.core.host.HostShapelessRecipeSpec
import org.shiyun.lapis.runtimes.jvm.core.host.HostWorld
import org.shiyun.lapis.runtimes.jvm.core.host.HttpRequestSpec
import org.shiyun.lapis.runtimes.jvm.core.host.HttpResponsePayload
import org.shiyun.lapis.runtimes.jvm.core.host.KeyValueStore
import org.shiyun.lapis.runtimes.jvm.core.host.Registration
import org.shiyun.lapis.runtimes.jvm.core.host.TaskHandle
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class NodeBridgeHost(
    private val hostServices: HostServices,
) : AutoCloseable {
    private val handleRegistry = ConcurrentHashMap<String, Any>()
    private val callbackRegistry = ConcurrentHashMap<String, Callback>()
    private lateinit var transport: NodeProcessTransport

    fun attach(transport: NodeProcessTransport) {
        this.transport = transport
    }

    fun handleRequest(method: String, rawParams: Any?): Any? {
        if (method == "callback.invoke") {
            val params = requireMap(rawParams)
            val callbackId = stringValue(params, "callbackId")
            val callback = callbackRegistry[callbackId]
                ?: error("Unknown parent callback \"$callbackId\".")
            return encode(callback.invoke(decode(params["payload"])))
        }

        val params = decode(rawParams)
        return when (method) {
            "context.describe" -> encode(
                mapOf(
                    "app" to hostServices.app(),
                    "storage" to mapOf(
                        "plugin" to hostServices.storage(),
                        "files" to hostServices.dataDirectory(),
                    ),
                    "config" to hostServices.config(),
                ),
            )
            "app.log" -> {
                val logParams = requireMap(params)
                val level = stringValue(logParams, "level")
                val messageText = stringValue(logParams, "message")
                when (level) {
                    "info" -> hostServices.logger().info(messageText)
                    "warn" -> hostServices.logger().warn(messageText)
                    "error" -> hostServices.logger().error(messageText, null)
                    "debug" -> hostServices.logger().info("[debug] $messageText")
                    else -> error("Unsupported log level \"$level\".")
                }
                null
            }
            "commands.register" -> encode(registerCommand(requireMap(params)))
            "events.on" -> encode(registerEvent(requireMap(params)))
            "tasks.run" -> encode(runTask(requireMap(params)))
            "tasks.delay" -> encode(delayTask(requireMap(params)))
            "tasks.repeat" -> encode(repeatTask(requireMap(params)))
            "tasks.timer" -> encode(timerTask(requireMap(params)))
            "players.online" -> encode(hostServices.onlinePlayers())
            "players.get" -> encode(hostServices.findPlayer(stringValue(requireMap(params), "query")))
            "players.require" -> encode(
                hostServices.findPlayer(stringValue(requireMap(params), "query"))
                    ?: error("Requested player is not online."),
            )
            "worlds.list" -> encode(hostServices.worlds())
            "worlds.get" -> encode(hostServices.findWorld(stringValue(requireMap(params), "name")))
            "worlds.require" -> encode(
                hostServices.findWorld(stringValue(requireMap(params), "name"))
                    ?: error("Requested world was not found."),
            )
            "entities.get" -> encode(hostServices.findEntity(stringValue(requireMap(params), "id")))
            "entities.spawn" -> encode(spawnEntity(requireMap(params)))
            "items.create" -> encode(hostServices.createItem(parseItemSpec(requireMap(params), "item")))
            "inventory.create" -> encode(createInventory(requireMap(params)))
            "inventory.open" -> {
                val inventoryParams = requireMap(params)
                val player = inventoryParams["player"] as? HostPlayer ?: error("inventory.open requires a player.")
                val inventory = inventoryParams["inventory"] as? HostInventory
                    ?: error("inventory.open requires an inventory.")
                inventory.open(player)
                null
            }
            "chat.broadcast" -> encode(hostServices.broadcastMessage(stringValue(requireMap(params), "message")))
            "effects.playSound" -> {
                playSound(requireMap(params))
                null
            }
            "effects.spawnParticle" -> {
                spawnParticle(requireMap(params))
                null
            }
            "effects.applyPotion" -> encode(applyPotion(requireMap(params)))
            "effects.clearPotion" -> {
                clearPotion(requireMap(params))
                null
            }
            "recipes.register" -> encode(registerRecipe(requireMap(params)))
            "bossBars.create" -> encode(createBossBar(requireMap(params)))
            "scoreboards.create" -> encode(createScoreboard(requireMap(params)))
            "http.fetch" -> encode(fetchHttp(requireMap(params)))
            "unsafe.dispatchCommand" -> encode(
                hostServices.dispatchConsoleCommand(stringValue(requireMap(params), "command")),
            )
            "handle.call" -> encode(invokeHandle(requireMap(params)))
            else -> error("Unsupported Node bridge method \"$method\".")
        }
    }

    override fun close() {
        handleRegistry.clear()
        callbackRegistry.clear()
    }

    private fun registerCommand(params: Map<String, Any?>): Registration {
        val execute = params["execute"] as? Callback
            ?: error("commands.register requires an execute callback.")
        return hostServices.registerCommand(
            name = stringValue(params, "name"),
            description = optionalString(params, "description") ?: "",
            usage = optionalString(params, "usage") ?: "",
            aliases = stringList(params["aliases"]),
            permission = optionalString(params, "permission"),
            execute = execute,
        )
    }

    private fun registerEvent(params: Map<String, Any?>): Registration {
        val handler = params["handler"] as? Callback
            ?: error("events.on requires a handler.")
        return hostServices.registerEvent(stringValue(params, "event"), handler)
    }

    private fun runTask(params: Map<String, Any?>): TaskHandle {
        val task = params["task"] as? Callback ?: error("tasks.run requires a callback.")
        return hostServices.runNow(task)
    }

    private fun delayTask(params: Map<String, Any?>): TaskHandle {
        val task = params["task"] as? Callback ?: error("tasks.delay requires a callback.")
        return hostServices.runLater(longValue(params, "delayTicks"), task)
    }

    private fun repeatTask(params: Map<String, Any?>): TaskHandle {
        val task = params["task"] as? Callback ?: error("tasks.repeat requires a callback.")
        val intervalTicks = longValue(params, "intervalTicks")
        return hostServices.runTimer(intervalTicks, intervalTicks, task)
    }

    private fun timerTask(params: Map<String, Any?>): TaskHandle {
        val task = params["task"] as? Callback ?: error("tasks.timer requires a callback.")
        return hostServices.runTimer(
            longValue(params, "delayTicks"),
            longValue(params, "intervalTicks"),
            task,
        )
    }

    private fun spawnEntity(params: Map<String, Any?>): HostEntity {
        val type = stringValue(params, "type")
        val location = parseLocation(requireMap(params["location"]), "location")
        val world = optionalString(params, "world") ?: location.world
            ?: error("entities.spawn requires a world.")
        return hostServices.spawnEntity(world, type, location)
    }

    private fun createInventory(params: Map<String, Any?>): HostInventory =
        hostServices.createInventory(
            id = optionalString(params, "id"),
            title = stringValue(params, "title"),
            size = intValue(params, "size"),
        )

    private fun playSound(params: Map<String, Any?>) {
        val sound = stringValue(params, "sound")
        val volume = floatValue(params, "volume", 1.0f)
        val pitch = floatValue(params, "pitch", 1.0f)
        val player = params["player"] as? HostPlayer
        val location = params["location"]?.let { parseLocation(requireMap(it), "location") }

        when {
            player != null -> hostServices.playSound(player, sound, volume, pitch)
            location != null -> hostServices.playSound(location, sound, volume, pitch)
            else -> error("effects.playSound requires a player or location.")
        }
    }

    private fun spawnParticle(params: Map<String, Any?>) {
        val location = parseLocation(requireMap(params["location"]), "location")
        val players = listValue(params["players"]).map { value ->
            value as? HostPlayer ?: error("effects.spawnParticle players must be player handles.")
        }
        hostServices.spawnParticle(
            location = location,
            particle = stringValue(params, "particle"),
            count = intValue(params, "count", 1),
            offsetX = doubleValue(params, "offsetX", 0.0),
            offsetY = doubleValue(params, "offsetY", 0.0),
            offsetZ = doubleValue(params, "offsetZ", 0.0),
            extra = doubleValue(params, "extra", 0.0),
            players = players,
        )
    }

    private fun applyPotion(params: Map<String, Any?>): Boolean {
        val player = params["player"] as? HostPlayer ?: error("effects.applyPotion requires a player.")
        return hostServices.applyPotionEffect(
            player = player,
            effect = stringValue(params, "effect"),
            durationTicks = intValue(params, "durationTicks"),
            amplifier = intValue(params, "amplifier", 0),
            ambient = booleanValue(params, "ambient", false),
            particles = booleanValue(params, "particles", true),
            icon = booleanValue(params, "icon", true),
        )
    }

    private fun clearPotion(params: Map<String, Any?>) {
        val player = params["player"] as? HostPlayer ?: error("effects.clearPotion requires a player.")
        hostServices.clearPotionEffect(player, optionalString(params, "effect"))
    }

    private fun registerRecipe(params: Map<String, Any?>): Map<String, Any?> {
        val spec = parseRecipeSpec(params)
        val registration = hostServices.registerRecipe(spec)
        return mapOf(
            "__lapisType" to "recipe",
            "__lapisRef" to rememberHandle(registration),
            "id" to spec.id,
        )
    }

    private fun createBossBar(params: Map<String, Any?>): HostBossBar =
        hostServices.createBossBar(
            id = optionalString(params, "id"),
            title = stringValue(params, "title"),
            color = optionalString(params, "color") ?: "purple",
            style = optionalString(params, "style") ?: "solid",
        ).also { handle ->
            params["progress"]?.let { handle.setProgress((it as? Number)?.toDouble() ?: 1.0) }
        }

    private fun createScoreboard(params: Map<String, Any?>): HostScoreboard =
        hostServices.createScoreboard(
            id = optionalString(params, "id"),
            title = stringValue(params, "title"),
        )

    private fun fetchHttp(params: Map<String, Any?>): HttpResponsePayload =
        hostServices.http().fetch(
            HttpRequestSpec(
                url = stringValue(params, "url"),
                method = optionalString(params, "method") ?: "GET",
                headers = stringMap(params["headers"]),
                body = optionalString(params, "body"),
            ),
        )

    private fun invokeHandle(params: Map<String, Any?>): Any? {
        val target = params["target"] ?: error("handle.call requires a target.")
        val method = stringValue(params, "method")
        val arguments = listValue(params["args"])

        return when (target) {
            is Registration -> when (method) {
                "unsubscribe", "unregister", "close" -> {
                    target.unregister()
                    null
                }
                else -> error("Unsupported registration method \"$method\".")
            }
            is TaskHandle -> when (method) {
                "cancel", "close" -> {
                    target.cancel()
                    null
                }
                else -> error("Unsupported task method \"$method\".")
            }
            is ConfigStore -> when (method) {
                "get" -> target.get(arguments[0].toString())
                "set" -> {
                    target.set(arguments[0].toString(), arguments.getOrNull(1))
                    null
                }
                "save" -> {
                    target.save()
                    null
                }
                "reload" -> {
                    target.reload()
                    null
                }
                "keys" -> target.keys()
                else -> error("Unsupported config method \"$method\".")
            }
            is KeyValueStore -> when (method) {
                "get" -> target.get(arguments[0].toString())
                "set" -> {
                    target.set(arguments[0].toString(), arguments.getOrNull(1))
                    null
                }
                "delete" -> {
                    target.delete(arguments[0].toString())
                    null
                }
                "save" -> {
                    target.save()
                    null
                }
                "reload" -> {
                    target.reload()
                    null
                }
                "keys" -> target.keys()
                else -> error("Unsupported store method \"$method\".")
            }
            is DataDirectory -> when (method) {
                "resolve" -> target.resolve(*arguments.map(Any?::toString).toTypedArray())
                "readText" -> target.readText(arguments[0].toString())
                "writeText" -> {
                    target.writeText(arguments[0].toString(), arguments.getOrNull(1)?.toString() ?: "")
                    null
                }
                "exists" -> target.exists(arguments[0].toString())
                "mkdirs" -> {
                    target.mkdirs(arguments.getOrNull(0)?.toString() ?: "")
                    null
                }
                else -> error("Unsupported file store method \"$method\".")
            }
            is HostPlayer -> invokePlayer(target, method, arguments)
            is HostWorld -> invokeWorld(target, method, arguments)
            is HostEntity -> invokeEntity(target, method, arguments)
            is HostBlock -> invokeBlock(target, method, arguments)
            is HostItem -> invokeItem(target, method, arguments)
            is HostInventory -> invokeInventory(target, method, arguments)
            is HostBossBar -> invokeBossBar(target, method, arguments)
            is HostScoreboard -> invokeScoreboard(target, method, arguments)
            else -> error("Unsupported handle target for method \"$method\".")
        }
    }

    private fun invokePlayer(player: HostPlayer, method: String, args: List<Any?>): Any? =
        when (method) {
            "worldName" -> player.worldName()
            "location" -> player.location()
            "sendMessage" -> {
                player.sendMessage(args[0].toString())
                null
            }
            "actionBar" -> {
                player.sendActionBar(args[0].toString())
                null
            }
            "showTitle" -> {
                val options = (args.getOrNull(1) as? Map<*, *>)?.entries
                    ?.associate { (key, value) -> key.toString() to value }
                    ?: emptyMap()
                player.showTitle(
                    title = args[0].toString(),
                    subtitle = options["subtitle"]?.toString() ?: "",
                    fadeInTicks = (options["fadeInTicks"] as? Number)?.toInt() ?: 10,
                    stayTicks = (options["stayTicks"] as? Number)?.toInt() ?: 70,
                    fadeOutTicks = (options["fadeOutTicks"] as? Number)?.toInt() ?: 20,
                )
                null
            }
            "hasPermission" -> player.hasPermission(args[0].toString())
            "teleport" -> player.teleport(parseLocation(requireMap(args[0]), "location"))
            "inventory" -> player.inventory()
            "tags" -> player.tags()
            else -> error("Unsupported player method \"$method\".")
        }

    private fun invokeWorld(world: HostWorld, method: String, args: List<Any?>): Any? =
        when (method) {
            "players" -> world.players()
            "entities" -> world.entities()
            "spawnLocation" -> world.spawnLocation()
            "time" -> world.time()
            "setTime" -> {
                world.setTime((args[0] as? Number)?.toLong() ?: 0L)
                null
            }
            "storming" -> world.storming()
            "setStorming" -> {
                world.setStorming(args[0] as? Boolean ?: false)
                null
            }
            "blockAt" -> world.blockAt(parseLocation(requireMap(args[0]), "location"))
            else -> error("Unsupported world method \"$method\".")
        }

    private fun invokeEntity(entity: HostEntity, method: String, args: List<Any?>): Any? =
        when (method) {
            "worldName" -> entity.worldName()
            "location" -> entity.location()
            "teleport" -> entity.teleport(parseLocation(requireMap(args[0]), "location"))
            "remove" -> {
                entity.remove()
                null
            }
            "tags" -> entity.tags()
            else -> error("Unsupported entity method \"$method\".")
        }

    private fun invokeBlock(block: HostBlock, method: String, args: List<Any?>): Any? =
        when (method) {
            "location" -> block.location()
            "setType" -> block.setType(args[0].toString())
            else -> error("Unsupported block method \"$method\".")
        }

    private fun invokeItem(item: HostItem, method: String, args: List<Any?>): Any? =
        when (method) {
            "amount" -> item.amount()
            "setAmount" -> {
                item.setAmount((args[0] as? Number)?.toInt() ?: 1)
                null
            }
            "name" -> item.displayName()
            "setName" -> {
                item.setDisplayName(args.getOrNull(0)?.toString())
                null
            }
            "lore" -> item.lore()
            "setLore" -> {
                item.setLore(listValue(args.getOrNull(0)).map(Any?::toString))
                null
            }
            "enchantments" -> item.enchantments()
            "enchant" -> {
                item.setEnchantment(args[0].toString(), (args[1] as? Number)?.toInt() ?: 1)
                null
            }
            "removeEnchantment" -> {
                item.removeEnchantment(args[0].toString())
                null
            }
            "clone" -> item.cloneItem()
            "tags" -> item.tags()
            else -> error("Unsupported item method \"$method\".")
        }

    private fun invokeInventory(inventory: HostInventory, method: String, args: List<Any?>): Any? =
        when (method) {
            "get" -> inventory.getItem((args[0] as? Number)?.toInt() ?: 0)
            "set" -> {
                inventory.setItem(
                    (args[0] as? Number)?.toInt() ?: 0,
                    args.getOrNull(1) as? HostItem,
                )
                null
            }
            "add" -> {
                inventory.addItem(args[0] as? HostItem ?: error("inventory.add requires an item handle."))
                null
            }
            "clear" -> {
                inventory.clear()
                null
            }
            "clearSlot" -> {
                inventory.clearSlot((args[0] as? Number)?.toInt() ?: 0)
                null
            }
            "open" -> {
                inventory.open(args[0] as? HostPlayer ?: error("inventory.open requires a player handle."))
                null
            }
            else -> error("Unsupported inventory method \"$method\".")
        }

    private fun invokeBossBar(bossBar: HostBossBar, method: String, args: List<Any?>): Any? =
        when (method) {
            "title" -> bossBar.title()
            "setTitle" -> {
                bossBar.setTitle(args[0].toString())
                null
            }
            "progress" -> bossBar.progress()
            "setProgress" -> {
                bossBar.setProgress((args[0] as? Number)?.toDouble() ?: 1.0)
                null
            }
            "color" -> bossBar.color()
            "setColor" -> {
                bossBar.setColor(args[0].toString())
                null
            }
            "style" -> bossBar.style()
            "setStyle" -> {
                bossBar.setStyle(args[0].toString())
                null
            }
            "players" -> bossBar.players()
            "addPlayer" -> {
                bossBar.addPlayer(args[0] as? HostPlayer ?: error("bossBar.addPlayer requires a player handle."))
                null
            }
            "removePlayer" -> {
                bossBar.removePlayer(args[0] as? HostPlayer ?: error("bossBar.removePlayer requires a player handle."))
                null
            }
            "clearPlayers", "delete", "close" -> {
                bossBar.removeAllPlayers()
                null
            }
            else -> error("Unsupported boss bar method \"$method\".")
        }

    private fun invokeScoreboard(scoreboard: HostScoreboard, method: String, args: List<Any?>): Any? =
        when (method) {
            "title" -> scoreboard.title()
            "setTitle" -> {
                scoreboard.setTitle(args[0].toString())
                null
            }
            "setLine" -> {
                scoreboard.setLine((args[0] as? Number)?.toInt() ?: 0, args[1].toString())
                null
            }
            "removeLine" -> {
                scoreboard.removeLine((args[0] as? Number)?.toInt() ?: 0)
                null
            }
            "clear" -> {
                scoreboard.clear()
                null
            }
            "viewers" -> scoreboard.viewers()
            "show" -> {
                scoreboard.show(args[0] as? HostPlayer ?: error("scoreboard.show requires a player handle."))
                null
            }
            "hide" -> {
                scoreboard.hide(args[0] as? HostPlayer ?: error("scoreboard.hide requires a player handle."))
                null
            }
            "delete", "close" -> {
                scoreboard.close()
                null
            }
            else -> error("Unsupported scoreboard method \"$method\".")
        }

    private fun parseItemSpec(value: Map<String, Any?>, label: String): HostItemSpec =
        HostItemSpec(
            type = stringValue(value, "type"),
            amount = intValue(value, "amount", 1),
            displayName = optionalString(value, "name"),
            lore = listValue(value["lore"]).map(Any?::toString),
            enchantments = intMap(value["enchantments"]),
        )

    private fun parseRecipeSpec(value: Map<String, Any?>): HostRecipeSpec {
        val kind = stringValue(value, "kind")
        val id = stringValue(value, "id")
        val result = parseItemSpec(requireMap(value["result"]), "result")
        return when (kind) {
            "shaped" -> HostShapedRecipeSpec(
                id = id,
                result = result,
                shape = listValue(value["shape"]).map(Any?::toString),
                ingredients = requireMap(value["ingredients"]).entries.associate { (key, nestedValue) ->
                    key.first() to parseRecipeIngredient(nestedValue)
                },
            )
            "shapeless" -> HostShapelessRecipeSpec(
                id = id,
                result = result,
                ingredients = listValue(value["ingredients"]).map(::parseRecipeIngredient),
            )
            else -> error("Unsupported recipe kind \"$kind\".")
        }
    }

    private fun parseRecipeIngredient(value: Any?): HostRecipeIngredient =
        when (value) {
            is String -> HostMaterialIngredient(value)
            is Map<*, *> -> HostExactItemIngredient(
                parseItemSpec(
                    value.entries.associate { (key, nestedValue) -> key.toString() to nestedValue },
                    "ingredient",
                ),
            )
            else -> error("Unsupported recipe ingredient payload.")
        }

    private fun parseLocation(value: Map<String, Any?>, label: String): HostLocation =
        HostLocation(
            world = optionalString(value, "world"),
            x = doubleValue(value, "x"),
            y = doubleValue(value, "y"),
            z = doubleValue(value, "z"),
            yaw = floatValue(value, "yaw", 0.0f),
            pitch = floatValue(value, "pitch", 0.0f),
        )

    private fun decode(value: Any?): Any? =
        when (value) {
            null, is String, is Number, is Boolean -> value
            is List<*> -> value.map(::decode)
            is Map<*, *> -> {
                val entries = value.entries.associate { (key, nestedValue) -> key.toString() to nestedValue }
                when {
                    entries.containsKey("__lapisCallback") -> {
                        val callbackId = entries["__lapisCallback"]?.toString()
                            ?: error("Callback marker must contain an id.")
                        Callback { payload ->
                            transport.request(
                                "callback.invoke",
                                mapOf(
                                    "callbackId" to callbackId,
                                    "payload" to encode(payload),
                                ),
                            )
                        }
                    }
                    entries.containsKey("__lapisRef") -> resolveHandle(entries["__lapisRef"]?.toString())
                    else -> entries.mapValues { (_, nestedValue) -> decode(nestedValue) }
                }
            }
            else -> value
        }

    private fun encode(value: Any?): Any? =
        when (value) {
            null, is String, is Number, is Boolean -> value
            is AppDescriptor -> mapOf(
                "id" to value.id,
                "name" to value.name,
                "version" to value.version,
                "engine" to value.engine,
                "apiVersion" to value.apiVersion,
                "backend" to value.backend,
                "runtime" to value.runtime,
            )
            is HttpResponsePayload -> mapOf(
                "url" to value.url,
                "status" to value.status,
                "ok" to value.ok,
                "headers" to value.headers,
                "body" to value.body,
            )
            is HostLocation -> mapOf(
                "world" to value.world,
                "x" to value.x,
                "y" to value.y,
                "z" to value.z,
                "yaw" to value.yaw,
                "pitch" to value.pitch,
            )
            is ConfigStore -> handleValue("config", value)
            is KeyValueStore -> handleValue("store", value)
            is DataDirectory -> handleValue("files", value, mapOf("path" to value.path()))
            is Registration -> handleValue("registration", value)
            is TaskHandle -> handleValue("task", value)
            is HostPlayer -> handleValue(
                "player",
                value,
                mapOf(
                    "id" to value.id(),
                    "name" to value.name(),
                    "tags" to value.tags(),
                    "unsafe" to mapOf("handle" to null),
                ),
            )
            is HostWorld -> handleValue(
                "world",
                value,
                mapOf(
                    "name" to value.name(),
                    "environment" to value.environment(),
                    "unsafe" to mapOf("handle" to null),
                ),
            )
            is HostEntity -> handleValue(
                "entity",
                value,
                mapOf(
                    "id" to value.id(),
                    "type" to value.type(),
                    "name" to value.customName(),
                    "tags" to value.tags(),
                    "unsafe" to mapOf("handle" to null),
                ),
            )
            is HostBlock -> handleValue(
                "block",
                value,
                mapOf(
                    "type" to value.type(),
                    "worldName" to value.worldName(),
                    "location" to value.location(),
                    "unsafe" to mapOf("handle" to null),
                ),
            )
            is HostItem -> handleValue(
                "item",
                value,
                mapOf(
                    "type" to value.type(),
                    "tags" to value.tags(),
                    "unsafe" to mapOf("handle" to null),
                ),
            )
            is HostInventory -> handleValue(
                "inventory",
                value,
                mapOf(
                    "id" to value.id(),
                    "title" to value.title(),
                    "size" to value.size(),
                    "unsafe" to mapOf("handle" to null),
                ),
            )
            is HostBossBar -> handleValue(
                "bossBar",
                value,
                mapOf(
                    "id" to value.id(),
                    "unsafe" to mapOf("handle" to null),
                ),
            )
            is HostScoreboard -> handleValue(
                "scoreboard",
                value,
                mapOf(
                    "id" to value.id(),
                    "unsafe" to mapOf("handle" to null),
                ),
            )
            is Callback -> mapOf("__lapisCallback" to rememberCallback(value))
            is Map<*, *> -> value.entries.associate { (key, nestedValue) ->
                key.toString() to encode(nestedValue)
            }
            is List<*> -> value.map(::encode)
            is Array<*> -> value.map(::encode)
            else -> null
        }

    private fun handleValue(type: String, value: Any, extra: Map<String, Any?> = emptyMap()): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "__lapisType" to type,
            "__lapisRef" to rememberHandle(value),
        ).apply {
            extra.forEach { (key, nestedValue) -> put(key, encode(nestedValue)) }
        }

    private fun rememberHandle(value: Any): String {
        val ref = UUID.randomUUID().toString()
        handleRegistry[ref] = value
        return ref
    }

    private fun rememberCallback(value: Callback): String {
        val ref = UUID.randomUUID().toString()
        callbackRegistry[ref] = value
        return ref
    }

    private fun resolveHandle(ref: String?): Any =
        handleRegistry[ref] ?: error("Unknown handle reference \"$ref\".")

    private fun requireMap(value: Any?): Map<String, Any?> =
        when (value) {
            is Map<*, *> -> value.entries.associate { (key, nestedValue) -> key.toString() to nestedValue }
            else -> error("Expected an object payload.")
        }

    private fun listValue(value: Any?): List<Any?> =
        when (value) {
            null -> emptyList()
            is List<*> -> value.map(::decode)
            else -> error("Expected an array payload.")
        }

    private fun stringValue(values: Map<String, Any?>, field: String): String =
        values[field]?.toString()?.takeIf(String::isNotBlank)
            ?: error("Expected non-empty string field \"$field\".")

    private fun optionalString(values: Map<String, Any?>, field: String): String? =
        values[field]?.toString()?.takeIf(String::isNotBlank)

    private fun intValue(values: Map<String, Any?>, field: String, defaultValue: Int? = null): Int =
        (values[field] as? Number)?.toInt() ?: defaultValue ?: error("Expected numeric field \"$field\".")

    private fun longValue(values: Map<String, Any?>, field: String): Long =
        (values[field] as? Number)?.toLong() ?: error("Expected numeric field \"$field\".")

    private fun doubleValue(values: Map<String, Any?>, field: String, defaultValue: Double? = null): Double =
        (values[field] as? Number)?.toDouble() ?: defaultValue ?: error("Expected numeric field \"$field\".")

    private fun floatValue(values: Map<String, Any?>, field: String, defaultValue: Float): Float =
        (values[field] as? Number)?.toFloat() ?: defaultValue

    private fun booleanValue(values: Map<String, Any?>, field: String, defaultValue: Boolean): Boolean =
        values[field] as? Boolean ?: defaultValue

    private fun stringList(value: Any?): List<String> =
        listValue(value).map(Any?::toString)

    private fun stringMap(value: Any?): Map<String, String> =
        requireMap(value).mapValues { (_, nestedValue) -> nestedValue?.toString() ?: "" }

    private fun intMap(value: Any?): Map<String, Int> =
        requireMap(value).mapValues { (_, nestedValue) -> (nestedValue as? Number)?.toInt() ?: 0 }
}
