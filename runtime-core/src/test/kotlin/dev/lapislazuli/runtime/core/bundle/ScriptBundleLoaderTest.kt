package dev.lapislazuli.runtime.core.bundle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ScriptBundleLoaderTest {
    private val loader = ScriptBundleLoader(BundleManifestParser())

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun loadsBundleFromDirectory() {
        val bundleDir = tempDir.resolve("hello")
        Files.createDirectories(bundleDir)
        Files.writeString(bundleDir.resolve("main.js"), "module.exports = { default: { name: 'Hello' } };")
        Files.writeString(
            bundleDir.resolve("lapis-plugin.json"),
            """
                {
                  "id": "hello",
                  "name": "Hello",
                  "version": "1.0.0",
                  "engine": "js",
                  "main": "main.js",
                  "apiVersion": "1.0"
                }
            """.trimIndent(),
        )

        val bundle = loader.load(bundleDir)

        assertEquals("hello", bundle.manifest.id)
        assertEquals(bundleDir.resolve("main.js"), bundle.mainFile)
    }

    @Test
    fun rejectsEscapingEntrypoint() {
        val bundleDir = tempDir.resolve("hello")
        Files.createDirectories(bundleDir)
        Files.writeString(
            bundleDir.resolve("lapis-plugin.json"),
            """
                {
                  "id": "hello",
                  "name": "Hello",
                  "version": "1.0.0",
                  "engine": "js",
                  "main": "../main.js",
                  "apiVersion": "1.0"
                }
            """.trimIndent(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            loader.load(bundleDir)
        }
    }
}

