package org.shiyun.lapis.runtimes.jvm.core.runtime

data class BundleLoadReport(
    val loadedBundles: List<String>,
    val failedBundles: List<String>,
)

