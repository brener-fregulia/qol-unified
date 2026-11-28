# Project Unified

A family of independent, client-side Fabric mods for Minecraft Java Edition
26.3, developed in one repository. Install only the ones you want; none
requires another.

| Mod | What it is for | State |
| --- | --- | --- |
| **Unified: QoL** | Quality-of-life features: Food HUD, Chat Heads, Keyboard Map, Target Info, Sound Control, Shulker Preview, Zoom, Inventory Mouse Tweaks | In development, `mods/qol` |
| **Unified: Optimizations** | Client-side performance and network optimizations: Fast IP Ping, FastQuit | In development, `mods/optimizations` |
| **Unified: Graphics** | Visual and rendering features | Planned; not created yet ([discovery](docs/discovery/unified-graphics-rust-vulkan.md)) |

Each mod carries a copy of **Unified Settings (Library)** inside its JAR;
the game loads only one. It adds one **Unified Settings** entry (the `U`
key and a button in Minecraft's Options screen) for whichever Unified mods
are installed.

Nothing has been released yet.

## Requirements

- JDK 25 (Temurin recommended) on `PATH` or in `JAVA_HOME`.
- The Gradle Wrapper (`gradlew`) downloads the right Gradle; no separate
  install.
- Minecraft 26.3, Fabric Loader and versions are pinned in
  [`gradle.properties`](gradle.properties).
- Players need **Fabric Language Kotlin** and **Fabric API** for every
  Unified mod (Unified: Optimizations needs Fabric API since it carries the
  settings library). Both are separate mods, installed next to the Unified
  JARs.
- New code is written in Kotlin where it fits; Mixins stay in Java
  ([language policy](docs/architecture/monorepo.md#rules-approved-2026-11-26)).

## Building and testing

| Goal | Command |
| --- | --- |
| Build and unit-test every mod | `./gradlew build` |
| Unified: QoL only | `./gradlew :mods:qol:build` |
| Unified: Optimizations only | `./gradlew :mods:optimizations:build` |
| Unit tests of one mod | `./gradlew :mods:qol:test` |
| A Unified: QoL client game test | `./gradlew :mods:qol:runGameTestZoom` |
| Development client | `./gradlew :mods:qol:runClient` |

On Windows use `.\gradlew.bat`. Each mod's JAR is written to
`mods/<module>/build/libs/` with that mod's own name and version.

After a root `./gradlew build`, all production JARs are also collected in
**`dist/`** (ignored by Git; `./gradlew collectJars` refreshes it on its
own):

| File | What it is |
| --- | --- |
| `unified-qol-<version>.jar` | **Install:** Unified: QoL |
| `unified-optimizations-<version>.jar` | **Install:** Unified: Optimizations |
| `unified-settings-<version>.jar` | **Do not install:** Unified Settings (Library), for inspection only; each mod already carries it inside |

`dist/` is synchronized with the current versions, so older JARs disappear.
Building a single module (`./gradlew :mods:qol:build`) does not touch it. Game tests
open a real window and use disposable folders under
`mods/qol/build/run-gametest*`.

## Documentation

- [Monorepo architecture](docs/architecture/monorepo.md)
- [Versioning and releases](docs/development/versioning.md) (per mod,
  tags like `qol/v26.3.0`)
- [Linux development setup](docs/development/linux-setup.md)
  ([CachyOS audit](docs/development/cachyos-environment-audit.md))
- Unified: QoL: [README](mods/qol/README.md),
  [feature catalog](docs/qol/product/feature-catalog.md),
  [specifications](docs/qol/specifications/),
  [changelog](mods/qol/CHANGELOG.md)
- Unified: Optimizations: [overview and catalog](docs/optimizations/README.md),
  [changelog](mods/optimizations/CHANGELOG.md)
- [Vanilla world compatibility](docs/architecture/vanilla-world-compatibility.md):
  removing any Unified mod never breaks a world
- Discoveries: [Rust, native code and AOT](docs/discovery/rust-native-aot-feasibility.md),
  [Unified: Graphics, Vulkan and Rust](docs/discovery/unified-graphics-rust-vulkan.md)
- Contributors and agents: [AGENTS.md](AGENTS.md)

The project follows Spec-Driven Development: behavior is specified and
approved before it is implemented.

## License

Not yet selected. Until a license is added, no rights are granted to use,
copy or redistribute this code.
