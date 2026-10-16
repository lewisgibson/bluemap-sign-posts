import org.gradle.language.base.plugins.LifecycleBasePlugin

plugins {
    `java-library`
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.jmh)
}

group = "com.lewisgibson"
// The Minecraft version is read from the Paper API pin so the two cannot drift: 26.3.build.174-beta
// gives 26.3.
val gameVersion =
    libs.versions.paperApi
        .get()
        .substringBefore(".build")
// The plugin version, `<semver>+mc<minecraft version>`, set here and nowhere else. The build
// writes it into plugin.yml and the release workflow tags the release with it.
version = "1.0.0+mc$gameVersion"
description = "Allows players to add BlueMap markers using signs and banners"

// Prints the version, for the release workflow.
tasks.register("printVersion") {
    val projectVersion = project.version.toString()
    doLast { println(projectVersion) }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// The integration tests run the Bukkit adapter on a mocked Paper server (MockBukkit), apart from
// the plain unit tests.
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

// Two more source sets for the Docker server tests: `serverTestFixture` is a small second plugin,
// loaded beside the real one, that fires real sign, place and break events over RCON and reads the
// marker set back; `serverTest` is the JUnit side that drives Docker and RCON.
sourceSets {
    create("serverTestFixture")
    create("serverTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

configurations["serverTestImplementation"].extendsFrom(configurations.testImplementation.get())

// The driver jars alone, no transitive dependencies, copied into BlueMap's folder by the storage
// server test.
val blueMapDrivers: Configuration by configurations.creating {
    isTransitive = false
}
configurations["serverTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    // The server API and BlueMap's API are both provided by the runtime; never bundle them.
    compileOnly(libs.paper.api)
    compileOnly(libs.bluemap.api)
    // Paper provides these at runtime: SQLite from the server itself, the rest through the
    // plugin.yml `libraries:` the build fills in below.
    compileOnly(libs.sqlite.jdbc)
    compileOnly(libs.hikaricp)
    compileOnly(libs.postgresql)
    compileOnly(libs.mariadb.jdbc)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
    // The store's unit tests run against real SQLite files, through the pool as well. PostgreSQL
    // and MySQL are run for real in the server tests.
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.hikaricp)
    testImplementation(libs.postgresql)
    testImplementation(libs.mariadb.jdbc)

    // compileOnly does not propagate to test classpaths, so the integration tests need the API
    // (for org.bukkit.* types) alongside MockBukkit, and BlueMapAPI for the fake map.
    // It is the 26.2 build MockBukkit's registry data matches, not the 26.3 the jar compiles
    // against: there is no mockbukkit-v26.3 yet (gradle/libs.versions.toml).
    "integrationTestImplementation"(libs.paper.api.mockbukkit)
    "integrationTestImplementation"(libs.bluemap.api)
    "integrationTestImplementation"(libs.mockbukkit)

    "serverTestFixtureCompileOnly"(libs.paper.api)
    "serverTestFixtureCompileOnly"(libs.bluemap.api)
    // The JDBC drivers BlueMap itself is given (its driver-jar setting) for its SQL storages.
    blueMapDrivers(libs.sqlite.jdbc)
    blueMapDrivers(libs.postgresql)
    blueMapDrivers(libs.mariadb.jdbc)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val integrationTest =
    tasks.register<Test>("integrationTest") {
        description = "Runs integration tests against a mocked Paper server (MockBukkit)."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
        classpath = sourceSets["integrationTest"].runtimeClasspath
        shouldRunAfter(tasks.test)
    }

// ---- Coverage ----------------------------------------------------------------
// The whole plugin is held to 100% line and branch coverage, every package, with nothing
// excluded. The unit tests cover `core` and `storage` and the MockBukkit tests cover the adapter,
// so the report and the gate read both. The Docker server tests are not counted.
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test, integrationTest)
    executionData(tasks.test.get(), integrationTest.get())
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, integrationTest)
    executionData(tasks.test.get(), integrationTest.get())
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf("com.lewisgibson.bluemapsignposts*")
            limit {
                counter = "LINE"
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(integrationTest, tasks.jacocoTestCoverageVerification)
}

// ---- Server tests (Docker) ---------------------------------------------------
// `./gradlew serverTest` boots real Paper 26.3 servers in Docker, one per store, with BlueMap,
// the built jar and the fixture plugin installed, and runs the store against real PostgreSQL,
// MySQL and MariaDB. It takes minutes and needs Docker, so it is not part of `check`.
val fixtureJar =
    tasks.register<Jar>("fixtureJar") {
        description = "Builds the test-only fixture plugin loaded beside the real one in server tests."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        archiveBaseName = "BlueMapSignPostsTestFixture"
        from(sourceSets["serverTestFixture"].output)
    }

val serverTest =
    tasks.register<Test>("serverTest") {
        description = "Boots real Paper servers in Docker and observes the markers over RCON."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = sourceSets["serverTest"].output.classesDirs
        classpath = sourceSets["serverTest"].runtimeClasspath
        dependsOn(tasks.jar, fixtureJar)
        // Never cached or skipped: the point is to run against today's Docker and today's jar.
        outputs.upToDateWhen { false }
        systemProperty(
            "bmsp.pluginJar",
            tasks.jar
                .get()
                .archiveFile
                .get()
                .asFile.absolutePath,
        )
        systemProperty(
            "bmsp.fixtureJar",
            fixtureJar
                .get()
                .archiveFile
                .get()
                .asFile.absolutePath,
        )
        val drivers = blueMapDrivers
        inputs.files(drivers)
        jvmArgumentProviders.add(
            CommandLineArgumentProvider {
                listOf("-Dbmsp.blueMapDrivers=" + drivers.files.joinToString(File.pathSeparator))
            },
        )
        systemProperty(
            "bmsp.workDir",
            layout.buildDirectory
                .dir("server-test")
                .get()
                .asFile.absolutePath,
        )
        testLogging {
            showStandardStreams = true
        }
    }

// ---- Formatting --------------------------------------------------------------
spotless {
    java {
        // AOSP variant keeps 4-space indentation (the default google style is 2-space).
        googleJavaFormat(libs.versions.googleJavaFormat.get()).aosp()
        formatAnnotations()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

// ---- Packaging ---------------------------------------------------------------
// plugin.yml gets the plugin version and the library versions from here, so each is set once.
tasks.processResources {
    val tokens =
        mapOf(
            "version" to project.version.toString(),
            "hikari" to libs.versions.hikaricp.get(),
            "postgresql" to libs.versions.postgresql.get(),
            "mariadb" to libs.versions.mariadbJdbc.get(),
        )
    inputs.properties(tokens)
    filesMatching("plugin.yml") {
        expand(tokens)
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
        )
    }
}

// ---- Local test server -------------------------------------------------------
// `./gradlew runServer` boots a real Paper 26.3 server with the built plugin installed, for
// manual smoke testing (MockBukkit cannot see loader differences). BlueMap itself is not
// downloaded for you: drop its jar into run/plugins/ first, or the plugin will not load
// (it hard-depends on BlueMap).
tasks.runServer {
    minecraftVersion(gameVersion)
}

// ---- Benchmarks --------------------------------------------------------------
// `./gradlew jmh` runs the JMH microbenchmarks in src/jmh over the hot-path core logic
// (the per-event sign parse and the per-chunk-load registry lookup). Not part of `build`.
jmh {
    jmhVersion = libs.versions.jmh.get()
    warmupIterations = 3
    iterations = 5
    fork = 1
}
