plugins {
    // 26.1 ships unobfuscated, so this is Loom's no-remap plugin id: plain Mojang names, no `mod*`
    // configurations and no remapJar task. `fabric-loom` is the legacy id for intermediary-mapped games.
    id("net.fabricmc.fabric-loom")
    id("com.gradleup.shadow")
}

base {
    // 名字里带平台与窗口：同一个平台会有多个窗口，只写版本号会撞名。
    archivesName.set("${property("archives_base_name")}-fabric-26.1")
}

java {
    // Minecraft 26.x requires Java 25; gradle/gradle-daemon-jvm.properties asks the daemon for it.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    // The platform-independent half of the bridge. The libraries it needs at runtime are bundled, relocated and
    // licensed by the root build — see the comment there.
    add("bundled", project(":core"))
}

// Shadow produces the jar that goes into `mods/`, so the thin jar keeps a classifier.
tasks.jar {
    archiveClassifier.set("dev")
}

// 在顶层取出来：在 tasks.processResources { } 里 property(...) 会去 task 上找，找不到。
// 描述符的公共字段只写在 gradle.properties 一处，这里把它们连同版本号一起喂给 expand。
val resourceFacts = mapOf(
    "version" to version.toString(),
    "mod_id" to property("mod_id").toString(),
    "mod_name" to property("mod_name").toString(),
    "mod_authors" to property("mod_authors").toString(),
    "mod_license" to property("mod_license").toString(),
    "mod_url" to property("mod_url").toString(),
    "mod_description" to property("mod_description").toString(),
)

tasks.processResources {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand(resourceFacts)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}
