package org.shiyun.lapislazuli.runtime.core.js

import org.graalvm.polyglot.Value

object JsValues {
    fun toJavaValue(value: Value?): Any? =
        when {
            value == null || value.isNull -> null
            value.isHostObject -> value.asHostObject<Any>()
            value.isBoolean -> value.asBoolean()
            value.isString -> value.asString()
            value.fitsInInt() -> value.asInt()
            value.fitsInLong() -> value.asLong()
            value.fitsInDouble() -> value.asDouble()
            value.hasArrayElements() -> buildList {
                for (index in 0 until value.arraySize) {
                    add(toJavaValue(value.getArrayElement(index)))
                }
            }
            value.hasMembers() -> buildMap {
                for (key in value.memberKeys) {
                    put(key, toJavaValue(value.getMember(key)))
                }
            }
            else -> value.`as`(Any::class.java)
        }
}
