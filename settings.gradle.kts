pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Lets Gradle fetch the JDK 25 toolchain Minecraft 26.1.2 needs, so nobody has to install it by hand.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "mc-qq"

// `core` holds everything that does not know Minecraft exists; each other project is one platform's
// adapter. Adding a platform means adding a directory here, not touching `core`.
include("core")
include("fabric")
include("bukkit")
include("neoforge")
