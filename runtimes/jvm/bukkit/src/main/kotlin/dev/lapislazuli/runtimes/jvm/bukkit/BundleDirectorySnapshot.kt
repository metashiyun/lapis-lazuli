package dev.lapislazuli.runtimes.jvm.bukkit

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

internal object BundleDirectorySnapshot {
    fun capture(bundlesRoot: Path): List<String> {
        if (Files.notExists(bundlesRoot)) {
            return emptyList()
        }

        return Files.walk(bundlesRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) }
                .toList()
                .mapNotNull { path ->
                    val relativePath = bundlesRoot.relativize(path).normalize()
                    if (!shouldTrack(relativePath)) {
                        return@mapNotNull null
                    }

                    "${relativePath.toString().replace('\\', '/')}|${sha256(Files.readAllBytes(path))}"
                }
                .sorted()
                .toList()
        }
    }

    private fun shouldTrack(relativePath: Path): Boolean {
        if (relativePath.nameCount == 0) {
            return false
        }

        if (relativePath.nameCount < 2) {
            return true
        }

        val bundleRelativePath = relativePath.subpath(1, relativePath.nameCount)

        if (bundleRelativePath.getName(0).toString() == "data") {
            return false
        }

        return !(bundleRelativePath.nameCount == 1 && bundleRelativePath.fileName.toString() == "config.yml")
    }

    private fun sha256(contents: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(contents)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
