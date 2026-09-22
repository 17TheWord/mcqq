plugins {
    // No Minecraft toolchain here on purpose: a Bukkit plugin compiles against an API jar, not against the
    // game, so there is nothing to remap and no loader plugin to apply.
    java
    id("com.gradleup.shadow")
}

base {
    // 名字里带平台与窗口：同一个平台会有多个窗口，只写版本号会撞名。
    archivesName.set("${property("archives_base_name")}-spigot-26.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    // spigot-api only publishes snapshots, and only here.
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") { name = "Spigot" }
}

dependencies {
    // Compile-only: the server provides this, and shipping it would only invite a conflict.
    compileOnly("org.spigotmc:spigot-api:${property("spigot_api_version")}")

    // The shared half of the Bukkit family (compiled against spigot-api). It carries core with it.
    add("bundled", project(":bukkit-common"))
    add("bundled", project(":core"))
}

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
    filesMatching("plugin.yml") {
        expand(resourceFacts)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    // Surface the platform's deprecations at compile time instead of finding out when an API disappears.
    options.compilerArgs.add("-Xlint:deprecation")
}
