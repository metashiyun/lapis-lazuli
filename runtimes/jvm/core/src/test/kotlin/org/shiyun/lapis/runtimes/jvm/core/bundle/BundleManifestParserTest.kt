package org.shiyun.lapis.runtimes.jvm.core.bundle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class BundleManifestParserTest {
    private val parser = BundleManifestParser()

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun parsesValidManifest() {
        val manifest = tempDir.resolve("lapis-plugin.json")
        Files.writeString(
            manifest,
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

        val parsed = parser.parse(manifest)

        assertEquals("hello", parsed.id)
        assertEquals("main.js", parsed.main)
    }

    @Test
    fun rejectsMissingRequiredField() {
        val manifest = tempDir.resolve("lapis-plugin.json")
        Files.writeString(
            manifest,
            """
                {
                  "id": "hello",
                  "name": "Hello",
                  "version": "1.0.0",
                  "engine": "js",
                  "apiVersion": "1.0"
                }
            """.trimIndent(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(manifest)
        }
    }
}

