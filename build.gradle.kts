plugins {
    // Declared once here, applied by the platform that needs it. Both ids exist at the same loom version:
    // `net.fabricmc.fabric-loom` is the no-remap plugin for the unobfuscated 26.x line, while plain
    // `fabric-loom` is the legacy remapping one an older Minecraft would need.
    id("net.fabricmc.fabric-loom") version "1.18.2" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
    // NeoForge's toolchain. Its docs only publish Groovy examples, which is why that one project uses
    // build.gradle instead of build.gradle.kts — a copy of upstream beats a translation of it.
    id("net.neoforged.moddev") version "2.0.147" apply false
    // Forge's toolchain. Forge and NeoForge split at 1.20.2 and their Gradle plugins share nothing, which is
    // why each needs its own project — the same conclusion the platform research came to.
    id("net.minecraftforge.gradle") version "[7.0.17,8)" apply false
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

    /**
     * Everything a platform jar needs in common, in one place.
     *
     * The contract every platform shares: **one jar dropped into `mods/` or `plugins/` must be enough**, so the
     * dependencies are embedded and renamed (relocated) — the SDK, and the two libraries the game also ships in
     * other versions. That block used to be copied into all five platform build files; what stays there is only
     * what is genuinely per-platform (the archives name, the API dependency, the toolchain, the descriptor).
     *
     * The Apache-2.0 notice files go in for the same reason: the jar is the only artefact an admin ever sees.
     */
    plugins.withId("com.gradleup.shadow") {
        val bundled = configurations.create("bundled") { isCanBeResolved = true }
        configurations.named("implementation") { extendsFrom(bundled) }
        val shadeGroup = "${property("maven_group")}.shaded"
        dependencies {
            add("bundled", "io.github.skiesworld:qqbot-java-sdk:${property("qqbot_sdk_version")}")
            add("bundled", "org.yaml:snakeyaml:${property("snakeyaml_version")}")
        }
        tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
            configurations = listOf(bundled)
            archiveClassifier.set("")
            // A library that the game itself ships (gson, snakeyaml) or that other mods commonly bundle
            // (kotlin) has to be renamed, or two copies of the same package would fight over one classpath.
            listOf(
                "io.github.skiesworld.qqbot",
                "okhttp3",
                "okio",
                "com.google.gson",
                "org.yaml.snakeyaml",
                "kotlin",
            ).forEach { pkg -> relocate(pkg, "$shadeGroup.$pkg") }
            // slf4j is provided by the game and must stay unrelocated, so it also must not be embedded.
            exclude("org/slf4j/**")
            exclude("META-INF/services/javax.annotation.processing.Processor")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            from(rootProject.file("LICENSE")) { into("") }
            from(rootProject.file("THIRD-PARTY.md")) { into("") }
        }
    }
}
