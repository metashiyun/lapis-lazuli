package dev.lapislazuli.runtimes.jvm.core.js

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
import dev.lapislazuli.runtimes.jvm.core.host.HttpRequestSpec
import dev.lapislazuli.runtimes.jvm.core.host.HttpResponsePayload
import dev.lapislazuli.runtimes.jvm.core.host.HttpService
import dev.lapislazuli.runtimes.jvm.core.host.HostLocation
import dev.lapislazuli.runtimes.jvm.core.host.HostPlayer
import dev.lapislazuli.runtimes.jvm.core.host.HostRecipeIngredient
import dev.lapislazuli.runtimes.jvm.core.host.HostExactItemIngredient
import dev.lapislazuli.runtimes.jvm.core.host.HostMaterialIngredient
import dev.lapislazuli.runtimes.jvm.core.host.HostRecipeSpec
import dev.lapislazuli.runtimes.jvm.core.host.HostScoreboard
import dev.lapislazuli.runtimes.jvm.core.host.HostShapedRecipeSpec
import dev.lapislazuli.runtimes.jvm.core.host.HostShapelessRecipeSpec
import dev.lapislazuli.runtimes.jvm.core.host.HostServices
import dev.lapislazuli.runtimes.jvm.core.host.HostWorld
import dev.lapislazuli.runtimes.jvm.core.host.KeyValueStore
import dev.lapislazuli.runtimes.jvm.core.host.TaskHandle
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyArray
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class JsBridgeFactory {
    fun createPluginContext(hostServices: HostServices, promiseConstructor: Value? = null): Any? {
        val root = linkedMapOf<String, Any?>()
        root["app"] = createApp(hostServices)
        root["commands"] = createCommands(hostServices)
        root["events"] = createEvents(hostServices)
        root["tasks"] = createTasks(hostServices)
        root["players"] = createPlayers(hostServices)
        root["worlds"] = createWorlds(hostServices)
        root["entities"] = createEntities(hostServices)
        root["items"] = createItems(hostServices)
        root["inventory"] = createInventory(hostServices)
        root["chat"] = createChat(hostServices)
        root["effects"] = createEffects(hostServices)
        root["recipes"] = createRecipes(hostServices)
        root["bossBars"] = createBossBars(hostServices)
        root["scoreboards"] = createScoreboards(hostServices)
        root["storage"] = createStorage(hostServices)
        root["config"] = createConfig(hostServices.config())
        root["http"] = createHttp(hostServices.http(), promiseConstructor)
        root["unsafe"] = createUnsafe(hostServices)
        return toGuestValue(root)
    }

    private fun createApp(hostServices: HostServices): Map<String, Any?> {
        val app = hostServices.app()
        return mapOf(
            "id" to app.id,
            "name" to app.name,
            "version" to app.version,
            "engine" to app.engine,
            "apiVersion" to app.apiVersion,
            "backend" to app.backend,
            "runtime" to app.runtime,
            "log" to createLogger(hostServices),
            "onShutdown" to executable { arguments ->
                require(arguments.isNotEmpty()) { "app.onShutdown requires a callback." }
                val handler = arguments[0]
                requireExecutable(handler, "handler")
                registrationHandle(
                    hostServices.onShutdown { payload -> executeCallback(handler, payload) },
                )
            },
        )
    }

    private fun createLogger(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "info" to executable { arguments ->
                hostServices.logger().info(textArg(arguments, 0, "message"))
                null
            },
            "warn" to executable { arguments ->
                hostServices.logger().warn(textArg(arguments, 0, "message"))
                null
            },
            "error" to executable { arguments ->
                hostServices.logger().error(textArg(arguments, 0, "message"), null)
                null
            },
            "debug" to executable { arguments ->
                hostServices.logger().info("[debug] ${textArg(arguments, 0, "message")}")
                null
            },
        )

    private fun createCommands(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "register" to executable { arguments ->
                val registrationSpec = parseCommandRegistration(arguments)
                registrationHandle(
                    hostServices.registerCommand(
                        name = registrationSpec.name,
                        description = registrationSpec.description,
                        usage = registrationSpec.usage,
                        aliases = registrationSpec.aliases,
                        permission = registrationSpec.permission,
                    ) { payload ->
                        executeCallback(registrationSpec.execute, payload)
                    },
                )
            },
        )

    private fun createEvents(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "on" to executable { arguments ->
                require(arguments.size >= 2) { "events.on requires an event key and handler." }
                val eventKey = stringArg(arguments, 0, "event")
                val handler = arguments[1]
                requireExecutable(handler, "handler")
                registrationHandle(
                    hostServices.registerEvent(eventKey) { payload ->
                        executeCallback(handler, payload)
                    },
                )
            },
        )

    private fun createTasks(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "run" to executable { arguments ->
                taskHandle(hostServices.runNow(taskCallback(arguments, 0, "run")))
            },
            "delay" to executable { arguments ->
                val delayTicks = longArg(arguments, 0, "delayTicks")
                taskHandle(hostServices.runLater(delayTicks, taskCallback(arguments, 1, "delay")))
            },
            "repeat" to executable { arguments ->
                val intervalTicks = longArg(arguments, 0, "intervalTicks")
                taskHandle(hostServices.runTimer(intervalTicks, intervalTicks, taskCallback(arguments, 1, "repeat")))
            },
            "timer" to executable { arguments ->
                val delayTicks = longArg(arguments, 0, "delayTicks")
                val intervalTicks = longArg(arguments, 1, "intervalTicks")
                taskHandle(hostServices.runTimer(delayTicks, intervalTicks, taskCallback(arguments, 2, "timer")))
            },
        )

    private fun createPlayers(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "online" to executable { hostServices.onlinePlayers() },
            "get" to executable { arguments ->
                hostServices.findPlayer(stringArg(arguments, 0, "query"))
            },
            "require" to executable { arguments ->
                hostServices.findPlayer(stringArg(arguments, 0, "query"))
                    ?: error("Player ${stringArg(arguments, 0, "query")} is not online.")
            },
        )

    private fun createWorlds(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "list" to executable { hostServices.worlds() },
            "get" to executable { arguments ->
                hostServices.findWorld(stringArg(arguments, 0, "name"))
            },
            "require" to executable { arguments ->
                hostServices.findWorld(stringArg(arguments, 0, "name"))
                    ?: error("World ${stringArg(arguments, 0, "name")} was not found.")
            },
        )

    private fun createEntities(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "get" to executable { arguments ->
                hostServices.findEntity(stringArg(arguments, 0, "id"))
            },
            "spawn" to executable { arguments ->
                val spec = requireNotNull(firstArg(arguments, "spawn spec"))
                val type = memberString(spec, "type", required = true)
                    ?: error("entities.spawn requires a type.")
                val location = parseLocation(
                    requireNotNull(member(spec, "location")) { "entities.spawn requires a location." },
                    "location",
                )
                val worldName = location.world
                    ?: memberString(spec, "world", required = false)
                    ?: error("entities.spawn requires a world in the location or world field.")
                hostServices.spawnEntity(worldName, type, location)
            },
        )

    private fun createItems(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "create" to executable { arguments ->
                hostServices.createItem(parseItemSpec(arguments, 0, "item"))
            },
        )

    private fun createInventory(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "create" to executable { arguments ->
                val spec = requireNotNull(firstArg(arguments, "inventory spec"))
                val title = memberText(spec, "title", required = true)
                    ?: error("inventory.create requires a title.")
                val size = memberInt(spec, "size", required = true)
                    ?: error("inventory.create requires a size.")
                val id = memberString(spec, "id", required = false)
                hostServices.createInventory(id, title, size)
            },
            "open" to executable { arguments ->
                require(arguments.size >= 2) { "inventory.open requires a player and an inventory." }
                val player = requireHostPlayer(arguments[0], "player")
                val inventory = requireHostInventory(arguments[1], "inventory")
                inventory.open(player)
                null
            },
        )

    private fun createChat(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "broadcast" to executable { arguments ->
                hostServices.broadcastMessage(textArg(arguments, 0, "message"))
            },
        )

    private fun createEffects(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "playSound" to executable { arguments ->
                val spec = requireNotNull(firstArg(arguments, "sound spec"))
                val sound = memberString(spec, "sound", required = true)
                    ?: error("effects.playSound requires a sound.")
                val volume = memberDouble(spec, "volume", required = false)?.toFloat() ?: 1.0f
                val pitch = memberDouble(spec, "pitch", required = false)?.toFloat() ?: 1.0f
                val player = member(spec, "player")
                if (player != null && !player.isNull) {
                    hostServices.playSound(requireHostPlayer(player, "player"), sound, volume, pitch)
                } else {
                    val location = member(spec, "location")
                        ?: error("effects.playSound requires either player or location.")
                    hostServices.playSound(parseLocation(location, "location"), sound, volume, pitch)
                }
                null
            },
            "spawnParticle" to executable { arguments ->
                val spec = requireNotNull(firstArg(arguments, "particle spec"))
                val particle = memberString(spec, "particle", required = true)
                    ?: error("effects.spawnParticle requires a particle.")
                val location = parseLocation(
                    requireNotNull(member(spec, "location")) { "effects.spawnParticle requires a location." },
                    "location",
                )
                val players = member(spec, "players")?.let { parsePlayers(it, "players") } ?: emptyList()
                hostServices.spawnParticle(
                    location = location,
                    particle = particle,
                    count = memberInt(spec, "count", required = false) ?: 1,
                    offsetX = memberDouble(spec, "offsetX", required = false) ?: 0.0,
                    offsetY = memberDouble(spec, "offsetY", required = false) ?: 0.0,
                    offsetZ = memberDouble(spec, "offsetZ", required = false) ?: 0.0,
                    extra = memberDouble(spec, "extra", required = false) ?: 0.0,
                    players = players,
                )
                null
            },
            "applyPotion" to executable { arguments ->
                val spec = requireNotNull(firstArg(arguments, "potion spec"))
                hostServices.applyPotionEffect(
                    player = requireHostPlayer(
                        requireNotNull(member(spec, "player")) { "effects.applyPotion requires a player." },
                        "player",
                    ),
                    effect = memberString(spec, "effect", required = true)
                        ?: error("effects.applyPotion requires an effect."),
                    durationTicks = memberInt(spec, "durationTicks", required = true)
                        ?: error("effects.applyPotion requires durationTicks."),
                    amplifier = memberInt(spec, "amplifier", required = false) ?: 0,
                    ambient = memberBoolean(spec, "ambient", default = false),
                    particles = memberBoolean(spec, "particles", default = true),
                    icon = memberBoolean(spec, "icon", default = true),
                )
            },
            "clearPotion" to executable { arguments ->
                require(arguments.isNotEmpty()) { "effects.clearPotion requires a player." }
                val player = requireHostPlayer(arguments[0], "player")
                val effect = if (arguments.size > 1 && !arguments[1].isNull) textValue(arguments[1]) else null
                hostServices.clearPotionEffect(player, effect)
                null
            },
        )

    private fun createRecipes(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "register" to executable { arguments ->
                val spec = parseRecipeSpec(arguments, 0, "recipe")
                val registration = hostServices.registerRecipe(spec)
                mapOf(
                    "id" to spec.id,
                    "unregister" to executable {
                        registration.unregister()
                        null
                    },
                )
            },
        )

    private fun createBossBars(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "create" to executable { arguments ->
                val spec = requireNotNull(firstArg(arguments, "boss bar spec"))
                val handle = hostServices.createBossBar(
                    id = memberString(spec, "id", required = false),
                    title = memberText(spec, "title", required = true)
                        ?: error("bossBars.create requires a title."),
                    color = memberString(spec, "color", required = false) ?: "purple",
                    style = memberString(spec, "style", required = false) ?: "solid",
                )
                memberDouble(spec, "progress", required = false)?.let(handle::setProgress)
                handle
            },
        )

    private fun createScoreboards(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "create" to executable { arguments ->
                val spec = requireNotNull(firstArg(arguments, "scoreboard spec"))
                hostServices.createScoreboard(
                    id = memberString(spec, "id", required = false),
                    title = memberText(spec, "title", required = true)
                        ?: error("scoreboards.create requires a title."),
                )
            },
        )

    private fun createStorage(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "plugin" to createStore(hostServices.storage()),
            "files" to createDataDir(hostServices.dataDirectory()),
        )

    private fun createConfig(configStore: ConfigStore): Map<String, Any?> =
        mapOf(
            "get" to executable { arguments -> configStore.get(stringArg(arguments, 0, "path")) },
            "set" to executable { arguments ->
                require(arguments.size >= 2) { "config.set requires a path and value." }
                configStore.set(stringArg(arguments, 0, "path"), JsValues.toJavaValue(arguments[1]))
                null
            },
            "save" to executable {
                configStore.save()
                null
            },
            "reload" to executable {
                configStore.reload()
                null
            },
            "keys" to executable { configStore.keys() },
        )

    private fun createHttp(httpService: HttpService, promiseConstructor: Value?): Map<String, Any?> =
        mapOf(
            "fetch" to executable { arguments ->
                val request = parseHttpRequest(arguments)
                if (promiseConstructor == null) {
                    httpResponseObject(httpService.fetch(request))
                } else {
                    createPromise(promiseConstructor) { resolve, reject ->
                        try {
                            httpService.fetchAsync(
                                request,
                                onSuccess = Callback { payload ->
                                    val response = payload as? HttpResponsePayload
                                        ?: error("Expected an HTTP response payload.")
                                    resolve.execute(toGuestValue(httpResponseObject(response)))
                                    null
                                },
                                onError = Callback { payload ->
                                    reject.execute(httpErrorMessage(payload))
                                    null
                                },
                            )
                        } catch (error: Exception) {
                            reject.execute(error.message ?: error.toString())
                        }
                    }
                }
            },
        )

    private fun createUnsafe(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "events" to mapOf(
                "onJava" to executable { arguments ->
                    require(arguments.size >= 2) { "unsafe.events.onJava requires an event class name and handler." }
                    val eventClassName = stringArg(arguments, 0, "eventClassName")
                    val handler = arguments[1]
                    requireExecutable(handler, "handler")
                    registrationHandle(
                        hostServices.registerJavaEvent(eventClassName) { payload ->
                            executeCallback(handler, payload)
                        },
                    )
                },
            ),
            "java" to mapOf(
                "type" to executable { arguments ->
                    StaticHostType(hostServices.javaType(stringArg(arguments, 0, "className")))
                },
            ),
            "backend" to mapOf(
                "server" to hostServices.serverHandle(),
                "plugin" to hostServices.pluginHandle(),
                "console" to hostServices.consoleSenderHandle(),
                "dispatchCommand" to executable { arguments ->
                    hostServices.dispatchConsoleCommand(stringArg(arguments, 0, "command"))
                },
            ),
        )

    private fun createStore(store: KeyValueStore): Map<String, Any?> =
        mapOf(
            "get" to executable { arguments -> store.get(stringArg(arguments, 0, "path")) },
            "set" to executable { arguments ->
                require(arguments.size >= 2) { "store.set requires a path and value." }
                store.set(stringArg(arguments, 0, "path"), JsValues.toJavaValue(arguments[1]))
                null
            },
            "delete" to executable { arguments ->
                store.delete(stringArg(arguments, 0, "path"))
                null
            },
            "save" to executable {
                store.save()
                null
            },
            "reload" to executable {
                store.reload()
                null
            },
            "keys" to executable { store.keys() },
        )

    private fun createDataDir(dataDirectory: DataDirectory): Map<String, Any?> =
        mapOf(
            "path" to dataDirectory.path(),
            "resolve" to executable { arguments -> dataDirectory.resolve(*stringArray(arguments)) },
            "readText" to executable { arguments -> dataDirectory.readText(stringArg(arguments, 0, "relativePath")) },
            "writeText" to executable { arguments ->
                dataDirectory.writeText(
                    stringArg(arguments, 0, "relativePath"),
                    textArg(arguments, 1, "contents"),
                )
                null
            },
            "exists" to executable { arguments -> dataDirectory.exists(stringArg(arguments, 0, "relativePath")) },
            "mkdirs" to executable { arguments ->
                dataDirectory.mkdirs(
                    if (arguments.isNotEmpty()) stringArg(arguments, 0, "relativePath") else "",
                )
                null
            },
        )

    private fun playerObject(player: HostPlayer): Map<String, Any?> =
        mapOf(
            "__lapisHostRef" to player,
            "id" to player.id(),
            "name" to player.name(),
            "worldName" to executable { player.worldName() },
            "location" to executable { player.location() },
            "sendMessage" to executable { arguments ->
                player.sendMessage(textArg(arguments, 0, "message"))
                null
            },
            "actionBar" to executable { arguments ->
                player.sendActionBar(textArg(arguments, 0, "message"))
                null
            },
            "showTitle" to executable { arguments ->
                val title = textArg(arguments, 0, "title")
                val options = if (arguments.size > 1) arguments[1] else null
                val subtitle = options?.let { memberText(it, "subtitle", required = false) } ?: ""
                val fadeIn = options?.let { memberInt(it, "fadeInTicks", required = false) } ?: 10
                val stay = options?.let { memberInt(it, "stayTicks", required = false) } ?: 70
                val fadeOut = options?.let { memberInt(it, "fadeOutTicks", required = false) } ?: 20
                player.showTitle(title, subtitle, fadeIn, stay, fadeOut)
                null
            },
            "hasPermission" to executable { arguments ->
                player.hasPermission(stringArg(arguments, 0, "permission"))
            },
            "teleport" to executable { arguments ->
                player.teleport(parseLocation(arguments, 0, "location"))
            },
            "inventory" to executable { player.inventory() },
            "tags" to player.tags(),
            "unsafe" to mapOf("handle" to player.backendHandle()),
        )

    private fun worldObject(world: HostWorld): Map<String, Any?> =
        mapOf(
            "__lapisHostRef" to world,
            "name" to world.name(),
            "environment" to world.environment(),
            "players" to executable { world.players() },
            "entities" to executable { world.entities() },
            "spawnLocation" to executable { world.spawnLocation() },
            "time" to executable { world.time() },
            "setTime" to executable { arguments ->
                world.setTime(longArg(arguments, 0, "time"))
                null
            },
            "storming" to executable { world.storming() },
            "setStorming" to executable { arguments ->
                require(arguments.isNotEmpty() && arguments[0].isBoolean) { "Expected boolean argument \"storming\"." }
                world.setStorming(arguments[0].asBoolean())
                null
            },
            "blockAt" to executable { arguments ->
                world.blockAt(parseLocation(arguments, 0, "location"))
            },
            "unsafe" to mapOf("handle" to world.backendHandle()),
        )

    private fun entityObject(entity: HostEntity): Map<String, Any?> =
        mapOf(
            "__lapisHostRef" to entity,
            "id" to entity.id(),
            "type" to entity.type(),
            "name" to entity.customName(),
            "worldName" to executable { entity.worldName() },
            "location" to executable { entity.location() },
            "teleport" to executable { arguments ->
                entity.teleport(parseLocation(arguments, 0, "location"))
            },
            "remove" to executable {
                entity.remove()
                null
            },
            "tags" to entity.tags(),
            "unsafe" to mapOf("handle" to entity.backendHandle()),
        )

    private fun blockObject(block: HostBlock): Map<String, Any?> =
        mapOf(
            "__lapisHostRef" to block,
            "type" to block.type(),
            "worldName" to block.worldName(),
            "location" to block.location(),
            "setType" to executable { arguments ->
                block.setType(stringArg(arguments, 0, "type"))
            },
            "unsafe" to mapOf("handle" to block.backendHandle()),
        )

    private fun itemObject(item: HostItem): Map<String, Any?> =
        mapOf(
            "__lapisHostRef" to item,
            "type" to item.type(),
            "amount" to executable { item.amount() },
            "setAmount" to executable { arguments ->
                item.setAmount(intArg(arguments, 0, "amount"))
                null
            },
            "name" to executable { item.displayName() },
            "setName" to executable { arguments ->
                item.setDisplayName(if (arguments.isEmpty() || arguments[0].isNull) null else textArg(arguments, 0, "name"))
                null
            },
            "lore" to executable { item.lore() },
            "setLore" to executable { arguments ->
                item.setLore(textList(arguments, 0, "lore"))
                null
            },
            "enchantments" to executable { item.enchantments() },
            "enchant" to executable { arguments ->
                item.setEnchantment(stringArg(arguments, 0, "key"), intArg(arguments, 1, "level"))
                null
            },
            "removeEnchantment" to executable { arguments ->
                item.removeEnchantment(stringArg(arguments, 0, "key"))
                null
            },
            "clone" to executable { item.cloneItem() },
            "tags" to item.tags(),
            "unsafe" to mapOf("handle" to item.backendHandle()),
        )

    private fun inventoryObject(inventory: HostInventory): Map<String, Any?> =
        mapOf(
            "__lapisHostRef" to inventory,
            "id" to inventory.id(),
            "title" to inventory.title(),
            "size" to inventory.size(),
            "get" to executable { arguments ->
                inventory.getItem(intArg(arguments, 0, "slot"))
            },
            "set" to executable { arguments ->
                val slot = intArg(arguments, 0, "slot")
                val item = if (arguments.size < 2 || arguments[1].isNull) {
                    null
                } else {
                    resolveItem(arguments[1], "item")
                }
                inventory.setItem(slot, item)
                null
            },
            "add" to executable { arguments ->
                inventory.addItem(resolveItem(requireArg(arguments, 0, "item"), "item"))
                null
            },
            "clear" to executable { arguments ->
                if (arguments.isEmpty() || arguments[0].isNull) {
                    inventory.clear()
                } else {
                    inventory.clearSlot(intArg(arguments, 0, "slot"))
                }
                null
            },
            "open" to executable { arguments ->
                inventory.open(requireHostPlayer(arguments[0], "player"))
                null
            },
            "unsafe" to mapOf("handle" to inventory.backendHandle()),
        )

    private fun bossBarObject(bossBar: HostBossBar): Map<String, Any?> =
        mapOf(
            "__lapisHostRef" to bossBar,
            "id" to bossBar.id(),
            "title" to executable { bossBar.title() },
            "setTitle" to executable { arguments ->
                bossBar.setTitle(textArg(arguments, 0, "title"))
                null
            },
            "progress" to executable { bossBar.progress() },
            "setProgress" to executable { arguments ->
                bossBar.setProgress(doubleArg(arguments, 0, "progress"))
                null
            },
            "color" to executable { bossBar.color() },
            "setColor" to executable { arguments ->
                bossBar.setColor(stringArg(arguments, 0, "color"))
                null
            },
            "style" to executable { bossBar.style() },
            "setStyle" to executable { arguments ->
                bossBar.setStyle(stringArg(arguments, 0, "style"))
                null
            },
            "players" to executable { bossBar.players() },
            "addPlayer" to executable { arguments ->
                bossBar.addPlayer(requireHostPlayer(arguments[0], "player"))
                null
            },
            "removePlayer" to executable { arguments ->
                bossBar.removePlayer(requireHostPlayer(arguments[0], "player"))
                null
            },
            "clearPlayers" to executable {
                bossBar.removeAllPlayers()
                null
            },
            "delete" to executable {
                bossBar.close()
                null
            },
            "unsafe" to mapOf("handle" to bossBar.backendHandle()),
        )

    private fun scoreboardObject(scoreboard: HostScoreboard): Map<String, Any?> =
        mapOf(
            "__lapisHostRef" to scoreboard,
            "id" to scoreboard.id(),
            "title" to executable { scoreboard.title() },
            "setTitle" to executable { arguments ->
                scoreboard.setTitle(textArg(arguments, 0, "title"))
                null
            },
            "setLine" to executable { arguments ->
                scoreboard.setLine(intArg(arguments, 0, "score"), textArg(arguments, 1, "text"))
                null
            },
            "removeLine" to executable { arguments ->
                scoreboard.removeLine(intArg(arguments, 0, "score"))
                null
            },
            "clear" to executable {
                scoreboard.clear()
                null
            },
            "viewers" to executable { scoreboard.viewers() },
            "show" to executable { arguments ->
                scoreboard.show(requireHostPlayer(arguments[0], "player"))
                null
            },
            "hide" to executable { arguments ->
                scoreboard.hide(requireHostPlayer(arguments[0], "player"))
                null
            },
            "delete" to executable {
                scoreboard.close()
                null
            },
            "unsafe" to mapOf("handle" to scoreboard.backendHandle()),
        )

    private fun locationObject(location: HostLocation): Map<String, Any?> =
        mapOf(
            "world" to location.world,
            "x" to location.x,
            "y" to location.y,
            "z" to location.z,
            "yaw" to location.yaw.toDouble(),
            "pitch" to location.pitch.toDouble(),
        )

    private fun registrationHandle(handle: AutoCloseable): Map<String, Any?> =
        mapOf(
            "unsubscribe" to executable {
                handle.close()
                null
            },
        )

    private fun taskHandle(handle: TaskHandle): Map<String, Any?> =
        mapOf(
            "cancel" to executable {
                handle.cancel()
                null
            },
        )

    private fun taskCallback(arguments: Array<out Value>, index: Int, methodName: String): Callback {
        require(arguments.size > index) { "$methodName requires a task callback." }
        val task = arguments[index]
        requireExecutable(task, "task")
        return Callback { payload -> executeCallback(task, payload) }
    }

    private fun executeCallback(callback: Value, payload: Any?): Any? {
        val result = if (payload == null) {
            callback.execute()
        } else {
            callback.execute(toGuestValue(payload))
        }
        return JsValues.toJavaValue(result)
    }

    private fun toGuestValue(value: Any?): Any? =
        when (value) {
            null -> null
            is ProxyObject, is ProxyArray, is ProxyExecutable -> value
            is Callback -> executable { arguments ->
                value.invoke(
                    if (arguments.isEmpty()) {
                        null
                    } else {
                        JsValues.toJavaValue(arguments[0])
                    },
                )
            }
            is AppDescriptor -> createAppDescriptorValue(value)
            is KeyValueStore -> createStore(value)
            is DataDirectory -> createDataDir(value)
            is HostPlayer -> playerObject(value)
            is HostWorld -> worldObject(value)
            is HostEntity -> entityObject(value)
            is HostBlock -> blockObject(value)
            is HostItem -> itemObject(value)
            is HostInventory -> inventoryObject(value)
            is HostBossBar -> bossBarObject(value)
            is HostScoreboard -> scoreboardObject(value)
            is HostLocation -> locationObject(value)
            is Map<*, *> -> ProxyObject.fromMap(
                value.entries.associate { (key, nestedValue) ->
                    key.toString() to toGuestValue(nestedValue)
                },
            )
            is List<*> -> ProxyArray.fromList(value.map(::toGuestValue))
            else -> value
        }

    private fun createAppDescriptorValue(app: AppDescriptor): Map<String, Any?> =
        mapOf(
            "id" to app.id,
            "name" to app.name,
            "version" to app.version,
            "engine" to app.engine,
            "apiVersion" to app.apiVersion,
            "backend" to app.backend,
            "runtime" to app.runtime,
        )

    private fun resolveItem(value: Value, field: String): HostItem {
        hostRef(value)?.let { host ->
            require(host is HostItem) { "Expected $field to be an item handle." }
            return host
        }
        error("Expected $field to be an item created by lapis.items.create(...).")
    }

    private fun requireHostPlayer(value: Value, field: String): HostPlayer {
        val host = hostRef(value)
        require(host is HostPlayer) { "Expected $field to be a player handle." }
        return host
    }

    private fun requireHostInventory(value: Value, field: String): HostInventory {
        val host = hostRef(value)
        require(host is HostInventory) { "Expected $field to be an inventory handle." }
        return host
    }

    private fun requireHostBossBar(value: Value, field: String): HostBossBar {
        val host = hostRef(value)
        require(host is HostBossBar) { "Expected $field to be a boss bar handle." }
        return host
    }

    private fun requireHostScoreboard(value: Value, field: String): HostScoreboard {
        val host = hostRef(value)
        require(host is HostScoreboard) { "Expected $field to be a scoreboard handle." }
        return host
    }

    private fun hostRef(value: Value): Any? {
        if (value.isHostObject) {
            return unwrapHostReference(value.asHostObject<Any>())
        }

        val hostMember = member(value, "__lapisHostRef")
        if (hostMember != null && hostMember.isHostObject) {
            return unwrapHostReference(hostMember.asHostObject<Any>())
        }

        return null
    }

    private fun unwrapHostReference(host: Any?): Any? =
        when (host) {
            is Map<*, *> -> host["__lapisHostRef"] ?: host
            else -> host
        }

    private fun parseCommandRegistration(arguments: Array<out Value>): CommandRegistration =
        when {
            arguments.isEmpty() -> error("commands.register requires a command definition.")
            arguments[0].isString -> {
                require(arguments.size >= 2) { "commands.register(name, execute, ...) requires an execute callback." }
                val execute = arguments[1]
                requireExecutable(execute, "execute")

                CommandRegistration(
                    name = arguments[0].asString(),
                    execute = execute,
                    description = optionalTextArg(arguments, 2),
                    usage = optionalTextArg(arguments, 3),
                    aliases = optionalStringArray(arguments, 4),
                    permission = optionalStringArg(arguments, 5),
                )
            }
            else -> {
                val spec = arguments[0]
                val execute = requireNotNull(member(spec, "execute")) {
                    "commands.register requires an execute callback."
                }
                requireExecutable(execute, "execute")

                CommandRegistration(
                    name = memberString(spec, "name", required = true) ?: error("Command name is required."),
                    execute = execute,
                    description = memberText(spec, "description", required = false) ?: "",
                    usage = memberText(spec, "usage", required = false) ?: "",
                    aliases = memberStringList(spec, "aliases"),
                    permission = memberString(spec, "permission", required = false),
                )
            }
        }

    private fun parseItemSpec(arguments: Array<out Value>, index: Int, name: String): HostItemSpec {
        val value = requireArg(arguments, index, name)
        return parseItemSpec(value, name)
    }

    private fun parseItemSpec(value: Value, name: String): HostItemSpec =
        HostItemSpec(
            type = memberString(value, "type", required = true) ?: error("Expected $name.type."),
            amount = memberInt(value, "amount", required = false) ?: 1,
            displayName = memberText(value, "name", required = false),
            lore = memberTextList(value, "lore"),
            enchantments = memberIntMap(value, "enchantments"),
        )

    private fun parseLocation(arguments: Array<out Value>, index: Int, name: String): HostLocation =
        parseLocation(requireArg(arguments, index, name), name)

    private fun parseLocation(value: Value, name: String): HostLocation =
        HostLocation(
            world = memberString(value, "world", required = false),
            x = memberDouble(value, "x", required = true) ?: error("Expected $name.x."),
            y = memberDouble(value, "y", required = true) ?: error("Expected $name.y."),
            z = memberDouble(value, "z", required = true) ?: error("Expected $name.z."),
            yaw = (memberDouble(value, "yaw", required = false) ?: 0.0).toFloat(),
            pitch = (memberDouble(value, "pitch", required = false) ?: 0.0).toFloat(),
        )

    private fun parsePlayers(value: Value, field: String): List<HostPlayer> {
        require(value.hasArrayElements()) { "Expected array field \"$field\"." }
        return List(value.arraySize.toInt()) { index ->
            requireHostPlayer(value.getArrayElement(index.toLong()), "$field[$index]")
        }
    }

    private fun parseRecipeSpec(arguments: Array<out Value>, index: Int, name: String): HostRecipeSpec =
        parseRecipeSpec(requireArg(arguments, index, name), name)

    private fun parseRecipeSpec(value: Value, name: String): HostRecipeSpec {
        val kind = memberString(value, "kind", required = true)
            ?: error("Expected $name.kind.")
        val id = memberString(value, "id", required = true)
            ?: error("Expected $name.id.")
        val result = parseItemSpec(
            requireNotNull(member(value, "result")) { "Expected $name.result." },
            "$name.result",
        )

        return when (kind.lowercase()) {
            "shaped" -> {
                val shape = memberStringList(value, "shape")
                require(shape.isNotEmpty()) { "Expected non-empty array field \"$name.shape\"." }
                val ingredientsValue = requireNotNull(member(value, "ingredients")) {
                    "Expected object field \"$name.ingredients\"."
                }
                val ingredients = linkedMapOf<Char, HostRecipeIngredient>()
                when {
                    ingredientsValue.hasMembers() -> {
                        ingredientsValue.memberKeys.forEach { key ->
                            require(key.length == 1) { "Shaped recipe key \"$key\" must be a single character." }
                            ingredients[key.first()] = parseRecipeIngredient(
                                requireNotNull(ingredientsValue.getMember(key)),
                                "$name.ingredients.$key",
                            )
                        }
                    }
                    ingredientsValue.hasHashEntries() -> {
                        val iterator = ingredientsValue.hashEntriesIterator
                        while (iterator.hasIteratorNextElement()) {
                            val entry = iterator.iteratorNextElement
                            val key = textValue(entry.getArrayElement(0))
                            require(key.length == 1) { "Shaped recipe key \"$key\" must be a single character." }
                            ingredients[key.first()] = parseRecipeIngredient(
                                entry.getArrayElement(1),
                                "$name.ingredients.$key",
                            )
                        }
                    }
                    else -> error("Expected object field \"$name.ingredients\".")
                }

                HostShapedRecipeSpec(
                    id = id,
                    result = result,
                    shape = shape,
                    ingredients = ingredients,
                )
            }
            "shapeless" -> {
                val ingredientsValue = requireNotNull(member(value, "ingredients")) {
                    "Expected array field \"$name.ingredients\"."
                }
                require(ingredientsValue.hasArrayElements()) { "Expected array field \"$name.ingredients\"." }
                HostShapelessRecipeSpec(
                    id = id,
                    result = result,
                    ingredients = List(ingredientsValue.arraySize.toInt()) { elementIndex ->
                        parseRecipeIngredient(
                            ingredientsValue.getArrayElement(elementIndex.toLong()),
                            "$name.ingredients[$elementIndex]",
                )
            },
        )

    private fun httpResponseObject(response: HttpResponsePayload): Map<String, Any?> =
        mapOf(
            "url" to response.url,
            "status" to response.status,
            "ok" to response.ok,
            "headers" to response.headers,
            "body" to response.body,
            "text" to executable { response.body },
        )
            }
            else -> error("Unsupported recipe kind \"$kind\".")
        }
    }

    private fun parseRecipeIngredient(value: Value, name: String): HostRecipeIngredient =
        when {
            value.isString -> HostMaterialIngredient(value.asString())
            value.hasMembers() || value.hasHashEntries() -> HostExactItemIngredient(parseItemSpec(value, name))
            else -> error("Expected recipe ingredient \"$name\" to be a material string or item spec.")
        }

    private fun parseHttpRequest(arguments: Array<out Value>): HttpRequestSpec =
        when {
            arguments.isEmpty() -> error("http.fetch requires a URL or request definition.")
            arguments[0].isString -> {
                val url = arguments[0].asString()
                val init = arguments.getOrNull(1)
                HttpRequestSpec(
                    url = url,
                    method = init?.takeUnless(Value::isNull)
                        ?.let { memberString(it, "method", required = false) }
                        ?.uppercase()
                        ?: "GET",
                    headers = init?.takeUnless(Value::isNull)?.let { memberStringMap(it, "headers") } ?: emptyMap(),
                    body = init?.takeUnless(Value::isNull)?.let { memberText(it, "body", required = false) },
                )
            }
            else -> {
                val value = arguments[0]
                HttpRequestSpec(
                    url = memberString(value, "url", required = true) ?: error("http.fetch requires a URL."),
                    method = memberString(value, "method", required = false)?.uppercase() ?: "GET",
                    headers = memberStringMap(value, "headers"),
                    body = memberText(value, "body", required = false),
                )
            }
        }

    private fun createPromise(
        promiseConstructor: Value,
        executor: (resolve: Value, reject: Value) -> Unit,
    ): Value =
        promiseConstructor.newInstance(
            executable { arguments ->
                require(arguments.size >= 2) { "Promise executor requires resolve and reject callbacks." }
                val resolve = arguments[0]
                val reject = arguments[1]
                requireExecutable(resolve, "resolve")
                requireExecutable(reject, "reject")
                executor(resolve, reject)
                null
            },
        )

    private fun executable(executable: Executable): ProxyExecutable =
        ProxyExecutable { arguments ->
            try {
                toGuestValue(executable.execute(arguments))
            } catch (error: RuntimeException) {
                throw error
            } catch (error: Exception) {
                throw IllegalStateException(error)
            }
        }

    private fun requireExecutable(value: Value?, field: String) {
        require(value != null && value.canExecute()) { "Expected executable field \"$field\"." }
    }

    private fun firstArg(arguments: Array<out Value>, name: String): Value? {
        require(arguments.isNotEmpty()) { "Expected $name." }
        return arguments[0]
    }

    private fun requireArg(arguments: Array<out Value>, index: Int, name: String): Value {
        require(arguments.size > index) { "Expected argument \"$name\"." }
        return arguments[index]
    }

    private fun member(value: Value, field: String): Value? =
        when {
            value.hasMembers() && value.hasMember(field) -> value.getMember(field)
            value.hasHashEntries() && value.hasHashEntry(field) -> value.getHashValue(field)
            else -> null
        }

    private fun memberString(value: Value, field: String, required: Boolean): String? {
        val member = member(value, field)
        if (member == null || member.isNull) {
            require(!required) { "Expected string field \"$field\"." }
            return null
        }

        require(member.isString) { "Expected string field \"$field\"." }
        return member.asString()
    }

    private fun memberText(value: Value, field: String, required: Boolean): String? {
        val member = member(value, field)
        if (member == null || member.isNull) {
            require(!required) { "Expected text field \"$field\"." }
            return null
        }
        return textValue(member)
    }

    private fun memberTextList(value: Value, field: String): List<String> {
        val member = member(value, field) ?: return emptyList()
        if (member.isNull) {
            return emptyList()
        }
        require(member.hasArrayElements()) { "Expected array field \"$field\"." }
        return List(member.arraySize.toInt()) { index ->
            textValue(member.getArrayElement(index.toLong()))
        }
    }

    private fun memberStringList(value: Value, field: String): List<String> {
        val member = member(value, field) ?: return emptyList()
        if (member.isNull) {
            return emptyList()
        }
        require(member.hasArrayElements()) { "Expected array field \"$field\"." }
        return List(member.arraySize.toInt()) { index ->
            val element = member.getArrayElement(index.toLong())
            require(element.isString) { "Expected string field \"$field\" element." }
            element.asString()
        }
    }

    private fun memberIntMap(value: Value, field: String): Map<String, Int> {
        val member = member(value, field) ?: return emptyMap()
        if (member.isNull) {
            return emptyMap()
        }

        val result = linkedMapOf<String, Int>()
        when {
            member.hasMembers() -> {
                member.memberKeys.forEach { key ->
                    val nested = member.getMember(key)
                    require(nested.fitsInInt()) { "Expected integer field \"$field.$key\"." }
                    result[key] = nested.asInt()
                }
            }
            member.hasHashEntries() -> {
                val iterator = member.hashEntriesIterator
                while (iterator.hasIteratorNextElement()) {
                    val entry = iterator.iteratorNextElement
                    val key = entry.getArrayElement(0).asString()
                    val nested = entry.getArrayElement(1)
                    require(nested.fitsInInt()) { "Expected integer field \"$field.$key\"." }
                    result[key] = nested.asInt()
                }
            }
            else -> require(false) { "Expected object field \"$field\"." }
        }
        return result
    }

    private fun memberStringMap(value: Value, field: String): Map<String, String> {
        val member = member(value, field) ?: return emptyMap()
        if (member.isNull) {
            return emptyMap()
        }

        val result = linkedMapOf<String, String>()
        when {
            member.hasMembers() -> {
                member.memberKeys.forEach { key ->
                    result[key] = textValue(requireNotNull(member.getMember(key)))
                }
            }
            member.hasHashEntries() -> {
                val iterator = member.hashEntriesIterator
                while (iterator.hasIteratorNextElement()) {
                    val entry = iterator.iteratorNextElement
                    result[textValue(entry.getArrayElement(0))] = textValue(entry.getArrayElement(1))
                }
            }
            else -> require(false) { "Expected object field \"$field\"." }
        }

        return result
    }

    private fun memberInt(value: Value, field: String, required: Boolean): Int? {
        val member = member(value, field)
        if (member == null || member.isNull) {
            require(!required) { "Expected integer field \"$field\"." }
            return null
        }
        require(member.fitsInInt()) { "Expected integer field \"$field\"." }
        return member.asInt()
    }

    private fun memberDouble(value: Value, field: String, required: Boolean): Double? {
        val member = member(value, field)
        if (member == null || member.isNull) {
            require(!required) { "Expected number field \"$field\"." }
            return null
        }
        require(member.fitsInDouble()) { "Expected number field \"$field\"." }
        return member.asDouble()
    }

    private fun memberBoolean(value: Value, field: String, default: Boolean): Boolean {
        val member = member(value, field) ?: return default
        if (member.isNull) {
            return default
        }
        require(member.isBoolean) { "Expected boolean field \"$field\"." }
        return member.asBoolean()
    }

    private fun stringArg(arguments: Array<out Value>, index: Int, name: String): String {
        require(arguments.size > index && arguments[index].isString) {
            "Expected string argument \"$name\"."
        }
        return arguments[index].asString()
    }

    private fun optionalStringArg(arguments: Array<out Value>, index: Int): String? {
        if (arguments.size <= index || arguments[index].isNull) {
            return null
        }
        require(arguments[index].isString) { "Expected string argument at position ${index + 1}." }
        return arguments[index].asString()
    }

    private fun optionalTextArg(arguments: Array<out Value>, index: Int): String {
        if (arguments.size <= index || arguments[index].isNull) {
            return ""
        }
        return textValue(arguments[index])
    }

    private fun optionalStringArray(arguments: Array<out Value>, index: Int): List<String> {
        if (arguments.size <= index || arguments[index].isNull) {
            return emptyList()
        }

        val value = arguments[index]
        require(value.hasArrayElements()) {
            "Expected array argument at position ${index + 1}."
        }
        return List(value.arraySize.toInt()) { elementIndex ->
            val element = value.getArrayElement(elementIndex.toLong())
            require(element.isString) { "Expected string element at position ${elementIndex + 1}." }
            element.asString()
        }
    }

    private fun longArg(arguments: Array<out Value>, index: Int, name: String): Long {
        require(arguments.size > index && arguments[index].fitsInLong()) {
            "Expected integer argument \"$name\"."
        }
        return arguments[index].asLong()
    }

    private fun intArg(arguments: Array<out Value>, index: Int, name: String): Int {
        require(arguments.size > index && arguments[index].fitsInInt()) {
            "Expected integer argument \"$name\"."
        }
        return arguments[index].asInt()
    }

    private fun doubleArg(arguments: Array<out Value>, index: Int, name: String): Double {
        require(arguments.size > index && arguments[index].fitsInDouble()) {
            "Expected number argument \"$name\"."
        }
        return arguments[index].asDouble()
    }

    private fun textArg(arguments: Array<out Value>, index: Int, name: String): String {
        require(arguments.size > index) { "Expected argument \"$name\"." }
        return textValue(arguments[index])
    }

    private fun textList(arguments: Array<out Value>, index: Int, name: String): List<String> {
        require(arguments.size > index) { "Expected argument \"$name\"." }
        val value = arguments[index]
        require(value.hasArrayElements()) { "Expected array argument \"$name\"." }
        return List(value.arraySize.toInt()) { elementIndex ->
            textValue(value.getArrayElement(elementIndex.toLong()))
        }
    }

    private fun stringArray(arguments: Array<out Value>): Array<String> =
        Array(arguments.size) { index ->
            require(arguments[index].isString) { "Expected string path segment at position ${index + 1}." }
            arguments[index].asString()
        }

    private fun textValue(value: Value): String {
        if (value.isNull) {
            return ""
        }

        if (value.isString) {
            return value.asString()
        }

        if (value.hasArrayElements()) {
            return List(value.arraySize.toInt()) { index ->
                textValue(value.getArrayElement(index.toLong()))
            }.joinToString("")
        }

        val textMember = member(value, "text")
        if (textMember != null) {
            val pieces = mutableListOf<String>()
            pieces += textValue(textMember)
            val extra = member(value, "extra")
            if (extra != null && !extra.isNull && extra.hasArrayElements()) {
                pieces += List(extra.arraySize.toInt()) { index ->
                    textValue(extra.getArrayElement(index.toLong()))
                }
            }
            return pieces.joinToString("")
        }

        return value.toString()
    }

    private fun httpErrorMessage(payload: Any?): String =
        when (payload) {
            null -> "HTTP request failed."
            is Throwable -> payload.message ?: payload.toString()
            else -> payload.toString()
        }

    private fun invokeStaticMethod(type: Class<*>, methodName: String, arguments: Array<out Value>): Any? {
        val overloads = type.methods.filter { method ->
            method.name == methodName && Modifier.isStatic(method.modifiers)
        }

        require(overloads.isNotEmpty()) {
            "Unknown static method ${type.name}.$methodName"
        }

        for (method in overloads) {
            val convertedArguments = convertArguments(method, arguments) ?: continue
            return method.invoke(null, *convertedArguments)
        }

        error(
            "No matching overload for ${type.name}.$methodName(${arguments.joinToString { argument ->
                when {
                    argument.isNull -> "null"
                    argument.isHostObject -> argument.asHostObject<Any>()::class.java.name
                    argument.isString -> "String"
                    argument.isBoolean -> "Boolean"
                    argument.fitsInLong() -> "Long"
                    argument.fitsInDouble() -> "Double"
                    else -> "Value"
                }
            }})",
        )
    }

    private fun convertArguments(method: Method, arguments: Array<out Value>): Array<Any?>? {
        if (method.parameterCount != arguments.size || method.isVarArgs) {
            return null
        }

        return Array(arguments.size) { index ->
            convertArgument(arguments[index], method.parameterTypes[index]) ?: return null
        }
    }

    private fun convertArgument(argument: Value, targetType: Class<*>): Any? {
        if (argument.isNull) {
            return if (targetType.isPrimitive) null else null
        }

        if (argument.isHostObject) {
            val hostObject = argument.asHostObject<Any>()
            if (targetType.isInstance(hostObject)) {
                return hostObject
            }
        }

        return when (targetType) {
            String::class.java -> argument.takeIf(Value::isString)?.asString()
            Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> argument.takeIf(Value::isBoolean)?.asBoolean()
            Int::class.javaPrimitiveType, Int::class.javaObjectType -> argument.takeIf(Value::fitsInInt)?.asInt()
            Long::class.javaPrimitiveType, Long::class.javaObjectType -> argument.takeIf(Value::fitsInLong)?.asLong()
            Double::class.javaPrimitiveType, Double::class.javaObjectType -> argument.takeIf(Value::fitsInDouble)?.asDouble()
            Float::class.javaPrimitiveType, Float::class.javaObjectType -> argument.takeIf(Value::fitsInDouble)?.asDouble()?.toFloat()
            Short::class.javaPrimitiveType, Short::class.javaObjectType -> argument.takeIf(Value::fitsInInt)?.asInt()?.toShort()
            Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> argument.takeIf(Value::fitsInInt)?.asInt()?.toByte()
            Char::class.javaPrimitiveType, Char::class.javaObjectType -> argument
                .takeIf(Value::isString)
                ?.asString()
                ?.takeIf { it.length == 1 }
                ?.first()
            else -> {
                val javaValue = JsValues.toJavaValue(argument)
                if (javaValue == null) {
                    if (targetType.isPrimitive) null else null
                } else if (targetType.isInstance(javaValue)) {
                    javaValue
                } else {
                    null
                }
            }
        }
    }

    private fun interface Executable {
        @Throws(Exception::class)
        fun execute(arguments: Array<out Value>): Any?
    }

    private data class CommandRegistration(
        val name: String,
        val execute: Value,
        val description: String,
        val usage: String,
        val aliases: List<String>,
        val permission: String?,
    )

    private inner class StaticHostType(
        private val type: Class<*>,
    ) : ProxyObject {
        private val staticMethodNames = type.methods
            .filter { Modifier.isStatic(it.modifiers) }
            .map(Method::getName)
            .toSet()
        private val staticFields = type.fields
            .filter { Modifier.isStatic(it.modifiers) }
            .associateBy { it.name }

        override fun getMember(key: String): Any? {
            if (staticMethodNames.contains(key)) {
                return executable { arguments -> invokeStaticMethod(type, key, arguments) }
            }

            return staticFields[key]?.get(null)
        }

        override fun getMemberKeys(): Any =
            (staticMethodNames + staticFields.keys).sorted().toTypedArray()

        override fun hasMember(key: String): Boolean =
            staticMethodNames.contains(key) || staticFields.containsKey(key)

        override fun putMember(key: String, value: Value) {
            throw UnsupportedOperationException("Java types are read-only from script code.")
        }
    }
}
