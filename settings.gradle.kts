plugins {
    // Lets Gradle auto-provision the Java 25 toolchain (via the Foojay Disco API) on any
    // machine or CI runner that does not already have a JDK 25 installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "BlueMapSignPosts"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "PaperMC"
        }
        // BlueMapAPI is published here and nowhere else: it is not on Maven Central, and the
        // JitPack coordinates older addons use are the legacy route.
        maven("https://repo.bluecolored.de/releases") {
            name = "BlueColored"
        }
    }
}
