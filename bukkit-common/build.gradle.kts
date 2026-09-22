// The part of the Bukkit family that every variant shares, compiled against **spigot-api** — the lowest
// common denominator. Paper's API is a superset of it, so a Paper variant can depend on this module as-is;
// a Spigot variant needs nothing more. Anything Paper-only (AsyncChatEvent, Adventure) stays in the variant.
plugins {
    id("java-library")
}

base {
    archivesName.set("${property("archives_base_name")}-bukkit-common")
}

repositories {
    // spigot-api only publishes snapshots, and only here.
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") { name = "Spigot" }
}

dependencies {
    api(project(":core"))
    compileOnly("org.spigotmc:spigot-api:${property("spigot_api_version")}")
}

// 在顶层取出来：在 tasks.withType { } 里 property(...) 会去 task 上找，找不到。
val javaRelease = property("core_java_release").toString().toInt()

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaRelease)
}
