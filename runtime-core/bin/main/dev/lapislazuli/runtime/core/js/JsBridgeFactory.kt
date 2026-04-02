package dev.lapislazuli.runtime.core.js

import dev.lapislazuli.runtime.core.host.Callback
import dev.lapislazuli.runtime.core.host.ConfigStore
import dev.lapislazuli.runtime.core.host.DataDirectory
import dev.lapislazuli.runtime.core.host.HostServices
import dev.lapislazuli.runtime.core.host.Registration
import dev.lapislazuli.runtime.core.host.TaskHandle
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyArray
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject

class JsBridgeFactory {
    fun createPluginContext(hostServices: HostServices): Any? {
        val root = linkedMapOf<String, Any?>()
        root["logger"] = createLogger(hostServices)
        root["events"] = createEvents(hostServices)
        root["commands"] = createCommands(hostServices)
        root["scheduler"] = createScheduler(hostServices)
        root["config"] = createConfig(hostServices.config())
        root["dataDir"] = createDataDir(hostServices.dataDirectory())
        root["javaInterop"] = createJavaInterop(hostServices)
        return toGuestValue(root)
    }

    private fun createLogger(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "info" to executable { arguments ->
                hostServices.logger().info(stringArg(arguments, 0, "message"))
                null
            },
            "warn" to executable { arguments ->
                hostServices.logger().warn(stringArg(arguments, 0, "message"))
                null
            },
            "error" to executable { arguments ->
                hostServices.logger().error(stringArg(arguments, 0, "message"), null)
                null
            },
            "debug" to executable { arguments ->
                hostServices.logger().info("[debug] ${stringArg(arguments, 0, "message")}")
                null
            },
        )

    private fun createEvents(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "on" to executable { arguments ->
                require(arguments.size >= 2) { "events.on requires an event key and handler." }

                val eventKey = arguments[0].asString()
                val handler = arguments[1]
                requireExecutable(handler, "handler")

                val registration = hostServices.registerEvent(eventKey) { payload ->
                    executeCallback(handler, payload)
                }

                mapOf(
                    "unsubscribe" to executable {
                        registration.unregister()
                        null
                    },
                )
            },
        )

    private fun createCommands(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "register" to executable { arguments ->
                require(arguments.isNotEmpty()) { "commands.register requires a command definition." }

                val spec = arguments[0]
                val name = memberString(spec, "name", required = true)
                val description = memberString(spec, "description", required = false)
                val usage = memberString(spec, "usage", required = false)
                val aliases = memberStringList(spec, "aliases")
                val execute = spec.getMember("execute")
                requireExecutable(execute, "execute")

                val registration = hostServices.registerCommand(
                    name = name,
                    description = description,
                    usage = usage,
                    aliases = aliases,
                ) { payload ->
                    executeCallback(execute, payload)
                }

                mapOf(
                    "unsubscribe" to executable {
                        registration.unregister()
                        null
                    },
                )
            },
        )

    private fun createScheduler(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "runNow" to executable { arguments ->
                taskHandle(hostServices.runNow(taskCallback(arguments, "runNow")))
            },
            "runLaterTicks" to executable { arguments ->
                val delayTicks = longArg(arguments, 0, "delayTicks")
                taskHandle(hostServices.runLater(delayTicks, taskCallback(arguments, "runLaterTicks")))
            },
            "runTimerTicks" to executable { arguments ->
                val delayTicks = longArg(arguments, 0, "delayTicks")
                val intervalTicks = longArg(arguments, 1, "intervalTicks")
                require(arguments.size >= 3) { "runTimerTicks requires a callback." }
                val task = arguments[2]
                requireExecutable(task, "task")
                taskHandle(hostServices.runTimer(delayTicks, intervalTicks) { payload ->
                    executeCallback(task, payload)
                })
            },
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

    private fun createDataDir(dataDirectory: DataDirectory): Map<String, Any?> =
        mapOf(
            "path" to dataDirectory.path(),
            "resolve" to executable { arguments -> dataDirectory.resolve(*stringArray(arguments)) },
            "readText" to executable { arguments -> dataDirectory.readText(stringArg(arguments, 0, "relativePath")) },
            "writeText" to executable { arguments ->
                dataDirectory.writeText(
                    stringArg(arguments, 0, "relativePath"),
                    stringArg(arguments, 1, "contents"),
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

    private fun createJavaInterop(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "type" to executable { arguments ->
                hostServices.javaType(stringArg(arguments, 0, "className"))
            },
        )

    private fun taskCallback(arguments: Array<out Value>, methodName: String): Callback {
        val callbackIndex = when (methodName) {
            "runNow" -> 0
            "runLaterTicks" -> 1
            else -> throw IllegalArgumentException("Unsupported scheduler method $methodName")
        }

        require(arguments.size > callbackIndex) { "$methodName requires a task callback." }
        val task = arguments[callbackIndex]
        requireExecutable(task, "task")
        return Callback { payload -> executeCallback(task, payload) }
    }

    private fun taskHandle(handle: TaskHandle): Map<String, Any?> =
        mapOf(
            "cancel" to executable {
                handle.cancel()
                null
            },
        )

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
            is Map<*, *> -> ProxyObject.fromMap(
                value.entries.associate { (key, nestedValue) ->
                    key.toString() to toGuestValue(nestedValue)
                },
            )
            is List<*> -> ProxyArray.fromList(value.map(::toGuestValue))
            else -> value
        }

    private fun executable(executable: Executable): ProxyExecutable =
        ProxyExecutable { arguments ->
            try {
                executable.execute(arguments)
            } catch (error: RuntimeException) {
                throw error
            } catch (error: Exception) {
                throw IllegalStateException(error)
            }
        }

    private fun requireExecutable(value: Value?, field: String) {
        require(value != null && value.canExecute()) { "Expected executable field \"$field\"." }
    }

    private fun memberString(value: Value, field: String, required: Boolean): String {
        val member = value.getMember(field)
        if (member == null || member.isNull) {
            require(!required) { "Expected string field \"$field\"." }
            return ""
        }

        require(member.isString) { "Expected string field \"$field\"." }
        return member.asString()
    }

    private fun memberStringList(value: Value, field: String): List<String> {
        val member = value.getMember(field)
        if (member == null || member.isNull) {
            return emptyList()
        }

        require(member.hasArrayElements()) { "Expected array field \"$field\"." }
        return List(member.arraySize.toInt()) { index ->
            member.getArrayElement(index.toLong()).asString()
        }
    }

    private fun stringArg(arguments: Array<out Value>, index: Int, name: String): String {
        require(arguments.size > index && arguments[index].isString) {
            "Expected string argument \"$name\"."
        }
        return arguments[index].asString()
    }

    private fun longArg(arguments: Array<out Value>, index: Int, name: String): Long {
        require(arguments.size > index && arguments[index].fitsInLong()) {
            "Expected integer argument \"$name\"."
        }
        return arguments[index].asLong()
    }

    private fun stringArray(arguments: Array<out Value>): Array<String> =
        Array(arguments.size) { index ->
            require(arguments[index].isString) { "resolve only accepts string path segments." }
            arguments[index].asString()
        }

    private fun interface Executable {
        @Throws(Exception::class)
        fun execute(arguments: Array<out Value>): Any?
    }
}
