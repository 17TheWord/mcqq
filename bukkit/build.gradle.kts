plugins {
    // No Minecraft toolchain here on purpose: a Bukkit plugin compiles against an API jar, not against the
    // game, so there is nothing to remap and no loader plugin to apply.
    java
    id("com.gradleup.shadow")
}

base {
    archivesName.set("${property("archives_base_name")}-bukkit")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/") { name = "PaperMC" }
}

/** Same contract as the Fabric side: the plugin jar is the only classpath a server is guaranteed to have. */
val bundled = configurations.create("bundled") {
    isCanBeResolved = true
}
configurations.named("implementation") { extendsFrom(bundled) }

val shadeGroup = "${property("maven_group")}.shaded"

dependencies {
    // Compile-only: the server provides this, and shipping it would only invite a conflict.
    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")

    bundled(project(":core"))
    bundled("io.github.skiesworld:qqbot-java-sdk:${property("qqbot_sdk_version")}")
    bundled("org.yaml:snakeyaml:${property("snakeyaml_version")}")
}

tasks.shadowJar {
    configurations.set(listOf(bundled))
    archiveClassifier.set("")

    listOf(
        "io.github.skiesworld.qqbot",
        "okhttp3",
        "okio",
        "com.google.gson",
        "org.yaml.snakeyaml",
        "kotlin",
    ).forEach { pkg -> relocate(pkg, "$shadeGroup.$pkg") }

    // Paper ships slf4j for its plugins, and the bridge's own logging goes through java.util.logging here.
    exclude("org/slf4j/**")
    exclude("META-INF/services/javax.annotation.processing.Processor")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.jar {
    archiveClassifier.set("dev")
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("plugin.yml") {
        expand("version" to version.toString())
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    // Surface the platform's deprecations at compile time instead of finding out when an API disappears.
    options.compilerArgs.add("-Xlint:deprecation")
}
