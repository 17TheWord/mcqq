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
// 一个平台可以有多个"版本窗口"，所以项目路径是 `平台:窗口`（目录同名）。
// 窗口名用**窗口起点**：fabric-26.1 覆盖 [26.1, 26.2]，范围写在描述符里。
include("core")
// Bukkit 一族的共享部分（编译对 spigot-api）：paper / 以后的 spigot 都依赖它。
include("bukkit-common")
include("fabric:fabric-26.1")
include("paper:paper-26.1")
include("spigot:spigot-26.1")
include("neoforge:neoforge-26.1")
/**
 * Forge 是**按需**加进来的，默认不加。
 *
 * 原因：ForgeGradle 的 mavenizer 在**配置阶段**就要下载整套 Forge 工具链。Gradle 会配置所有 include 的项目，
 * 所以它一旦在项目列表里，网速差的机器上**任何** `./gradlew` 调用都会失败 —— 连另外三个平台也编不了。
 *
 * 默认（本地）：不带参数 → 只构建 core / fabric / neoforge / bukkit。
 * 需要 Forge 时：`./gradlew -PwithForge=true build`（CI 就是这么调的，runner 的网络没问题）。
 */
val withForge = startParameter.projectProperties["withForge"].toBoolean()
if (withForge) {
    include("forge:forge-26.1")
    // 1.20.1 那一代**不**需要独立的 legacy 构建：官方 MDK 走 ModDevGradle 的
    // `net.neoforged.moddev.legacyforge`，而它与 neoforge 用的 `net.neoforged.moddev` 是同一个
    // artifact 同一个版本，所以它只是同一构建里的另一个窗口。依据见 docs/MULTIPLATFORM.md 第六节。
    include("forge:forge-1.20.1")
}
