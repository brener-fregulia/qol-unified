# Discovery: Rust, Native Code and AOT Compilation for Project Unified

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).
Discovery of 2026-11-28 (brief "Rust/AOT Architecture Discovery"). **Nothing
here is implemented or approved** unless marked so; every recommendation is
**[Proposed]**. Evidence labels: **Verified**, **Inferred**, **Proposed**,
**Open** (defined in
[vanilla world compatibility](../architecture/vanilla-world-compatibility.md)).

Companion documents:
[vanilla world compatibility](../architecture/vanilla-world-compatibility.md),
[Unified: Graphics, Rust and Vulkan](unified-graphics-rust-vulkan.md),
[CachyOS environment audit](../development/cachyos-environment-audit.md).

## 1. Summary

- **Recommendation [Proposed]: no migration.** Java 25 and Kotlin stay the
  only production languages for QoL, Optimizations and Unified Settings.
  Nothing in the current Optimizations catalog is CPU-bound in a way native
  code would fix; both implemented features are about *not doing* work
  (a reverse DNS lookup, a client-side wait).
- **Rust is not rejected.** It becomes an option for a *specific* workload
  only after a benchmark shows a JVM implementation is the bottleneck and
  a native one wins end-to-end, including boundary costs (§12, §13).
- **If native code is ever approved, prefer FFM over JNI** (§4–§5): it is
  final in Java 22+, needs no C glue, and its permission model is the same
  as JNI's. Both print a warning on launchers that do not pass
  `--enable-native-access` (Prism Launcher does not; Mojang's launcher does;
  **Verified**).
- **Native code never enters save paths.** A Rust panic with
  `panic = "abort"` killed the JVM with SIGABRT in a fixture run
  (**Verified**); that is equivalent to `kill -9` during a save.
- **AOT** in the Rust sense (native machine code) does not speed up
  Minecraft's startup, which is JVM class loading, Mixin transformation and
  resource loading. The JVM's own AOT cache (JEP 483/514/515) targets
  startup, but it is a launcher flag, not something a mod can turn on (§8).
- **Graphics** is the one area where native code may become unavoidable
  (vendor SDKs such as AMD FidelityFX are C/C++ libraries); see the
  [Graphics discovery](unified-graphics-rust-vulkan.md).

## 2. Platform facts that shape every option (Verified)

| Fact | Source |
| --- | --- |
| Minecraft 26.3 requires Java 25 (`javaVersion.majorVersion = 25`, runtime component `java-runtime-epsilon`). | Loom's cached `mojang_minecraft_info.json` |
| Mojang's version JSON passes `--enable-native-access=ALL-UNNAMED`, `-Dorg.lwjgl.system.SharedLibraryExtractPath=${natives_directory}/lwjgl`, `-Djava.library.path=${natives_directory}/java`, `-Djna.tmpdir`, `-Dio.netty.native.workdir`. | same file, `arguments.jvm` |
| Prism Launcher's 26.3 metadata passes only `-XX:StackShadowPages=32 -XX:+UnlockDiagnosticVMOptions -XX:-UseBASE64Intrinsics` — **no** `--enable-native-access`. | `~/.local/share/PrismLauncher/meta/net.minecraft/26.3.json` |
| Minecraft already loads many natives: LWJGL 3.4.3 (core, OpenGL, Vulkan, VMA, shaderc, SPIRV-Cross, SDL, FreeType, OpenAL, jemalloc, stb), Netty epoll/kqueue, JNA 5.17, jtracy, lz4-java. | library list in the version JSON |
| LWJGL's Linux natives need at most `GLIBC_2.28` (`liblwjgl.so`); SDL3 2.27; shaderc 2.17; Netty epoll 2.14. | `objdump -T` of the shipped `.so` files |
| Fabric Loader loads mods and Minecraft through its own class loader (Knot), so mod classes live in unnamed modules. | Fabric design; **Inferred** that `ALL-UNNAMED` therefore covers mods; to confirm in N1 (§13) |

## 3. Option A — pure JVM (Java 25, Kotlin)

