package org.shiyun.lapis.runtimes.jvm.core.bundle

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class BundleManifest(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val engine: String = "",
    val main: String = "",
    val apiVersion: String = "",
    val dependencies: List<String> = emptyList(),
    val softDependencies: List<String> = emptyList(),
)
