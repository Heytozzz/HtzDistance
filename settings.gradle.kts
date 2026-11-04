pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    // One source tree, one jar per Minecraft version.
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Applies the remapping Loom on obfuscated versions (1.21.x) and the plain Loom on 26.1+.
    id("dev.kikugie.loom-back-compat") version "0.4.3"
    // Lets Gradle download the right JDK (21 for 1.21.x, 25 for 26.x) when it is missing.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // 1.21.9 is skipped on purpose: its Fabric API has no world render event.
        versions("1.21.8", "1.21.10", "1.21.11", "26.1.2", "26.2", "26.3")
        vcsVersion = "1.21.8"
    }
}

rootProject.name = "HtzDistance"
