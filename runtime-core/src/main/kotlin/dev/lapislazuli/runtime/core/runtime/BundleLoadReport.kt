package dev.lapislazuli.runtime.core.runtime

data class BundleLoadReport(
    val loadedBundles: List<String>,
    val failedBundles: List<String>,
)

