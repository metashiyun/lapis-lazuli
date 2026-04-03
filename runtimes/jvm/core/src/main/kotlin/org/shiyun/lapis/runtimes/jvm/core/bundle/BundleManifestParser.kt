package org.shiyun.lapis.runtimes.jvm.core.bundle

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path

class BundleManifestParser {
    private val objectMapper = jacksonObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun parse(manifestPath: Path): BundleManifest {
        val manifest = objectMapper.readValue(Files.readString(manifestPath), BundleManifest::class.java)
        validate(manifest)
        return manifest
    }

    private fun validate(manifest: BundleManifest) {
        requireNonBlank(manifest.id, "id")
        requireNonBlank(manifest.name, "name")
        requireNonBlank(manifest.version, "version")
        requireNonBlank(manifest.engine, "engine")
        requireNonBlank(manifest.main, "main")
        requireNonBlank(manifest.apiVersion, "apiVersion")
    }

    private fun requireNonBlank(value: String?, field: String) {
        require(!value.isNullOrBlank()) { "Manifest field \"$field\" must be present." }
    }
}

