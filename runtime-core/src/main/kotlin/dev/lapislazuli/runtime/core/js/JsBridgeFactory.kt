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
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class JsBridgeFactory {
    fun createPluginContext(hostServices: HostServices): Any? {
        val root = linkedMapOf<String, Any?>()
        root["logger"] = createLogger(hostServices)
        root["events"] = createEvents(hostServices)
        root["commands"] = createCommands(hostServices)
        root["scheduler"] = createScheduler(hostServices)
        root["config"] = createConfig(hostServices.config())
        root["dataDir"] = createDataDir(hostServices.dataDirectory())
        root["server"] = createServer(hostServices)
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
            "onJava" to executable { arguments ->
                require(arguments.size >= 2) { "events.onJava requires an event class name and handler." }

                val eventClassName = stringArg(arguments, 0, "eventClassName")
                val handler = arguments[1]
                requireExecutable(handler, "handler")

                val registration = hostServices.registerJavaEvent(eventClassName) { payload ->
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
                val registrationSpec = parseCommandRegistration(arguments)

                val registration = hostServices.registerCommand(
                    name = registrationSpec.name,
                    description = registrationSpec.description,
                    usage = registrationSpec.usage,
                    aliases = registrationSpec.aliases,
                ) { payload ->
                    executeCallback(registrationSpec.execute, payload)
                }

                mapOf(
                    "unsubscribe" to executable {
                        registration.unregister()
                        null
                    },
                )
            },
        )

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
                    description = optionalStringArg(arguments, 2),
                    usage = optionalStringArg(arguments, 3),
                    aliases = optionalStringArray(arguments, 4),
                )
            }
            else -> {
                val spec = arguments[0]
                val execute = requireNotNull(member(spec, "execute"))
                requireExecutable(execute, "execute")

                CommandRegistration(
                    name = memberString(spec, "name", required = true),
                    execute = execute,
                    description = memberString(spec, "description", required = false),
                    usage = memberString(spec, "usage", required = false),
                    aliases = memberStringList(spec, "aliases"),
                )
            }
        }

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

    private fun createServer(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "bukkit" to hostServices.serverHandle(),
            "plugin" to hostServices.pluginHandle(),
            "console" to hostServices.consoleSenderHandle(),
            "dispatchCommand" to executable { arguments ->
                hostServices.dispatchConsoleCommand(stringArg(arguments, 0, "command"))
            },
            "broadcast" to executable { arguments ->
                hostServices.broadcastMessage(stringArg(arguments, 0, "message"))
            },
        )

    private fun createJavaInterop(hostServices: HostServices): Map<String, Any?> =
        mapOf(
            "type" to executable { arguments ->
                StaticHostType(hostServices.javaType(stringArg(arguments, 0, "className")))
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

    private fun member(value: Value, field: String): Value? =
        when {
            value.hasMembers() && value.hasMember(field) -> value.getMember(field)
            value.hasHashEntries() && value.hasHashEntry(field) -> value.getHashValue(field)
            else -> null
        }

    private fun memberString(value: Value, field: String, required: Boolean): String {
        val member = member(value, field)
        if (member == null || member.isNull) {
            require(!required) { "Expected string field \"$field\"." }
            return ""
        }

        require(member.isString) { "Expected string field \"$field\"." }
        return member.asString()
    }

    private fun memberStringList(value: Value, field: String): List<String> {
        val member = member(value, field)
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

    private fun optionalStringArg(arguments: Array<out Value>, index: Int): String {
        if (arguments.size <= index || arguments[index].isNull) {
            return ""
        }

        require(arguments[index].isString) {
            "Expected string argument at position ${index + 1}."
        }
        return arguments[index].asString()
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
            require(element.isString) { "Expected string alias at position ${elementIndex + 1}." }
            element.asString()
        }
    }

    private fun stringArray(arguments: Array<out Value>): Array<String> =
        Array(arguments.size) { index ->
            require(arguments[index].isString) { "resolve only accepts string path segments." }
            arguments[index].asString()
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
