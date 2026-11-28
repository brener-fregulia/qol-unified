# Project Unified: Monorepo Architecture

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).
Structure approved by the Product Owner on 2026-11-26 (monorepo foundation
brief).

## Mods [Approved 2026-11-26]

| Public name | Mod id | Gradle module | State |
| --- | --- | --- | --- |
| Unified: QoL | `qol_unified` | `:mods:qol` | Eight features implemented |
| Unified: Optimizations | `unified_optimizations` | `:mods:optimizations` | Fast IP Ping and FastQuit implemented |
| Unified: Graphics | not chosen | not created | Future; discovery in [unified-graphics-rust-vulkan.md](../discovery/unified-graphics-rust-vulkan.md) |

Unified: QoL keeps the mod id, packages, configuration file, keybindings and
feature ids it had as "QoL: Unified", so existing configurations keep working.

## Layout

```text
Unified/
├── mods/
│   ├── qol/              Unified: QoL (src/main, src/test, src/devFixtures)
│   └── optimizations/    Unified: Optimizations (src/main, src/test)
├── libs/
│   └── settings/         Unified Settings (Library), nested into each mod (src/main, src/test)
├── tests/
│   └── settings-runtime/ development-only runtime game tests with the real JARs
├── spikes/               disposable experiments, separate Gradle builds
├── docs/
│   ├── architecture/     project-wide architecture (this file)
│   ├── development/      shared setup and versioning policy
│   ├── discovery/        project-wide discoveries (Rust/AOT, Graphics)
│   ├── qol/              Unified: QoL product, architecture, design, specs
│   └── optimizations/    Unified: Optimizations catalog and specs
├── .github/workflows/    one workflow per module
├── settings.gradle.kts   includes the modules
├── build.gradle.kts      aggregator only: declares Loom once
├── gradle.properties     shared Minecraft, Loader, Loom, Fabric API, JUnit versions
└── gradlew, gradle/      the single Gradle wrapper
```

## Rules [Approved 2026-11-26]

- **Independence.** No mod depends on another mod at build time or runtime.
  Each has its own `fabric.mod.json`, entrypoint, Mixin configuration,
  resources, configuration directory, tests and JAR. A common need is
  duplicated until sharing demonstrably pays off and is approved.
- **One shared library [Approved 2026-11-26, Q-US1].** The only exception is
  `unified_settings` ("Unified Settings (Library)", `:libs:settings`). Every
  mod nests it with Loom's `include` and depends on
  `">=<library version compiled against> <<next major>.0.0"` (computed in
  each mod's build). Fabric Loader 0.20.1 loads one copy: the highest
  version that every range allows (runtime-verified,
  [integration §18–§19](unified-settings-integration.md)). It coordinates
  the settings entry points only; each mod keeps its own configuration. It
  has its own version and tags (`settings/v…`).
- **Versions.** Each module's `gradle.properties` sets its own `mod_version`
  and `archives_base_name`. The root has no mod version. Tags are namespaced
  (`qol/v…`, `optimizations/v…`); see [versioning](../development/versioning.md).
- **Build conventions.** The small amount of shared build setup (Java 25,
  UTF-8, resource expansion) is repeated in each module's
  `build.gradle.kts`. A `build-logic` convention plugin is not justified for
  two modules. Toolchain versions are shared through the root
  `gradle.properties`, which is the one place to update them.
- **Java namespaces [Approved 2026-11-26, M2.0.1].** One root package per
  mod: `unified.qol`, `unified.optimizations`, later `unified.graphics`. Every
  source set uses it (main, test, development fixtures), and so do Fabric
  entrypoints and Mixin packages. A package is internal to its mod; mod ids,
  configuration keys and translation keys never derive from it, so packages
  can change without a configuration migration.
- **Languages [Approved 2026-11-26, M2.0.2].** Kotlin is preferred for new
  feature logic, configuration models and UI code. Java stays for Mixins
  (Mixin classes must be Java) and for low-level integrations where it is
  simpler. Existing Java code is not converted automatically. Both languages
  share one Java 25 toolchain and call each other freely. Sources:
  `src/<set>/java/` and `src/<set>/kotlin/` in the same packages.
- **Kotlin runtime.** Kotlin `2.4.21` compiles both modules. Its standard
  library at runtime comes from **Fabric Language Kotlin**
  (`fabric-language-kotlin`, `1.14.2+kotlin.2.4.21`, which bundles exactly
  Kotlin 2.4.21). It is a separate mod: every Unified mod declares
  `"fabric-language-kotlin": ">=1.14.2+kotlin.2.4.21"` in `depends`, and
  players install it next to the Unified mods (Prism Launcher: **Mods →
  Download mods → Fabric Language Kotlin**). It is never bundled into a
  Unified JAR. Versions are pinned in the root `gradle.properties`
  (`kotlin_version`, `fabric_kotlin_version`) and move together.
- **Vanilla worlds [Approved 2026-11-28].** No mod may make a world depend
  on it; see the [vanilla world compatibility contract](vanilla-world-compatibility.md).
- **Configuration directories.** `config/qol_unified/` and
  `config/unified_optimizations/`. A mod never reads another mod's files.

## Commands

| Goal | Command |
| --- | --- |
| Build and unit-test everything | `./gradlew build` |
| One module | `./gradlew :mods:qol:build`, `./gradlew :mods:optimizations:build` |
| QoL unit tests only | `./gradlew :mods:qol:test` |
| A QoL game test | `./gradlew :mods:qol:runGameTestZoom` (any `runGameTest<Scenario>`) |
| Development client | `./gradlew :mods:qol:runClient`, `./gradlew :mods:optimizations:runClient` |
| Unified Settings library | `./gradlew :libs:settings:build` |
| Installation-scenario runtime tests | `./gradlew :tests:settings-runtime:runGameTestQolOnly` (also `OptimizationsOnly`, `Both`) |

JARs: `mods/<module>/build/libs/`.

## CI

One workflow per module, triggered by changes under that module or the shared
build files (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
`gradle/`, `gradlew*`, the workflow itself). Each runs only its module's
`build` (compile and unit tests), uploads that module's JAR and test reports,
and never publishes. Game tests need a desktop session and run locally, as
before.

## History

- 2026-11-26, M2.0.1: Java packages simplified from
  `io.github.brener_fregulia.qolunified` to `unified.qol` and from
  `io.github.brener_fregulia.unified.optimizations` to
  `unified.optimizations`. Mod ids, configuration, key bindings and
  translations are unchanged. The Maven group in `gradle.properties` stays
  `io.github.brener_fregulia`; it is a build coordinate, not a package.

Until 2026-11-26 the repository was `brener-fregulia/qol-unified` with a single
Gradle project. It was renamed to `brener-fregulia/Unified` (GitHub keeps
redirects), and the sources moved into `mods/qol` with their history (`git log
--follow`).
