# CachyOS Development Environment Audit (2026-11-28)

Audit of the Product Owner's CachyOS laptop after the `git pull` that brought
the checkout up to the monorepo layout. Evidence labels: **Verified**,
**Inferred**, **Proposed**, **Open** (see
[vanilla world compatibility](../architecture/vanilla-world-compatibility.md)).
Nothing was installed and no system configuration was changed.

General setup instructions remain in [linux-setup.md](linux-setup.md).

## 1. Summary

| Area | State |
| --- | --- |
| Repository | **Ready.** `main` at `735889f`, equal to `origin/main`, clean, no submodules, monorepo layout, all recent features present (§2). |
| Java / Gradle | **Ready.** Temurin 25.0.5.1 is the system default; wrapper Gradle 9.9.2 runs on it (§3). |
| Build | **Blocked by the network, not by the machine.** The Wi-Fi's DNS (FortiGuard) blocks Mojang and Fabric Meta hosts, so Loom cannot configure any module (§5). |
| Rust, LLVM | **Ready** for research: rustup 1.29, Rust 1.96.0 stable, Clang/LLVM 23.1.5 (§4). |
| Vulkan | **Ready:** loader 1.4.363, RADV (Mesa 26.3.1), validation layers, shaderc/glslang/SPIRV-Tools (§4). |
| Stale local files | Pre-monorepo `build/` and `run/` at the repository root (ignored by Git; harmless; removable, §6). |

## 2. Repository state (Verified)

| Check | Result |
| --- | --- |
| Branch / HEAD | `main`, `735889f4910fdfcd9958af80d7b019df9b98165c` (2026-11-27, "feat(qol): show Shulker contents in Target Info") |
| Remote | `origin https://github.com/brener-fregulia/qol-unified.git` — the old name; GitHub redirects to `brener-fregulia/Unified`. `git fetch` works; `main...origin/main` has no divergence. |
| Working tree | Clean before this audit |
| Submodules | None |
| Gradle projects | `settings.gradle.kts` includes `:mods:qol`, `:mods:optimizations`, `:libs:settings`, `:tests:settings-runtime`; `spikes/unified-settings-nesting` is a separate build |
| Old root `src/` | Absent |
| Old packages | No `io.github.brener_fregulia.qolunified` / `…unified.optimizations` references in sources, Mixin configs or build files; Mixin packages are `unified.qol.mixin` and `unified.optimizations.mixin` |
| Wrapper | `gradlew` is mode `100755` in Git and `-rwxr-xr-x` on disk, LF endings (`.gitattributes`) |
| Broken symlinks | None (`find -xtype l`, excluding `.git`) |
| Module versions | QoL `26.3.0-alpha.1-SNAPSHOT`, Optimizations `26.3.0-alpha.1-SNAPSHOT`, Unified Settings `1.2.0`; each in its own `gradle.properties` |

Changes expected after the restructuring, all present in `git log`:

| Change | Commit(s) |
| --- | --- |
| Monorepo migration, namespace simplification | history before `9be6540` ("docs: record Unified namespace conventions") |
| Kotlin foundation | `8e10347`, `155f38f` |
| Unified Settings library and integrated workspace | `1d92812` … `a36606f`, `a62cf31` (library 1.2.0) |
| Inventory Mouse Tweaks double-click fix | `e1cab8b` |
| Fast IP Ping | `6cfc1dc` |
| FastQuit | `e4ad4df`, `9c6472d`, `bbe21b0` |
| Placed Shulker Box Target Info | `735889f` |

Metadata (static inspection of `fabric.mod.json` sources):

- `qol_unified` and `unified_optimizations` each depend on
  `unified_settings: ${settings_range}` (computed as `>=1.2.0 <2.0.0`) and on
  Fabric Language Kotlin; neither depends on the other. **Verified.**
- Both modules nest the library with `include(project(":libs:settings"))`;
  Fabric loads one copy (runtime-verified earlier,
  [integration §18–§19](../architecture/unified-settings-integration.md)).
  Whether the *built* JARs contain it could not be re-checked (§5).
- Minor: `mods/optimizations/build.gradle.kts` still has the comment "No
  Fabric API yet" directly above its Fabric API dependency. Comment only;
  left unchanged because the module could not be rebuilt here.

## 3. Java and Gradle (Verified)

