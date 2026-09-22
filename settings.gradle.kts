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
    include("forge")
}
