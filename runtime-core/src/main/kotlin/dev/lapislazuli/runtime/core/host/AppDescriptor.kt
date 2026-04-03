package dev.lapislazuli.runtime.core.host

data class AppDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val engine: String,
    val apiVersion: String,
    val backend: String,
    val runtime: String,
)
