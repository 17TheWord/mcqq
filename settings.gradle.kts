pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// ⚠️ 这里**刻意不装**工具链解析器插件（`org.gradle.toolchains.foojay-resolver-convention`）：
// 它会在找不到 JDK 时**自动下载**一个，代价是把 JDK 版本变成构建的隐式依赖、
// 往 `<GRADLE_USER_HOME>/jdks/` 里堆几百 MB、还绕过了"我就用我装的那个 JDK"的意图。
// 本项目的约定是 **JDK 由开发者自己提供**（`JAVA_HOME` 指向 JDK 25），构建只做探测、不做下载。
// 机器上 JDK 装在非标准位置（如 `D:\SDK\...`）时，用 `org.gradle.java.installations.paths`
// 告诉 Gradle 去哪找 —— 那是绝对路径，写在**机器本地**的 gradle.properties 里，别写进仓库。

rootProject.name = "mcqq"

// `core` holds everything that does not know Minecraft exists; each other project is one platform's
// adapter. Adding a platform means adding a directory here, not touching `core`.
// 一个平台可以有多个"版本窗口"，所以项目路径是 `平台:窗口`（目录同名）。
// 窗口名用**窗口起点**：fabric-26.1 覆盖 [26.1, 26.2]，范围写在描述符里。
include("core")
// Bukkit 一族的共享部分（编译对 spigot-api）：paper / 以后的 spigot 都依赖它。
include("bukkit-common")
include("fabric:fabric-26.1")
include("paper:paper-1.20.1")
include("spigot:spigot-1.20.1")
include("neoforge:neoforge-26.1")
include("forge:forge-26.1")
include("forge:forge-1.20.1")
