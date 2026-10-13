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
    // Minecraft 26.x requires Java 25 — 用 JAVA_HOME 指定（loom 是跑在 Gradle daemon 里的插件，
    // 所以是 daemon 的 JVM 要 25，toolchain 管不到它）。见 docs/MULTIPLATFORM.md 第一节。
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
    // 入口类所在的 Java 包，用来拼描述符里的 main（见 fabric.mod.json）。
    // 改包名时这里**不用动**：只要改根 gradle.properties 的 maven_group（本项目的 Java 包与 Maven 坐标是同一个值）。
    "java_package" to property("maven_group").toString(),
    "mod_id" to property("mod_id").toString(),
    "mod_name" to property("mod_name").toString(),
    "mod_authors" to property("mod_authors").toString(),
    "mod_license" to property("mod_license").toString(),
    "mod_url" to property("mod_url").toString(),
    "mod_description" to property("mod_description").toString(),
    // fabric.mod.json 的 depends 也引用它：没装 Fabric API 时加载器要给"缺少依赖"，而不是 NoClassDefFoundError。
    "fabric_api_version" to property("fabric_api_version").toString(),
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
