package dev.lapislazuli.runtimes.jvm.core.bundle

import java.nio.file.Path

data class ScriptBundle(
    val bundleDirectory: Path,
    val manifestPath: Path,
    val mainFile: Path,
    val manifest: BundleManifest,
)

