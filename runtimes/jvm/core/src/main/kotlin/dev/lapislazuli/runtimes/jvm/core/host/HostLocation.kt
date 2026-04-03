package dev.lapislazuli.runtimes.jvm.core.host

data class HostLocation(
    val world: String?,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0.0f,
    val pitch: Float = 0.0f,
)