| Item | Value |
| --- | --- |
| `java` / `javac` | `/usr/bin/java`, Temurin `25.0.5.1+2-LTS` (package `jdk25-temurin 25.0.5.1.u2-1`) |
| `archlinux-java` | `java-25-temurin (default)`; the only installed environment |
| `JAVA_HOME` | Unset (fine: Gradle uses `/usr/lib/jvm/java-25-temurin` from `PATH`) |
| `cacerts` | `/usr/lib/jvm/java-25-temurin/lib/security/cacerts` → `/etc/ssl/certs/java/cacerts` (system trust store) |
| Gradle | Wrapper 9.9.2 (SHA-256 pinned in `gradle-wrapper.properties`), Kotlin DSL 2.4.21, launcher and daemon JVM 25.0.5.1 |
| `~/.gradle/gradle.properties`, `init.d` | Absent (no local overrides) |
| Loom cache | `~/.gradle/caches/fabric-loom/26.3/` holds the client, server and merged JARs and `mojang_minecraft_info.json`, but **no version manifest**, so `--offline` also fails |
| Prism Launcher | Bundled Java for 26.3: Microsoft OpenJDK 25.0.1 (`java-runtime-epsilon`) |

## 4. Native toolchains and GPU (Verified)

| Tool | Version / state |
| --- | --- |
| rustup | 1.29.0; default `stable-x86_64-unknown-linux-gnu` |
| rustc / cargo | 1.96.0 (2026-05-25) |
| Rust targets | `x86_64-unknown-linux-gnu`, `x86_64-unknown-linux-musl`, `x86_64-pc-windows-msvc` |
| Rust components | clippy, rustfmt, rust-src, llvm-tools, rust-docs; `cargo-nextest`, `cargo-llvm-cov`, `cargo-xwin`, `sccache`, `cargo-miri` in `~/.cargo/bin` |
| `~/.cargo/config.toml`, `RUSTFLAGS` | Absent / unset (no `target-cpu=native` leaking into builds) |
| Clang / LLVM / LLD | 23.1.5; `mold` 3.0.0; `cmake` 4.5.1; `pkgconf` 3.0.11 |
| glibc | 2.44 |
| Fixture | A Rust `cdylib` built and called from Java 25 through FFM in the scratchpad (see [Rust feasibility](../discovery/rust-native-aot-feasibility.md) §5.1): the toolchain works end to end |
| CPU | AMD Ryzen 5 5625U, 12 threads; supports x86-64-v3 (AVX2, BMI2, FMA); `ld.so` also lists v4 but it is not supported |
| Package repositories | `cachyos-v3`, `cachyos-core-v3`, `cachyos-extra-v3`, then Arch: system packages are built for x86-64-v3. Binaries built here for distribution must not assume that. |
| GPU | AMD Radeon "Barcelo" `[1002:15e7]` (Renoir-class iGPU), kernel driver `amdgpu` |
| OpenGL | radeonsi, Mesa 26.3.1, core profile 4.6 |
| Vulkan | `vulkan-icd-loader` 1.4.363; one ICD `radeon_icd.json` (RADV, conformance 1.4.5.3); instance 1.4.363; Wayland, XCB and Xlib surfaces |
| Vulkan layers | `VK_LAYER_KHRONOS_validation` 1.4.363, Mesa device select, MangoHud, Steam overlay/Fossilize |
| Vulkan for Minecraft | Every extension and feature named in 26.3's Vulkan feature sets is reported by `vulkaninfo` (dynamic rendering, synchronization2, timeline semaphores, push descriptors, multi-draw, vertex-attribute divisor…). The backend was not launched (§5). |
| Shader tools | `glslc` (shaderc 2026.4), `glslangValidator` 16.6.0, `spirv-val`/`spirv-opt` (SPIRV-Tools 2026.4), `vulkan-headers` 1.4.363 |
| Session | Wayland (`WAYLAND_DISPLAY=wayland-1`, XWayland `:0`) |
| Editor | VS Code 1.147.0 with only `rust-lang.rust-analyzer`; no `.vscode/` or `.idea/` in the repository (both ignored) |

Optional and missing (none needed for current development):

| Missing | Purpose | Install (only if wanted) |
| --- | --- | --- |
| `flite` | Minecraft narrator (client logs a warning without it) | `sudo pacman -S flite` |
| `ninja` | Faster CMake builds for C/C++ SDKs (Graphics research) | `sudo pacman -S ninja` |
| `directx-shader-compiler` (`dxc`) | HLSL → SPIR-V, only if an HLSL SDK (e.g. FidelityFX) is studied | `sudo pacman -S directx-shader-compiler` |
| VS Code Java/Kotlin support | Editing the Gradle modules in VS Code | VS Code extensions "Extension Pack for Java" and a Kotlin language extension, or use IntelliJ IDEA (not detected as a pacman or Flatpak package) |

## 5. Build validation

### 5.1 Result

```text
$ ./gradlew projects
A problem occurred configuring project ':libs:settings'.
> Failed to download https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
  ... SunCertPathBuilderException: unable to find valid certification path
```

