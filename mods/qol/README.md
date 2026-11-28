# Unified: QoL

An all-in-one, modular, client-side quality-of-life mod for Minecraft, part of
[Project Unified](../../README.md). It works on its own; the other Unified mods
are optional.

Unified: QoL (mod id `qol_unified`; formerly published under the name
"QoL: Unified") combines independent quality-of-life features (HUD information,
inventory tools, controls, chat, audio and navigation) into a single
client-side mod. Each feature can be enabled or disabled individually from a
central settings screen.

> **Status:** early development (milestone M1). Nine features are
> implemented: Food HUD, Chat Heads, Keyboard Map, Target Info, Sound
> Control, Shulker Preview, Zoom, Inventory Mouse Tweaks and Inventory
> Search. Nothing has been released.

## Initial target

| Item | Target |
| --- | --- |
| Game | Minecraft Java Edition 26.3+ |
| Loader | Fabric Loader |
| Language / runtime | Java 25 (Temurin) |
| Build | Gradle with Fabric Loom |
| Languages | English, Brazilian Portuguese |
| Distribution | One JAR, client-side only |

Shared dependency versions live in the root [`gradle.properties`](../../gradle.properties);
this mod's own version is in [`mods/qol/gradle.properties`](gradle.properties). Both
and are recorded in the [architecture overview](../../docs/qol/architecture/overview.md#build-bootstrap-approved-2026-11-21).

A future native implementation for Minecraft Horizon (Rust Edition) is planned
(milestone M5). It shares behavioral specifications, not executable code.

## Building

Requires **JDK 25** (Temurin recommended) on `PATH` or in `JAVA_HOME`. The
Gradle Wrapper downloads the correct Gradle version; no separate Gradle
install is needed.

| Task | Windows (PowerShell or Command Prompt) | Linux, macOS, Git Bash |
| --- | --- | --- |
| Build and unit tests | `.\gradlew.bat :mods:qol:build` | `./gradlew :mods:qol:build` |
| Development client | `.\gradlew.bat :mods:qol:runClient` | `./gradlew :mods:qol:runClient` |
| One game test | `.\gradlew.bat :mods:qol:runGameTestZoom` | `./gradlew :mods:qol:runGameTestZoom` |

Linux setup (Java 25, WSL2, Prism Launcher): see
[Linux development setup](../../docs/development/linux-setup.md).

The distributable mod JAR is written to `mods/qol/build/libs/`, for example
`mods/qol/build/libs/unified-qol-26.3.0-alpha.1-SNAPSHOT.jar` (the file name
was `qol-unified-…` before the monorepo migration; the mod id is unchanged).

### Development client

`runClient` launches Minecraft 26.3 with the mod and Fabric API loaded. Game files
are kept in `mods/qol/run/` (ignored by Git). A successful start logs
`Unified: QoL initialized successfully (N features).`

### Development fixtures

For development only, two kinds of Gradle task load a separate fixture mod
with sample features:
- `runFixtureClient` starts a manual QA client in `mods/qol/run-fixtures/`.
- `runGameTestMain`, then `runGameTestRestart`, `runGameTestNewer` and
  `runGameTestCorrupt`, run automated client tests in `mods/qol/build/run-gametest*`.
  `runGameTestFoodhud` covers Food HUD in a disposable world;
  `runGameTestInventorysearch` covers Inventory Search (screens, keys,
  Mouse Tweaks gestures, layouts and timings). With
  `-PappleSkinJar=<path to a downloaded AppleSkin JAR>`, `runGameTestAppleskin`
  captures the same situations with AppleSkin for comparison. Screenshots are
  saved there.

Neither affects the distributable JAR.

## Settings

Press **`U`** (**Open Unified Settings**, category "Unified" in Key Binds) in
game or on the title screen, or use **Options → Unified Settings…**, to open
the shared Unified Settings workspace. With other Unified mods installed,
QoL's categories appear under a **Unified: QoL** group; alone, they are shown
directly. A custom binding of the former "Open Unified: QoL Settings" key is
moved to the shared key once. Every valid change is saved and applied at
once; Back or Escape closes the settings. Settings are stored in
`config/qol_unified/config.json` (unchanged by the monorepo migration).

The first feature is **Food HUD** (HUD & Information). It shows your
saturation on the hunger bar, and blinking previews of what held food would
restore. It can also add food values to tooltips and a food line to the F3
screen; turn that line on in the F3 options (F3 + F6). Previews are
estimates, and it never shows exhaustion. It turns itself off by default when
AppleSkin is installed. See the
[specification](../../docs/qol/specifications/food-hud-v1.md).

## Documentation

This project follows **Spec-Driven Development (SDD)**: behavior is specified
and approved before it is implemented. See [AGENTS.md](../../AGENTS.md) for the
workflow.

- Product
  - [Vision](../../docs/qol/product/vision.md)
  - [Feature catalog](../../docs/qol/product/feature-catalog.md)
- Architecture
  - [Overview](../../docs/qol/architecture/overview.md)
  - [Configuration](../../docs/qol/architecture/configuration.md)
- Specifications
  - [M0 — Platform Foundations](../../docs/qol/specifications/m0-platform-foundations.md)
  - [Inventory Search V1](../../docs/qol/specifications/inventory-search-v1.md)
  - [all specifications](../../docs/qol/specifications/)
- Development
  - [Versioning and release policy](../../docs/development/versioning.md)
  - [Linux development setup](../../docs/development/linux-setup.md)
- [Changelog](CHANGELOG.md)

## Versioning

Versions follow `MC_MAJOR.MC_MINOR.MOD_REVISION[-PRERELEASE]`, where the first
two numbers are the target Minecraft release line (e.g. `26.3.0-alpha.1`,
`26.3.0`, `26.3.1`). The first intended distributable version is
`26.3.0-alpha.1`. See the [versioning policy](../../docs/development/versioning.md).

## License

Not yet selected. Until a license is added, no rights are granted to use,
copy or redistribute this code.
