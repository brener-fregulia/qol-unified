plugins {
    id("net.fabricmc.fabric-loom")
    // Kotlin for new code (docs/architecture/monorepo.md, language policy); Mixins stay in Java.
    id("org.jetbrains.kotlin.jvm")
}

// This module's own version and artifact name, from mods/qol/gradle.properties. Other modules never inherit them.
version = property("mod_version") as String
group = providers.gradleProperty("maven_group").get()

base {
    archivesName = property("archives_base_name") as String
}

// Development-only fixture mod (sample features and client game tests). It is never part of the
// distributable JAR: the jar task packages only the main source set.
val devFixtures: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

loom {
    mods {
        register("unified_settings") {
            sourceSet("main", ":libs:settings")
        }
        register("qol_unified") {
            sourceSet(sourceSets.main.get())
        }
        register("qol_unified_dev") {
            sourceSet(devFixtures)
        }
    }
    runs {
        // Manual QA with sample features, in a disposable game directory.
        register("fixtureClient") {
            client()
            name("Fixture Client")
            source(devFixtures)
            runDir("run-fixtures")
        }
        // Automated client game tests. "main" seeds a fresh configuration; "restart" reuses it.
        for ((scenario, dir) in listOf(
                "main" to "build/run-gametest",
                "restart" to "build/run-gametest",
                "newer" to "build/run-gametest-newer",
                "corrupt" to "build/run-gametest-corrupt",
                "foodhud" to "build/run-gametest-foodhud",
                "chatheads" to "build/run-gametest-chatheads",
                "keyboardmap" to "build/run-gametest-keyboardmap",
                "targetinfo" to "build/run-gametest-targetinfo",
                "soundcontrol" to "build/run-gametest-soundcontrol",
                "soundcontrolrestart" to "build/run-gametest-soundcontrol",
                "shulkerpreview" to "build/run-gametest-shulkerpreview",
                "settings" to "build/run-gametest-settings",
                "settingsrestart" to "build/run-gametest-settings",
                "zoom" to "build/run-gametest-zoom",
                "mousetweaks" to "build/run-gametest-mousetweaks",
                "inventorysearch" to "build/run-gametest-inventorysearch")) {
            register("gameTest" + scenario.replaceFirstChar { it.uppercase() }) {
                client()
                name("Game Test ($scenario)")
                source(devFixtures)
                runDir(dir)
                property("fabric.client.gametest")
                property("fabric.client.gametest.modid", "qol_unified_dev")
                property("qol_unified.dev.scenario", scenario)
                ideConfigGenerated(false)
            }
        }
        // Optional side-by-side comparison with AppleSkin (FH-AC-15), in its own disposable game directory.
        // Runs only when a downloaded AppleSkin JAR is passed: -PappleSkinJar=<path>. Nothing is added to the build.
        providers.gradleProperty("appleSkinJar").orNull?.let { jar ->
            register("gameTestAppleskin") {
                client()
                name("Game Test (AppleSkin comparison)")
                source(devFixtures)
                runDir("build/run-gametest-appleskin")
                property("fabric.client.gametest")
                property("fabric.client.gametest.modid", "qol_unified_dev")
                property("qol_unified.dev.scenario", "appleskin")
                property("fabric.addMods", file(jar).absolutePath)
                ideConfigGenerated(false)
            }
        }
        // Optional keyboard_map run with Controlling installed, in its own disposable game directory:
        // -PcontrollingJars=<Controlling jar><path separator><Searchables jar>. Nothing is added to the build.
        providers.gradleProperty("controllingJars").orNull?.let { jars ->
            register("gameTestKeyboardmapControlling") {
                client()
                name("Game Test (keyboard_map with Controlling)")
                source(devFixtures)
                runDir("build/run-gametest-keyboardmap-controlling")
                property("fabric.client.gametest")
                property("fabric.client.gametest.modid", "qol_unified_dev")
                property("qol_unified.dev.scenario", "keyboardmap")
                property("fabric.addMods", jars.split(File.pathSeparator).joinToString(File.pathSeparator) {
                    file(it).absolutePath
                })
                ideConfigGenerated(false)
            }
        }
        // Optional side-by-side comparison with Jade (TI-AC-16), in its own disposable game directory. Runs only when
        // a downloaded JAR is passed: -PjadeJar=<path>. Nothing is added to the build.
        providers.gradleProperty("jadeJar").orNull?.let { jar ->
            register("gameTestTargetinfoJade") {
                client()
                name("Game Test (Target Info with Jade)")
                source(devFixtures)
                runDir("build/run-gametest-targetinfo-jade")
                property("fabric.client.gametest")
                property("fabric.client.gametest.modid", "qol_unified_dev")
                property("qol_unified.dev.scenario", "targetinfo_jade")
                property("fabric.addMods", file(jar).absolutePath)
                ideConfigGenerated(false)
            }
        }
        // Optional side-by-side comparison with the original Chat Heads (CH-AC-15, CH-AC-17), in its own disposable
        // game directory. Runs only when a downloaded JAR is passed: -PchatHeadsJar=<path>. Nothing is added to the build.
        providers.gradleProperty("chatHeadsJar").orNull?.let { jar ->
            register("gameTestChatheadsReference") {
                client()
                name("Game Test (Chat Heads comparison)")
                source(devFixtures)
                runDir("build/run-gametest-chatheads-reference")
                property("fabric.client.gametest")
                property("fabric.client.gametest.modid", "qol_unified_dev")
                property("qol_unified.dev.scenario", "chatheads_reference")
                property("fabric.addMods", file(jar).absolutePath)
                ideConfigGenerated(false)
            }
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")

    // Kotlin's standard library at runtime comes from Fabric Language Kotlin, a separate mod the player installs; it is
    // a development and compile dependency here and is never bundled into this JAR.
    implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

    // Unified Settings (Library): compiled against and nested (jar-in-jar) as its own mod, never merged into this JAR.
    // Fabric loads one copy for all Unified mods (docs/architecture/unified-settings-integration.md §18).
    implementation(project(":libs:settings"))
    include(project(":libs:settings"))

    testImplementation(platform("org.junit:junit-bom:${providers.gradleProperty("junit_version").get()}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Keep log files written during tests out of the project directory.
    val testRunDir = layout.buildDirectory.dir("test-run")
    workingDir(testRunDir)
    doFirst { testRunDir.get().asFile.mkdirs() }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

// ">=<library version compiled against> <<next major>.0.0" (spike rule §18.4: never be served an older contract).
val settingsRange = (project(":libs:settings").property("mod_version") as String).let {
    ">=$it <${it.substringBefore('.').toInt() + 1}.0.0"
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraft_version" to providers.gradleProperty("minecraft_version").get(),
        "loader_version" to providers.gradleProperty("loader_version").get(),
        "fabric_kotlin_version" to providers.gradleProperty("fabric_kotlin_version").get(),
        "settings_range" to settingsRange,
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

// Keep the development fixtures compiling in CI without packaging them.
tasks.check {
    dependsOn(devFixtures.classesTaskName)
}
