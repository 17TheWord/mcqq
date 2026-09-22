plugins {
    // Declared once here, applied by the platform that needs it. Both ids exist at the same loom version:
    // `net.fabricmc.fabric-loom` is the no-remap plugin for the unobfuscated 26.x line, while plain
    // `fabric-loom` is the legacy remapping one an older Minecraft would need.
    id("net.fabricmc.fabric-loom") version "1.18.2" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
    // NeoForge's toolchain. Its docs only publish Groovy examples, which is why that one project uses
    // build.gradle instead of build.gradle.kts — a copy of upstream beats a translation of it.
    // `legacyforge` is an addon of the *same* artifact, released at the same version, and it is what builds
    // Forge 1.17–1.20.1 — which is why the 1.20.1 window does not need a build of its own.
    id("net.neoforged.moddev") version "2.0.147" apply false
    id("net.neoforged.moddev.legacyforge") version "2.0.147" apply false
    // Forge's toolchain. Forge and NeoForge split at 1.20.2 and their Gradle plugins share nothing, which is
    // why each needs its own project — the same conclusion the platform research came to.
    id("net.minecraftforge.gradle") version "[7.0.17,8)" apply false
}

/**
 * 描述符的公共字段（显示名、作者、许可证、描述）从 `descriptors.properties` 读，**不从 `gradle.properties`**。
 *
 * `gradle.properties` 是 Java Properties 格式，Gradle 按 ISO-8859-1 读它：非 ASCII 值会被双重编码 ——
 * "把" 在磁盘上是 E6 8A 8A，被读成 6 个字符，写回去就成了 C3 A6 C2 8A …。那些字节里有 C1 控制字符
 * （U+0080–U+009F），YAML 1.1 拒绝它们，于是 Paper / Spigot 报 `Invalid plugin.yml` 并且**插件完全不加载**。
 * JSON / TOML 容忍同样的字节 —— 这就是为什么当时只有 Bukkit 一侧炸得响。
 *
 * 在这里用 UTF-8 读一次、挂到根项目上就够：`Project.property` 会沿父项目向上找，所以 Kotlin 模块里的
 * `property("mod_name")` 和 Groovy 模块里的裸 `mod_name` 都能拿到，五个平台模块的脚本一行都不用改。
 */
java.util.Properties().apply {
    file("descriptors.properties").inputStream().reader(Charsets.UTF_8).use { load(it) }
}.forEach { key, value -> extensions.extraProperties.set(key.toString(), value) }

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

    // Gradle 过滤资源时默认用平台字符集。描述符是 UTF-8，填进去的值也是 UTF-8，所以写明，
    // 而不是继承这台机器碰巧是什么（本机 file.encoding=UTF-8 但 native.encoding=GBK）。
    tasks.withType<ProcessResources>().configureEach {
        filteringCharset = "UTF-8"
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
        // 两个**只含注解**的传递依赖，别裹进来：gson 带来 com.google.errorprone:error_prone_annotations，
        // kotlin-stdlib 带来 org.jetbrains:annotations（包是 org.jetbrains.annotations 与
        // org.intellij.lang.annotations）。它们运行时毫无用处，但 relocate 规则匹配不到它们 ——
        // 规则按包名匹配（`com.google.gson`、`kotlin`），而这两个库的包名不同，于是原样进了 jar。
        //
        // 后果不是"多几 KB"，而是 Forge 1.20.1 直接拒绝启动：
        //   java.lang.module.ResolutionException: Modules com.google.errorprone.annotations and mcqq
        //   export package com.google.errorprone.annotations.concurrent to module minecraft
        // —— 它的模块系统不允许同一个包出现在两个模块里。26.x 的加载器不检查这个，所以只有这一代炸。
        configurations.named("bundled") {
            exclude(group = "com.google.errorprone", module = "error_prone_annotations")
            exclude(group = "org.jetbrains", module = "annotations")
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
