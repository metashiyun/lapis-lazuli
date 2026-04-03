plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "8.3.10"
}

val bukkitPluginVersion = "0.1.0"

version = bukkitPluginVersion

base {
    archivesName.set("lapis-lazuli")
}

dependencies {
    implementation(project(":runtime-core"))
    compileOnly("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    inputs.property("pluginVersion", bukkitPluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to bukkitPluginVersion)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}
