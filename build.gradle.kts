plugins {
    base
    kotlin("jvm") version "1.9.20" apply false
}

group = "dev.lapislazuli"
version = "0.1.0-SNAPSHOT"

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    extensions.configure<JavaPluginExtension>("java") {
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 21
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        exclude("**/*$*")
    }
}