**No module could be configured**, so compilation, unit tests, JAR building,
`dist/` collection and JAR inspection were **not run**. (No module had ever
been built in this checkout: there is no `mods/*/build`, `libs/*/build` or
`dist/`.)

### 5.2 Cause (Verified)

The certificate error is not a JDK problem:

- `curl https://piston-meta.mojang.com/...` fails with *certificate subject
  name 'Fortiguard SDNS Blocked Page'*.
- The DHCP-provided DNS server of the Wi-Fi link, `192.168.0.28`, answers
  `208.91.112.55` / `2620:101:9000:53::55` (a FortiGuard block page) for
  `piston-meta.mojang.com`, `piston-data.mojang.com`,
  `libraries.minecraft.net`, `resources.download.minecraft.net`,
  `meta.fabricmc.net` and `api.modrinth.com`.
- The second configured server (`208.67.222.222`) returns the real Azure
  CDN addresses.
- Reachable: `maven.fabricmc.net`, `plugins.gradle.org`,
  `repo.maven.apache.org`, `services.gradle.org`, `github.com`,
  `static.rust-lang.org`, `crates.io`.

This is a **network policy of the current Wi-Fi** (web filter), not a
machine misconfiguration. The audit did not bypass it.

### 5.3 To finish the validation (manual)

On a network without that filter (or after the network administrator
allows the hosts above), run:

```sh
./gradlew projects
./gradlew :libs:settings:build :mods:qol:build :mods:optimizations:build
./gradlew collectJars && ls dist/
unzip -l mods/qol/build/libs/unified-qol-*.jar | grep 'META-INF/jars/'
unzip -l mods/optimizations/build/libs/unified-optimizations-*.jar | grep 'META-INF/jars/'
unzip -p mods/qol/build/libs/unified-qol-*.jar fabric.mod.json
git diff --check
```

Expected: each mod JAR contains exactly one
`META-INF/jars/unified-settings-1.2.0.jar`; `dist/` holds
`unified-qol-26.3.0-alpha.1-SNAPSHOT.jar`,
`unified-optimizations-26.3.0-alpha.1-SNAPSHOT.jar` and
`unified-settings-1.2.0.jar` only; `fabric.mod.json` shows
`"unified_settings": ">=1.2.0 <2.0.0"`.

Choosing a different DNS server for the system is a system-wide change and
was not made. If the PO wants it, NetworkManager can override DNS per
connection; that decision belongs to the PO and may conflict with the
network's rules.

## 6. Stale and generated local files (Verified)

| Path | Content | Assessment |
| --- | --- | --- |
| `build/` (root) | Pre-monorepo output of 2026-11-23: `libs/qol-unified-26.3.0-alpha.1-SNAPSHOT.jar`, `classes/`, `run-gametest*` folders | Ignored by Git; the current root project only uses `build/` for its own small outputs. Harmless but confusing. |
| `run/` (root) | Pre-monorepo development client directory (empty `saves/`, `options.txt`, logs) | Ignored; no world data. Current runs use `mods/<mod>/run/`. |
| `.gradle/` (root) | Gradle 9.9.2 caches, configuration cache from 2026-11-23 | Normal; Gradle invalidates it when build files change. |
| `.claude/` | Empty | Harmless. |

Optional cleanup (not done; only the PO's local files):

```sh
rm -rf build run    # from the repository root; both are regenerated if needed
```

Prism Launcher (inspected read-only, nothing changed):

- Instances `26.3` and `QoL Unified - Testing` still contain the
  pre-monorepo `qol-unified-26.3.0-alpha.1-SNAPSHOT.jar`. Current builds are
  named `unified-qol-…` and **require Fabric Language Kotlin**, which the
  `QoL Unified - Testing` instance does not have.
- Instance `26.3` also has `fast-ip-ping-v1.0.12-mc26.3.jar`; with it
  installed, Unified's `fast_ip_ping` stays inactive by design.
- `26.3` uses Sodium 0.9.3-beta.1, Iris 1.11.7 and
  `preferredGraphicsBackend:"default"`; its last log shows "Using graphics
  backend OpenGL … Mesa 26.3.1".

## 7. Changes made by this audit

None to the system, the Prism instances or the build. Documentation only:
this file, the three discovery/architecture documents, and index updates.

## 8. Remaining manual actions

1. Re-run §5.3 on an unfiltered network and report the result.
2. Optionally `git remote set-url origin https://github.com/brener-fregulia/Unified.git`
   (the redirect works, but the canonical URL avoids surprises).
3. Optionally remove the stale root `build/` and `run/` (§6).
4. Before testing new JARs in Prism: replace the old `qol-unified-…` JAR and
   add Fabric Language Kotlin to the test instance (instructions in
   [linux-setup.md](linux-setup.md) §4).
5. Optionally install `flite` for the narrator.
