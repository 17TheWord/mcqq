plugins {
    `java-library`
}

base {
    archivesName.set("${property("archives_base_name")}-core")
}

/**
 * The part of the bridge that does not know Minecraft exists: the config file, the QQ bots, the routing in
 * both directions, and the seam the platform adapters implement. It is a plain library on purpose — the tests
 * here run without a game, and every platform shades this jar (plus the SDK) into its own artifact, because
 * that artifact is the only classpath a server is guaranteed to have.
 */
repositories {
    // Brigadier only exists here (and in the game's own library folder), not on Maven Central.
    maven("https://libraries.minecraft.net/") { name = "Minecraft" }
}

dependencies {
    // Brigadier is Mojang's command library, not a Minecraft class: it lets the core build the command tree
    // for every Minecraft-native platform. compileOnly — the game provides it at runtime.
    compileOnly("com.mojang:brigadier:${property("brigadier_version")}")

    // Compile-only: each platform bundles and relocates these into its own jar, so core must not carry them.
    compileOnly("io.github.skiesworld:qqbot-java-sdk:${property("qqbot_sdk_version")}")
    compileOnly("org.yaml:snakeyaml:${property("snakeyaml_version")}")
    // Only so the shared slf4j sink can exist; every platform provides slf4j at runtime (or ignores it).
    compileOnly("org.slf4j:slf4j-api:${property("slf4j_version")}")

    testImplementation(platform("org.junit:junit-bom:5.13.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.github.skiesworld:qqbot-java-sdk:${property("qqbot_sdk_version")}")
    testImplementation("org.yaml:snakeyaml:${property("snakeyaml_version")}")
    testImplementation("com.squareup.okhttp3:mockwebserver:${property("mockwebserver_version")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

// Read outside the task block: inside `configureEach` the receiver is the task, so `property` would look
// for a property on JavaCompile instead of on the project.
val coreRelease = property("core_java_release").toString().toInt()

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // No toolchain: the daemon already runs on a JDK new enough to emit this release, and pinning a
    // toolchain here would make the build depend on a second JDK being installed.
    options.release.set(coreRelease)
}

tasks.test {
    useJUnitPlatform()
}
