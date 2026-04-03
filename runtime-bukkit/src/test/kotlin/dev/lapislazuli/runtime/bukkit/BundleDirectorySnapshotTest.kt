package org.shiyun.lapislazuli.runtime.bukkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class BundleDirectorySnapshotTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun ignoresRuntimeManagedFilesWhenTrackingBundleChanges() {
        val bundleDir = tempDir.resolve("hello-ts")
        Files.createDirectories(bundleDir.resolve("data"))
        Files.writeString(bundleDir.resolve("lapis-plugin.json"), """{"main":"main.js"}""")
        Files.writeString(bundleDir.resolve("main.js"), "console.log('v1');")
        Files.writeString(bundleDir.resolve("config.yml"), "value: one")
        Files.writeString(bundleDir.resolve("data").resolve("cache.txt"), "cache-one")

        val initialSnapshot = BundleDirectorySnapshot.capture(tempDir)

        Files.writeString(bundleDir.resolve("config.yml"), "value: two")
        Files.writeString(bundleDir.resolve("data").resolve("cache.txt"), "cache-two")

        assertEquals(initialSnapshot, BundleDirectorySnapshot.capture(tempDir))
    }

    @Test
    fun changesWhenTrackedBundleArtifactsChange() {
        val bundleDir = tempDir.resolve("hello-ts")
        Files.createDirectories(bundleDir)
        Files.writeString(bundleDir.resolve("lapis-plugin.json"), """{"main":"main.js"}""")
        Files.writeString(bundleDir.resolve("main.js"), "console.log('v1');")

        val initialSnapshot = BundleDirectorySnapshot.capture(tempDir)

        Files.writeString(bundleDir.resolve("main.js"), "console.log('v2');")

        assertNotEquals(initialSnapshot, BundleDirectorySnapshot.capture(tempDir))
    }
}
