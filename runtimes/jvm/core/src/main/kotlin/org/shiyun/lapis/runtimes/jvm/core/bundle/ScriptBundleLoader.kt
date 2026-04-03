package org.shiyun.lapis.runtimes.jvm.core.bundle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

class ScriptBundleLoader(
    private val manifestParser: BundleManifestParser,
) {
    fun listBundleDirectories(bundlesRoot: Path): List<Path> {
        if (Files.notExists(bundlesRoot)) {
            Files.createDirectories(bundlesRoot)
            return emptyList()
        }

        return Files.list(bundlesRoot).use { paths ->
            paths
                .filter(Path::isDirectory)
                .sorted()
                .toList()
        }
    }

    fun load(bundleDirectory: Path): ScriptBundle {
        val manifestPath = bundleDirectory.resolve(MANIFEST_FILE)
        require(Files.exists(manifestPath)) { "Missing bundle manifest at $manifestPath" }

        val manifest = manifestParser.parse(manifestPath)
        val mainFile = bundleDirectory.resolve(manifest.main).normalize()

        require(mainFile.startsWith(bundleDirectory.normalize())) {
            "Bundle entrypoint escapes bundle directory for ${manifest.id}"
        }
        require(Files.exists(mainFile) && Files.isRegularFile(mainFile)) {
            "Missing bundle entrypoint at $mainFile"
        }

        return ScriptBundle(bundleDirectory, manifestPath, mainFile, manifest)
    }

    private companion object {
        private const val MANIFEST_FILE = "lapis-plugin.json"
    }
}

