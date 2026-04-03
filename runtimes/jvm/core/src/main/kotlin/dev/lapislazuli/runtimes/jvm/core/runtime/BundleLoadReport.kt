package dev.lapislazuli.runtimes.jvm.core.runtime

data class BundleLoadReport(
    val loadedBundles: List<String>,
    val failedBundles: List<String>,
)

