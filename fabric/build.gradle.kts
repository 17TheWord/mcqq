plugins {
    // 26.1 ships unobfuscated, so this is Loom's no-remap plugin id: plain Mojang names, no `mod*`
    // configurations and no remapJar task. `fabric-loom` is the legacy id for intermediary-mapped games.
    id("net.fabricmc.fabric-loom")
    id("com.gradleup.shadow")
}

base {
    archivesName.set(property("archives_base_name").toString())
}

java {
    // Minecraft 26.x requires Java 25; gradle/gradle-daemon-jvm.properties asks the daemon for it.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

/**
 * Everything this jar has to carry, because a mod jar is the only classpath a server is guaranteed to have.
 * `implementation` extends it so the dev `runServer` classpath and the compile classpath see the same
 * libraries, unrelocated.
 */
val bundled = configurations.create("bundled") {
    isCanBeResolved = true
}
configurations.named("implementation") { extendsFrom(bundled) }

val shadeGroup = "${property("maven_group")}.shaded"

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    // The platform-independent half of the bridge, plus what it needs at runtime.
    bundled(project(":core"))
    bundled("io.github.skiesworld:qqbot-java-sdk:${property("qqbot_sdk_version")}")
    bundled("org.yaml:snakeyaml:${property("snakeyaml_version")}")
}

tasks.shadowJar {
    // The bundled configuration alone: `runtimeClasspath` also carries Minecraft and Fabric API.
    configurations.set(listOf(bundled))
    archiveClassifier.set("")

    listOf(
        "io.github.skiesworld.qqbot",
        "okhttp3",
        "okio",
        "com.google.gson",
        "org.yaml.snakeyaml",
        // OkHttp is Kotlin, so kotlin-stdlib rides along; relocating it keeps the mod from arguing with whatever
        // another mod bundled.
        "kotlin",
    ).forEach { pkg -> relocate(pkg, "$shadeGroup.$pkg") }

    // slf4j-api is provided by Minecraft and must stay unrelocated, so it also must not be embedded: the two
    // copies would only decide by classloader order which one wins.
    exclude("org/slf4j/**")
    exclude("META-INF/services/javax.annotation.processing.Processor")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Shadow produces the plain `mc-qq-<version>.jar` that goes into `mods/`, so the thin jar keeps a classifier.
tasks.jar {
    archiveClassifier.set("dev")
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand("version" to version.toString())
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}