| Aspect | Assessment |
| --- | --- |
| JIT | C2 inlines across Minecraft, Fabric and mod code, including Mixin-injected code. Native calls are opaque to it: no inlining, escape analysis or loop fusion across the boundary. |
| GC | Java 25 G1 (default) and ZGC (generational only since JDK 24) handle Minecraft's allocation-heavy load well. Allocation pressure is usually reduced more cheaply by fixing the allocating code than by moving it to native memory. |
| Startup | Class loading and Mixin application dominate a mod's startup cost; a small Kotlin feature adds little (see [startup discovery](../qol/discovery/startup-performance-minecraft-26.3.md)). |
| Memory | `MemorySegment`/`Arena` (FFM) give off-heap buffers in pure Java when needed, without native code. Vector API remains incubating in Java 25 (**Inferred** from the JDK 25 JEP list; not used). |
| Integration | Direct access to Minecraft types, Fabric events and Mixins; no marshalling. |
| Distribution | One platform-independent JAR per mod (today's model). |

**Verdict:** the default for all current and foreseeable QoL and
Optimizations work.

## 4. Option B — Rust through JNI

| Aspect | Assessment |
| --- | --- |
| Binding | `jni` crate 0.22.4 (crates.io, 2026-03-16) or `jni-sys` 0.4.1. A Java class declares `native` methods; Rust exports `Java_<class>_<method>` symbols, or registers them with `RegisterNatives`. |
| Call overhead | A JNI transition costs on the order of tens of nanoseconds and blocks inlining; per-call work must be large enough to amortize it (**Inferred**; to be measured, §12). |
| Objects and arrays | Arrays are copied (`Get<Type>ArrayRegion`) or pinned (`GetPrimitiveArrayCritical`, which can stall GC). Minecraft objects (`BlockState`, `ItemStack`, chunk sections) cannot be used natively without converting them field by field. |
| Ownership | Local references live until the native frame returns; global references must be deleted manually; native memory needs an explicit `close()` or a `Cleaner`. |
| Threads | Native threads must `AttachCurrentThread` before calling Java and detach before exiting. |
| Exceptions | Java exceptions are pending state that Rust must check after every call; Rust panics must be caught with `catch_unwind` at every exported function, or the process aborts (§10.3). |
| Crashes | A segfault or abort in native code kills the JVM (hs_err file); nothing in Java can recover. |
| Permissions | `System.load`/`loadLibrary` are restricted since JDK 24 (JEP 472): a warning without `--enable-native-access`, a block "in a future release". Same as FFM. |

**Verdict:** works, but adds C-ABI glue on both sides and the same
permission rules as FFM. Only preferable if a library already ships JNI
bindings.

## 5. Option C — Rust through the Foreign Function & Memory API

| Aspect | Assessment |
| --- | --- |
| Availability | Final since Java 22 (JEP 454); stable in Java 25. `Linker`, `SymbolLookup`, `FunctionDescriptor`, `MemorySegment`, `Arena`. |
| Native side | Plain `extern "C"` functions from a Rust `cdylib`; no JNI headers, no Java types in Rust. |
| Memory ownership | `Arena.ofConfined()`/`ofShared()`/`ofAuto()` bound segment lifetimes; access after close throws instead of crashing. Rust must never keep a pointer into an arena past the call unless the contract says who frees it. |
| Calling conventions | The platform C ABI (System V on Linux, Microsoft x64 on Windows), chosen by `Linker.nativeLinker()`. `Linker.Option.critical(true)` (Java 22+) allows heap segments for short, non-blocking calls (**Inferred** from the API documentation; not measured). |
| Upcalls | Possible (`upcallStub`), but an exception thrown in an upcall terminates the JVM; avoid. |
| Permissions | Restricted methods (`libraryLookup`, `downcallHandle`, `reinterpret`) warn without `--enable-native-access`. |
| Launch constraints | Fabric cannot add JVM flags itself. On Mojang's launcher the flag is present; on Prism (and likely other third-party launchers) the player sees a warning in the console today and a failure once the JDK blocks restricted calls by default. A mod cannot rely on a flag it does not control. |
| Overhead vs JNI | Comparable or lower for downcalls in published OpenJDK measurements (**Inferred**; to be measured in N1). |

### 5.1 Fixture results (Verified, 2026-11-28)

A throwaway Rust `cdylib` (`probe_add`, `probe_sum` over a `MemorySegment`,
`probe_panic`) and a single-file Java 25 program using FFM, built and run in
the session scratchpad (not committed), Temurin 25.0.5.1, rustc 1.96.0:

| Run | Result |
| --- | --- |
| No flag | Works; prints `WARNING: A restricted method in java.lang.foreign.SymbolLookup has been called … Restricted methods will be blocked in a future release unless native access is enabled` |
| `--enable-native-access=ALL-UNNAMED` | Works, no warning |
| `--illegal-native-access=deny` (the future default) | `IllegalCallerException: Illegal native access from an unnamed module` |
| Rust panic in an `extern "C"` function, `panic = "abort"` | JVM terminated, exit 134 (SIGABRT); the Java `catch (Throwable)` never ran |
| Release `cdylib` built on CachyOS (glibc 2.44) | Requires `GLIBC_2.33`: higher than Minecraft's own 2.28 baseline (§9.3) |

**Verdict:** the preferred mechanism if native code is approved, with the
permission caveat recorded as Q-RN2.

## 6. Option D — external native process

Useful only for work that is large, latency-tolerant and isolatable:
offline analysis, batch conversion, tooling.

| Aspect | Assessment |
| --- | --- |
| IPC | Pipes or sockets plus serialization: microseconds to milliseconds per message; unsuitable for per-tick or per-frame work. |
| Isolation | The one real advantage: a crash does not kill the game. |
| Lifecycle | Must start, be supervised, be killed when the game exits (including crashes); orphan processes are a real risk on Windows. |
| Security | A spawned executable from a mod JAR is an obvious malware pattern for anti-virus software and players; signing and extraction add complexity. |
| Distribution | Per-platform executables, same as libraries. |

**Verdict:** not recommended for any in-game feature. Acceptable for
development tools that run outside Minecraft.

## 7. Option E — GraalVM Native Image

- Native Image compiles a **closed world** at build time: all classes known,
  reflection declared, no runtime class definition.
- Fabric's model is the opposite: Mixin rewrites Minecraft classes at load
  time; mods are discovered at startup; LWJGL and Netty load natives
  dynamically. A Fabric client **cannot** be turned into a native executable
  in any practical way (**Inferred**, consistent with the absence of any such
  project in the ecosystem).
- Realistic uses: standalone **tools** (a world-snapshot comparer for the
  [uninstall test](../architecture/vanilla-world-compatibility.md#8-uninstallreinstall-acceptance-test-proposed),
  benchmark report generators) where fast startup matters. Even there, the
  JVM with an AOT cache is often enough. Adding GraalVM needs approval.

## 8. What "AOT" means here

| Term | Meaning | Effect on Unified |
| --- | --- | --- |
| Rust AOT | `rustc`/LLVM compiles to machine code for one target triple at build time. | A `.so`/`.dll` per platform; no JIT warm-up for that code; no cross-boundary inlining. |
| JVM JIT | HotSpot compiles hot bytecode at run time with profile data. | Minecraft and all mods, including Mixin-transformed code. |
| JVM AOT cache | JEP 483 (JDK 24) caches loaded and linked classes; JEP 514 (JDK 25) adds `-XX:AOTCacheOutput` one-step training; JEP 515 (JDK 25) caches method profiles. Used with `-XX:AOTCache=<file>`. | Targets startup and warm-up. A **launcher** flag; mods cannot enable it. Classes defined by custom loaders (Knot loads Minecraft and mods) are likely not cached (**Inferred**, Q-RN5). |
| GraalVM Native Image | Whole-program AOT. | Not applicable to the client (§7). |

Answers to the brief's questions (**Inferred** unless noted):

- **What remains on the JVM:** everything that touches Minecraft or Fabric
  objects, all Mixins, configuration, UI, events, threading decisions.
- **What must be loaded dynamically:** the native library itself, per
  platform, at the first use of the feature (never in a mod initializer, per
  the startup rule).
- **Startup:** native code does not make Minecraft start faster; loading a
  library adds a little (file extraction, `dlopen`, symbol lookup).
- **Throughput:** native code can be faster for large, self-contained loops
  over primitive data (SIMD, no bounds checks), and is slower for chatty
  small calls.
- **GC pressure:** native code allocates outside the heap, so it removes GC
  work only if the data never needs to come back as Java objects.
- **Memory elsewhere:** native allocations are invisible to `-Xmx` and to
  heap tools; a leak there grows the process without a Java-side signal.

## 9. Build settings, ABI and packaging (if native code is approved)

### 9.1 Cargo release profile [Proposed]

```toml
[profile.release]
opt-level = 3
lto = "fat"          # measure "thin" vs "fat"; keep whichever wins
codegen-units = 1
panic = "unwind"     # every exported fn uses catch_unwind and returns an error code
debug = "line-tables-only"
strip = "debuginfo"  # ship stripped; keep the unstripped file as a CI artifact for crash symbols
```

- **`panic`:** `unwind` plus `catch_unwind` at each `extern "C"` function, so
  a bug becomes an error code and the Java side disables the feature.
  `abort` is acceptable only if a panic can never happen while game state is
  at stake; the fixture shows it takes the whole game down.
- **Target CPU:** **no `target-cpu=native`** in distributed builds (it would
  encode the build machine; CachyOS itself runs x86-64-v3 repositories).
  Baseline `x86-64` (v1) or `x86-64-v2` (Q-RN3); hot kernels may use
  `is_x86_feature_detected!` with a scalar fallback.
- **Reproducibility:** pinned toolchain (`rust-toolchain.toml`),
  `Cargo.lock` committed, `--locked` builds, `--remap-path-prefix` to strip
  local paths, `SOURCE_DATE_EPOCH` where relevant.

### 9.2 ABI contract [Proposed]

- C ABI only (`extern "C"`, `#[repr(C)]`), no Rust types across the boundary.
- An exported `unified_<mod>_abi_version() -> u32` checked before any other
  symbol; a mismatch disables the feature (§10).
- Every function returns a status code; outputs go to caller-provided
  buffers; no native-owned memory escapes without a matching `free` function.
- No native threads calling back into Java.

### 9.3 Platforms and baselines

| Platform | Target triple | Toolchain | Notes |
| --- | --- | --- | --- |
| Windows 11 x86_64 | `x86_64-pc-windows-msvc` | MSVC (Windows CI runner) | Link the CRT statically (`-C target-feature=+crt-static`) so no Visual C++ redistributable is needed (**Inferred**). Cross-building from Linux with `cargo-xwin` (installed on this machine) needs Microsoft's SDK licence accepted: CI on Windows is simpler. |
| Linux x86_64 | `x86_64-unknown-linux-gnu` | GNU, built against **glibc ≤ 2.28** | Matches LWJGL's own requirement (§2), so Unified never narrows the set of Linux systems Minecraft runs on. A CachyOS build is **not** portable (fixture: `GLIBC_2.33`). Build in an old-glibc container (e.g. a manylinux_2_28 or Enterprise Linux 8 image) or use a glibc-versioned linker wrapper (a new tool: needs approval). |
| Linux musl | `x86_64-unknown-linux-musl` | — | Not usable: a musl `cdylib` loaded into a glibc JVM is unsupported. |
| macOS, ARM64 | — | — | **[Deferred]**: not V1 targets; nothing in the design prevents adding them later. |

### 9.4 Packaging and loading [Proposed]

1. Libraries live in the mod JAR under `natives/<os>-<arch>/`, with a
   SHA-256 manifest.
2. On first use, the feature copies its library to a versioned cache
   directory (`<cache>/<mod_id>/natives/<version>-<sha256>/`, location per
   Q-VWC1), verifies the hash, and loads it **by absolute path** only (never
   `java.library.path`, the working directory or `PATH`).
3. Old versions are deleted at next start when not in use; on Windows a
   loaded DLL cannot be deleted, so cleanup is best-effort and retried.
4. Missing entry for the current OS/architecture → the feature reports
   "unsupported on this platform" and stays on the JVM path.
5. Libraries are never loaded from a world directory, a resource pack or a
   download.

## 10. Failure policy [Proposed]

### 10.1 Startup-time (before the feature changes anything)

| Failure | Behavior |
| --- | --- |
| Library missing for this platform | Feature inactive; reason shown in Unified Settings; log once with feature id. |
| Wrong architecture, unresolved symbol, `UnsatisfiedLinkError` | Same. |
| ABI version mismatch | Same; never call further symbols. |
| CPU lacks a required instruction | Detected *before* calling optimized code (runtime feature detection); scalar path or inactive. |
| Native access denied (`IllegalCallerException`) | Same; message names the missing `--enable-native-access` flag. |

### 10.2 Runtime

| Failure | Behavior |
| --- | --- |
| Native function returns an error | Feature disables itself for the session; vanilla path from then on. |
| Rust panic (caught by `catch_unwind`) | Same as an error. |
| Rust panic (uncaught) / segfault / abort | **Not recoverable**: the process ends (fixture §5.1). This is why native code is kept out of save and world-IO paths. |
| JVM shutdown during native work | The Java side owns the call; shutdown hooks wait for an in-flight call to return or skip new calls; native code never starts threads that outlive the call. |

### 10.3 What Unified will never claim

That every native failure can be caught. Panics across `extern "C"` abort the
process; memory-safety bugs in `unsafe` code or C/C++ dependencies corrupt
the process.

## 11. Optimizations use-case matrix

| Area | Classification | Reasoning |
| --- | --- | --- |
| DNS and IP handling | **Pure JVM preferred** | A literal-IP check is microseconds; the cost avoided is a network round trip. Native resolvers would bypass the JDK's resolver SPI and blocklist path that Fast IP Ping deliberately preserves. |
| Fast IP Ping (implemented) | **Not suitable for native** | It removes work; nothing CPU-bound remains. Security boundaries (vanilla blocklist, hostnames, SRV) live in JVM code. No rewrite. |
| FastQuit (implemented) | **Not suitable for native** | Lifecycle and ownership of the server thread; native code would add a failure mode to the save path. No rewrite. |
| Startup optimizations | **Pure JVM preferred** | Costs are class loading, Mixin, resource and DFU work inside the JVM; native code cannot accelerate them. The JVM AOT cache is a launcher-level topic (Q-RN5). |
| Memory optimizations | **Pure JVM preferred** | Deduplication and allocation fixes act on Java objects; moving data off-heap requires converting it back for Minecraft. |
| Chunk processing (meshing, lighting) | **Rust potentially beneficial**, high integration cost | Data-parallel loops over primitive arrays are the best native candidates, but Minecraft's chunk data lives in Java objects (palettes, `BlockState`) and Sodium already owns client meshing. Requires a benchmark first. |
| Data compression | **Pure JVM preferred** | Minecraft already uses zlib (JDK, native), lz4-java (native-backed) for region and network data; world-format changes are prohibited anyway. |
| Parsing and transformations (JSON, NBT, resources) | **Pure JVM preferred** | Small, object-rich work; boundary costs would dominate. |
| CPU-intensive background tasks (e.g. map rendering, image processing, search indexes) | **Rust potentially beneficial** | Only for large batches of primitive data off the main threads, with a benchmark. None is in the catalog today. |
| Anything touching saves, world IO, locks | **Not suitable for native** | Vanilla compatibility contract §4.3. |

Nothing qualifies as **Rust strongly justified** today.

## 12. Benchmark methodology [Proposed]

Rule: a native implementation is better only if it wins **end-to-end on
identical workloads**, including marshalling, loading and warm-up, on both
supported platforms. Compiled-to-native is not evidence.

| Level | Tool | Measures |
| --- | --- | --- |
| Microbenchmark | JMH (new test dependency: needs approval) | Throughput and latency of the JVM vs FFM vs JNI variant on the same inputs; per-call boundary cost; allocation rate (`-prof gc`). |
| Integrated client | Fabric client game test in a disposable directory, with JFR (`-XX:StartFlightRecording`) | Frame time (P50/P95/P99), GC pauses, allocation rate, CPU time, startup and shutdown durations, background save completion. |
| Real gameplay | Production install in a dedicated test instance (per [startup discovery](../qol/discovery/startup-performance-minecraft-26.3.md) Q-SP3) | The same metrics on a scripted route; manual QA only. |

Requirements:

- the same JDK build (record vendor and version), JVM flags, GC, heap size,
  Minecraft and mod versions, world seed, render distance and window size;
- JMH: at least 5 forks, warm-up iterations until steady state, report
  mean ± 99.9 % CI and the distribution, not one number;
- integrated runs: at least 10 runs per variant, alternating order, first run
  discarded; report median and P95/P99 with bootstrap confidence intervals;
- CPU governor and background load recorded (CachyOS defaults to an
  aggressive scheduler; note `scx`/governor state); laptop runs on AC power;
- fixtures (seeds, scripts) committed; raw results stored outside the
  repository unless approved (startup discovery Q-SP4);
- **no fabricated or estimated numbers** in specifications.

## 13. Build and CI architecture [Proposed, not created]

```text
Unified/
├── mods/…, libs/…            Gradle, JVM only (unchanged)
└── native/                   created only when a native PoC is approved
    ├── Cargo.toml            Cargo workspace, independent of Gradle
    ├── rust-toolchain.toml   pinned Rust version
    └── <crate>/              one crate per consuming mod (e.g. optimizations-probe)
```

- **QoL never depends on Rust.** Unified Settings never does either.
- A consuming module (Optimizations, Graphics) gets a Gradle property, e.g.
  `-Punified.natives=<dir>`, pointing to **prebuilt** libraries. Without it,
  the module builds its JVM parts and the native feature is absent from that
  JAR (inactive at run time, §10.1). A local `cargoBuild` task may exist for
  convenience but is never part of `build` by default.
- CI: a separate `native-<mod>.yml` workflow per native crate, matrix
  `{linux-x86_64 in an old-glibc container, windows-x86_64 MSVC}`, caching
  `~/.cargo` and `target/`, running `cargo test --locked`, `cargo clippy`,
  `cargo deny` or `cargo audit` (new tools: need approval), and uploading the
  libraries plus checksums. Release packaging downloads those artifacts into
  the JAR.
- Module versions stay independent: a native crate carries the consuming
  mod's version; its ABI version is separate (§9.2).

### 13.1 Proof-of-concept candidates (if the PO wants evidence)

- **N1 [Proposed]:** a development-only Fabric game test that loads a trivial
  Rust `cdylib` through FFM inside Knot, on Mojang-launcher arguments and on
  Prism-like arguments, recording warnings, load time and boundary cost.
  Lives under `spikes/`, not in a production module. Answers Q-RN2 and the
  Knot/`ALL-UNNAMED` question (§2).
- No production native feature is proposed.

## 14. Recommended language policy [Proposed]

1. Java 25 and Kotlin remain the production languages (monorepo policy
   unchanged).
2. Native code (Rust preferred over C/C++ for new code) is allowed only
   - for a workload with a measured JVM bottleneck and an approved benchmark
     showing an end-to-end win on Windows and Linux, or
   - where a required SDK exists only as a native library (Graphics);
   - and never in QoL, Unified Settings, save/world-IO/lock paths, registry
     or configuration code.
3. Each native feature has a JVM or vanilla fallback and an off switch.
4. FFM is the default binding; JNI only for an existing JNI library.
5. No native code before an approved specification, and no Horizon code before
   M5 (existing rule).

## 15. Decision: migration / no migration [Proposed]

**No migration.** Fast IP Ping and FastQuit stay in Kotlin/Java: neither has
a CPU-bound part, and rewriting them would weaken their security and
save-safety boundaries. No Optimizations feature in the catalog justifies
native code. Revisit only with a benchmark (§12) for a new, specified
workload.

## 16. Open questions

- **Q-RN1 [Open]:** approve the "no migration" decision (§15) and the
  language policy (§14)?
- **Q-RN2 [Open]:** a native feature would print a JDK warning on Prism and
  fail once the JDK denies native access by default. Accept that (document
  the flag for players), or reject native features that depend on launcher
  flags?
- **Q-RN3 [Open]:** CPU baseline for any future native build: x86-64 (v1) or
  x86-64-v2?
- **Q-RN4 [Open]:** run the N1 spike (§13.1) now, or only when a concrete
  native feature is proposed?
- **Q-RN5 [Open]:** investigate the JVM AOT cache (JEP 483/514/515) as a
  documented, optional player setting for startup, with a production-install
  benchmark? It needs no code in the mods.

## Sources

- [JEP 454: Foreign Function & Memory API](https://openjdk.org/jeps/454)
- [JEP 472: Prepare to Restrict the Use of JNI](https://openjdk.org/jeps/472)
- [JEP 483: Ahead-of-Time Class Loading & Linking](https://openjdk.org/jeps/483)
- [JEP 514: Ahead-of-Time Command-Line Ergonomics](https://openjdk.org/jeps/514)
- [JEP 515: Ahead-of-Time Method Profiling](https://openjdk.org/jeps/515)
- [RFC 2945: "C-unwind" ABI](https://rust-lang.github.io/rfcs/2945-c-unwind-abi.html)
- crates.io API, queried 2026-11-28: `jni` 0.22.4, `jni-sys` 0.4.1
- Local, Verified: Loom cache `~/.gradle/caches/fabric-loom/26.3/`, Prism
  metadata `meta/net.minecraft/26.3.json`, Prism library natives (`objdump`),
  scratchpad FFM/Rust fixture.
