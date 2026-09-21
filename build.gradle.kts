plugins {
    // Declared once here, applied by the platform that needs it. Both ids exist at the same loom version:
    // `net.fabricmc.fabric-loom` is the no-remap plugin for the unobfuscated 26.x line, while plain
    // `fabric-loom` is the legacy remapping one an older Minecraft would need.
    id("net.fabricmc.fabric-loom") version "1.18.2" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
    // NeoForge's toolchain. Its docs only publish Groovy examples, which is why that one project uses
    // build.gradle instead of build.gradle.kts — a copy of upstream beats a translation of it.
    id("net.neoforged.moddev") version "2.0.147" apply false
}

/**
 * Coordinates and repositories for every project. A repository declared at the root is not inherited by
 * subprojects, so it has to be said here once instead of in each of them; the same goes for the version, which
 * otherwise leaves the core jar unversioned.
 */
subprojects {
    group = property("maven_group").toString()
    version = property("mod_version").toString()
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
    }

    // The shipped jars bundle Apache-2.0 libraries (the SDK, OkHttp, Gson, SnakeYAML, Kotlin), and Apache-2.0
    // asks that whoever receives them also receives a copy of the licence and the notices. Both files go in
    // every platform's jar, which is the only artefact a server admin ever sees.
    plugins.withId("com.gradleup.shadow") {
        tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
            from(rootProject.file("LICENSE")) { into("") }
            from(rootProject.file("THIRD-PARTY.md")) { into("") }
        }
    }
}
