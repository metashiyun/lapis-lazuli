import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

base {
    archivesName.set("lapis-runtime-bukkit")
}

dependencies {
    implementation(project(":runtimes:jvm:core"))
    // Keep the concrete Graal runtimes directly on the deployable plugin's runtime
    // classpath so the shaded jar stays self-contained in production.
    runtimeOnly("org.graalvm.js:js-community:24.2.1")
    runtimeOnly("org.graalvm.polyglot:python:24.2.1")
    compileOnly("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

val shadowJarTask = tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    archiveVersion.set("")
    // Graal discovers its polyglot implementation and languages through
    // META-INF/services entries contributed by multiple jars.
    mergeServiceFiles()
}

val verifyShadowJarServices = tasks.register("verifyShadowJarServices") {
    dependsOn(shadowJarTask)

    doLast {
        val shadowJar = shadowJarTask.get().archiveFile.get().asFile

        ZipFile(shadowJar).use { zip ->
            fun readService(entryName: String): String {
                val entry = requireNotNull(zip.getEntry(entryName)) {
                    "Expected $entryName in ${shadowJar.name}, but it was missing."
                }

                return zip.getInputStream(entry)
                    .bufferedReader(StandardCharsets.UTF_8)
                    .use { it.readText() }
            }

            val polyglotImplementation =
                readService("META-INF/services/org.graalvm.polyglot.impl.AbstractPolyglotImpl")
            check("com.oracle.truffle.polyglot.PolyglotImpl" in polyglotImplementation) {
                "Expected Graal polyglot implementation provider in ${shadowJar.name}."
            }

            val languageProviders =
                readService("META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider")
            check("com.oracle.truffle.js.lang.JavaScriptLanguageProvider" in languageProviders) {
                "Expected Graal JavaScript language provider in ${shadowJar.name}."
            }
            check("com.oracle.graal.python.PythonLanguageProvider" in languageProviders) {
                "Expected Graal Python language provider in ${shadowJar.name}."
            }
        }
    }
}

tasks.build {
    dependsOn(shadowJarTask)
    dependsOn(verifyShadowJarServices)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "21"
    }
}
