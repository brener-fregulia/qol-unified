# Discovery: Unified: Graphics — Vulkan, Shaders, Upscaling and Rust

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).
Discovery of 2026-11-28 (brief "Rust/AOT Architecture Discovery", §9–§12).
**Nothing here is approved or implemented**; Unified: Graphics has no module,
mod id or version yet. Evidence labels: **Verified**, **Inferred**,
**Proposed**, **Open** (defined in
[vanilla world compatibility](../architecture/vanilla-world-compatibility.md)).
Third-party claims that could not be checked first-hand are marked
"**Inferred (reported)**".

Related: [Rust/AOT feasibility](rust-native-aot-feasibility.md) (bindings,
packaging, failure policy), [vanilla world compatibility](../architecture/vanilla-world-compatibility.md).

## 1. Summary

- **The key finding:** Minecraft 26.3 already has a **backend-neutral
  renderer ("renderpearl") with an OpenGL and a Vulkan backend**, a
  GLSL → SPIR-V shader pipeline (shaderc) and SPIR-V reflection
  (SPIRV-Cross). Vulkan is an opt-in, experimental, restart-required
  setting (**Verified**, §2).
- So "Vulkan-first" no longer means *writing* a Vulkan renderer. The
  sensible path is to **build Unified: Graphics on vanilla's renderpearl
  abstraction in Java/Kotlin**, which works on both backends, and to add
  native code only where a vendor SDK forces it (FSR) [Proposed].
- **A Rust-owned Vulkan renderer is not recommended**: it would replace
  Minecraft's renderer, conflict with Sodium (which itself drives
  renderpearl's Vulkan backend) and duplicate what Mojang now maintains.
- **Iris shader packs do not run on Vulkan** today (Iris 1.11.7 has no
  Vulkan code, **Verified**); a Unified loader for existing packs is a
  separate, very large problem (§6).
- **FSR:** spatial upscaling is feasible on both backends; temporal FSR needs
  motion vectors that vanilla does not produce (**Verified**: no vanilla
  shader writes them) and the FidelityFX SDK, which targets DirectX 12 and
  Vulkan only, so FSR 3.1+/4 would be Vulkan-backend-only (§7).

## 2. Minecraft 26.3 rendering architecture (Verified from bytecode)

Inspected with `javap` and `unzip` on Loom's cached
`minecraft-client.jar` (26.3, released 2026-09-15, unobfuscated names).

### 2.1 Layers

| Package | Role |
| --- | --- |
| `com.mojang.renderpearl.api.*` | Backend-neutral API: `GpuBackend`, `GpuDevice`, `GpuSurface` (present modes `IMMEDIATE`, `MAILBOX`, `FIFO`, `FIFO_RELAXED`), buffers, textures, samplers, `RenderPipeline`, command encoders, render passes, `DeviceFeatures`/`DeviceLimits`/`DeviceInfo`, `GpuDeviceLossException`, `GpuOutOfMemoryException`. |
| `com.mojang.renderpearl.frontend` | `FrontendGpuDevice`, `FrontendCommandEncoder`, `FrontendRenderPass`, `TracyGpuProfiler`; `frontend.shaders`: `GlslCompiler` (shaderc), `SPIRVModule`, `SpvUtil`, `PipelineBuilder` (SPIRV-Cross reflection). |
| `com.mojang.renderpearl.backend.opengl` | `GlBackend`, `GlDevice`, DSA (core and emulated), `GlPipelineRecompiler` (SPIR-V back to GLSL via SPIRV-Cross), buffers, fences, debug labels (64 classes). |
| `com.mojang.renderpearl.backend.vulkan` | `VulkanBackend`, `VulkanInstance`, `VulkanPhysicalDevice`, `VulkanDevice`, `VulkanQueue` (submissions with semaphore ops), `VulkanCommandEncoder`, `VulkanRenderPass`, `VulkanRenderPipeline`, `VulkanGpuSurface`, `VulkanTransientMemory`, `DestructionQueue`; `checkpoints` (AMD buffer markers, NVIDIA diagnostic checkpoints for device-loss diagnosis); `init` (feature sets). |
| `com.mojang.blaze3d.*` | Engine-level code on top: `RenderSystem`, `framegraph.FrameGraphBuilder`/`FramePass`, `pipeline.RenderTarget`/`MainTarget`/`PipelineCache`, vertex formats, platform window. |
| `assets/minecraft/shaders/` | 63 `core/` GLSL files plus `include/` and `post/` (blur, invert, entity outline, spider…), compiled at run time. |

### 2.2 Backend selection

- Option `preferredGraphicsBackend` in `options.txt` (`"default"` on the
  Prism 26.3 instance, read-only check). UI: **Graphics API** — Default /
  Prefer OpenGL / **Prefer Vulkan (Experimental)**; tooltip: "Experimental.
  Might reduce performance or cause instability on some systems"; changing it
  requires a restart (`options.graphicsApi.restart`).
- `PreferredGraphicsApi.getBackendsToTry()`: **Default and OpenGL try
  `GlBackend` then `VulkanBackend`; Vulkan tries `VulkanBackend` then
  `GlBackend`**. Vanilla therefore already has a startup fallback.
- Failure reasons (`BackendCreationException.Reason`): `PLATFORM_ERROR`,
  `VULKAN_LOADER_MISSING`, `VULKAN_INSTANCE_CREATION_FAILED`,
  `VULKAN_NO_DEVICE`, `VULKAN_KNOWN_PROBLEMATIC`,
  `VULKAN_DEVICE_VERSION_TOO_LOW`, `VULKAN_NO_GRAPHICS_QUEUE`,
  `VULKAN_MISSING_EXTENSION`, `VULKAN_MISSING_FEATURE`, `OPENGL_MISSING`,
  `OTHER`; reported in telemetry properties.
- The Vulkan backend references `VK_API_VERSION_1_2` and enables feature sets
  ("Minecraft base required" plus optional ones) built from:
  `VK_KHR_swapchain`, `VK_KHR_dynamic_rendering`, `VK_KHR_synchronization2`,
  `VK_KHR_push_descriptor`, `VK_EXT_multi_draw`,
  `VK_EXT_vertex_attribute_divisor`, `VK_EXT_calibrated_timestamps`,
  `VK_AMD_buffer_marker`, `VK_NV_device_diagnostic_checkpoints`,
  `VK_KHR_portability_subset` and features `dynamicRendering`,
  `synchronization2`, `timelineSemaphore`, `shaderDrawParameters`,
  `multiDrawIndirect`, `drawIndirectFirstInstance`, `samplerAnisotropy`,
  `hostQueryReset`, `fillModeNonSolid`. Which are *required* was not
  decoded.
- `ENABLE_VULKAN_RENDERDOC_CAPTURE=1` is read by `VulkanBackend` (debug aid).
- Natives: `lwjgl-vulkan` ships natives only for macOS (MoltenVK); on
  Windows and Linux the **system Vulkan loader** is used, which is why
  `VULKAN_LOADER_MISSING` exists.

### 2.3 Other findings

- `PipelineCache` is an in-memory map of compiled pipelines; vanilla writes
  no shader cache to disk (no on-disk cache class found; drivers keep their
  own caches).
- `GpuDeviceLossException` is thrown from `VulkanUtils`; no class under
  `net.minecraft` references it, so device loss most likely ends in a crash
  report (**Inferred**; Q-G6).
- No vanilla shader writes motion vectors or velocity (`grep` over
  `assets/minecraft/shaders`).
- **Inferred (reported):** 26.4 snapshots make "Default" mean "Prefer
  Vulkan" (community articles, not verified against Mojang notes). If true,
  Vulkan becomes the common case within one release line.

## 3. Ecosystem state for 26.3

| Project | Finding | Evidence |
| --- | --- | --- |
| Sodium 0.9.3-beta.1+mc26.3 (Polyform Shield) | Contains `VKDrawContext`, `VKMultiDrawBatch`, `VKIndirectDrawBatch` next to `GLDrawContext`: it renders terrain on vanilla's Vulkan backend. Declares `breaks: vulkanmod`. | **Verified** (local JAR in the Prism instance, read-only) |
| Iris 1.11.7+mc26.3 (LGPL-3.0) | No Vulkan-related classes; requires Sodium 0.9.x. Reportedly switches itself off on Vulkan. | **Verified** (no classes) / **Inferred (reported)** (behavior) |
| VulkanMod (LGPL-3.0) | Latest GitHub release 0.6.0 (2026-03-04) targets 1.21.11; branches up to `1.21`, `dev`; no 26.x release. A replacement renderer that predates vanilla Vulkan. | **Verified** (GitHub API) |
| Vitrail Shaders, Vultrum | Reported Fabric mods for shader packs and GPU culling on 26.3's Vulkan backend. | **Inferred (reported)**; Modrinth unreachable from this network |
| FidelityFX SDK 2.4 | Reported to add Vulkan at parity with DX12 for FSR Upscaling 4.1.2, FSR 3.1.6 and frame generation; FSR 4 frame generation reported to need Radeon RX 9000 and a recent Windows driver; Linux not confirmed by AMD. | **Inferred (reported)**; to verify on GPUOpen before any spec |

## 4. Four different engineering problems

| # | Problem | What it means here | Size |
| --- | --- | --- | --- |
| 1 | **Custom shader for Unified: Graphics** | Unified-authored GLSL passes inserted through vanilla's pipeline (frame graph, post chains). Runs on both backends because vanilla compiles GLSL → SPIR-V for both. | Small to medium |
| 2 | **Loader for existing shader packs** | Implementing the OptiFine/Iris pack format: G-buffer layout, `shaders.properties`, dozens of uniforms, shadow pass, composite/deferred chains, dimension overrides, custom textures, and compatibility with Sodium's terrain path. | Very large; already Iris's (OpenGL) and reportedly Vitrail's (Vulkan) scope |
| 3 | **Replacement renderer** | Owning world, entity and UI rendering (VulkanMod's model). | Very large; now redundant with vanilla Vulkan and incompatible with Sodium |
| 4 | **Vulkan translation/compatibility layer** | Translating OpenGL calls to Vulkan (Zink-like) or between abstractions. | Not useful: vanilla already abstracts both |

## 5. Architecture options for Unified: Graphics

| Option | Description | Assessment |
| --- | --- | --- |
| **GA. JVM on renderpearl** | Kotlin/Java logic; Java Mixins into `blaze3d`/`renderpearl` frontend; shaders in GLSL compiled by vanilla; LWJGL Vulkan (already on the classpath) when Vulkan-only calls are needed. | **Recommended [Proposed].** No new runtime dependency; both backends; same distribution as other Unified mods; coexists with Sodium if hook points are chosen carefully (Q-G3). |
| **GB. Rust renderer (ash)** | A native Vulkan renderer owning the device and swapchain. | **Rejected for now.** Replacement-renderer scope; must fight vanilla and Sodium for device/window ownership; highest crash risk. |
| **GC. Native helper on vanilla's device** | JVM integration as in GA; a native library (Rust or the vendor's C/C++) receives vanilla's `VkInstance`/`VkDevice`/queue handles and records work into Unified-owned command buffers or images. | **Only where an SDK requires it** (FSR 3.1+/4). Requires a handle-access path into `VulkanDevice` (Mixin/accessor) and strict synchronization with vanilla's queue submissions (`VulkanQueue.Submission` semaphores). |
| **GD. wgpu** | A Rust-native abstraction over Vulkan/DX12/Metal/GL. | **Not suitable** as the main layer: it wants to own device and surface, duplicates renderpearl, and its interop with an external `VkDevice` is an unsafe, backend-specific path. Fine for standalone tools. |

### 5.1 Rust Vulkan bindings (Verified on crates.io, 2026-11-28)

| Crate | Latest | Note |
| --- | --- | --- |
| `ash` | 0.38.0+1.3.281 (2024-04-01) | Thin, complete raw bindings; the usual choice for GC. No release with Vulkan 1.4 headers yet. Works with handles created elsewhere. |
| `erupt` | 0.23.0+213 (2022-11-14) | Unmaintained; do not use. |
| `vulkano` | 0.35.2 (2025-08-16) | Safe wrapper that expects to own objects; poor fit for sharing vanilla's device. |
| `wgpu` / `naga` | 30.0.2 (2026-11-12) | See GD. |
| `gpu-allocator` | 0.28.0 (2025-09-26) | Only if Unified allocates its own Vulkan memory natively; vanilla already uses VMA. |
| `shaderc` / `spirv-tools` / `rspirv` | 0.10.1 / 0.13.3 / 0.13.0 | Not needed at run time: vanilla already ships shaderc and SPIRV-Cross through LWJGL. Build-time validation can use the system `glslc`/`spirv-val`. |

On the JVM, LWJGL 3.4.3's `lwjgl-vulkan`, `lwjgl-vma`, `lwjgl-shaderc` and
`lwjgl-spvc` are already loaded by Minecraft, so option GA needs **no new
dependency** (**Verified** library list).

### 5.2 Native/C++ interoperability

The FidelityFX SDK is a C/C++ API shipped as prebuilt libraries
(**Inferred**). Calling it needs either FFM bindings directly from Java
(no Rust at all) or a small Rust/C shim. Rust adds value only if Unified
logic must also live natively; otherwise **FFM straight to the SDK** is the
smaller design [Proposed]. Licensing and redistribution of the SDK binaries
must be checked by the PO before any use (AGENTS: no licence decisions).

## 6. Shader-pack compatibility

- Iris (OpenGL) is the reference implementation of the pack format; it
  depends on Sodium and is LGPL-3.0. Unified must not copy its code
  (AGENTS: no copied code); an independent implementation would be a
  multi-month project with an unbounded compatibility tail (every pack uses
  different features).
- On Vulkan, packs need translation of pack GLSL to vanilla's SPIR-V pipeline
  and a G-buffer layout Sodium agrees with. Reportedly Vitrail does this.
- **Recommendation [Proposed]:** do not attempt a pack loader. If packs
  matter, ensure Unified: Graphics *coexists* with Iris (OpenGL) and with
  Vulkan pack loaders: detect them, and turn off overlapping Unified passes.

## 7. Upscaling and frame generation feasibility

| Technique | Needs | Feasibility |
| --- | --- | --- |
| Render-scale + spatial upscale (FSR 1-style EASU + RCAS, or a Unified-written equivalent) | Render the world to a smaller target, upscale before UI | **Feasible on both backends** with GLSL passes (option GA). Vanilla UI must stay at native resolution. |
| Temporal upscaling (FSR 2/3.1/4-style) | Per-pixel motion vectors, depth, sub-pixel camera jitter, reactive/transparency masks, history reset on camera cuts | **Hard.** Static terrain motion can be derived from depth and the previous camera matrix; entities, animated textures, particles, water and translucency need per-object vectors that vanilla and Sodium do not produce. Jitter changes the projection Sodium also uses. |
| FSR 3.1+/4 via the SDK | Vulkan backend; SDK binaries; vendor hardware/driver limits | **Vulkan-backend-only**, and only as a native integration (GC). FSR 4's ML upscaler and FG 4 are reported to target RDNA 4 on Windows. Linux status unconfirmed. |
| Frame generation | Control of present timing and swapchain (`VulkanGpuSurface`), UI composition, latency compensation | **Research only.** Owning presentation inside vanilla's surface is the most invasive change of all. |

The development machine (Ryzen 5 5625U, Radeon "Barcelo"/Renoir iGPU, RADV,
Vulkan 1.4.363) is RDNA-less (GCN 5 based), so it can test spatial upscaling
and the vanilla Vulkan backend, not FSR 4 (**Inferred** from the device id).

## 8. Native architecture constraints

- **Window and swapchain** are owned by vanilla (`GpuBackend.createWindow`,
  `GpuSurface`); Unified never creates its own.
- **Render thread** owns all GPU calls; no Unified GPU work from other
  threads unless vanilla's API allows it.
- **Synchronization:** native work submitted to vanilla's queue must chain
  through vanilla's semaphores/fences; never `vkDeviceWaitIdle` in a frame.
- **Resource uploads** through renderpearl buffers/textures where possible;
  Unified images on the native side must be destroyed before vanilla tears
  the device down (vanilla's `DestructionQueue` model).
- **Shader compilation:** GLSL compiled by vanilla at load time; compile
  failures surface as `ShaderCompileException` and must disable the pass,
  not crash.
- **Caches:** any Unified shader or pipeline cache is disposable (vanilla
  compatibility contract §4.1); none is needed in V1 because vanilla compiles
  in memory.
- **Driver differences:** RADV, AMDVLK/proprietary, NVIDIA, Intel ANV and
  Windows drivers differ in extensions (vanilla's optional feature sets
  already reflect this). Capability detection uses renderpearl's
  `DeviceFeatures`/`DeviceInfo`, not Unified's own probing.

## 9. Failure policy for Graphics [Proposed]

| Situation | Startup-time behavior | Runtime behavior |
| --- | --- | --- |
| Vulkan unavailable or rejected | Vanilla already falls back to OpenGL (§2.2). Unified passes that work on OpenGL stay on; Vulkan-only features are inactive with a reason. | — |
| Unsupported GPU or missing feature | Feature inactive with a reason, from `DeviceFeatures`. | — |
| Native library missing / ABI mismatch | As in [Rust feasibility](rust-native-aot-feasibility.md) §10.1. | — |
| Shader compilation failure | That pass disabled; log with feature id. | Same on resource reload. |
| GPU out of memory | Feature disabled; resources released. | Same. |
| Device lost | — | Not recoverable inside the session (vanilla has no handler, §2.3). Unified must not mask it; ensure the crash report names any active Unified passes. |
| Native panic/crash | — | Ends the process; therefore no native code before the world is fully loaded *and* never during saving (FastQuit's background save keeps the server thread alive while the client renders the title screen). |

Rules:

- Unified never changes `preferredGraphicsBackend` on the player's behalf
  (requires PO approval, vanilla compatibility contract §4.3); it may show
  that a feature needs "Prefer Vulkan".
- Switching rendering paths happens only at startup; a failed Vulkan-only
  feature does not try to switch the backend while running. A clean restart
  may be needed to recover.
- Graphics never writes world data; removing it restores vanilla rendering.

## 10. Milestones [Proposed]

Each stage ends with a go/no-go review by the PO. None starts without
approval; G0 is this document.

### G0 — Rendering architecture discovery (this document)

- **Objective:** understand 26.3 rendering, ecosystem and options.
- **Acceptance:** §2–§9 written with evidence labels; open decisions listed.
- **Go/no-go:** PO chooses GA (or another option) and approves G1.

### G1 — Graphics module skeleton and capability report (JVM only)

- **Objective:** `:mods:graphics` with its own id, version, settings entry
  and a diagnostics view of backend, device, driver and feature sets; no
  rendering change.
- **Prerequisites:** mod id and name (Q-G1); module approved.
- **Acceptance:** loads with and without QoL/Optimizations; shows correct
  backend on OpenGL and Vulkan; zero rendering difference (screenshot
  comparison); passes the uninstall test trivially.
- **Benchmark:** startup cost vs baseline (startup discovery method).
- **Fallback:** none needed (no behavior).
- **Risks:** low.
- **Go/no-go:** clean coexistence with Sodium and Iris installed.

### G2 — First custom pass through vanilla's pipeline

- **Objective:** one simple Unified GLSL post pass (e.g. a sharpening or
  colour pass) in the frame graph, on both backends.
- **Prerequisites:** G1; identified hook point (Q-G3).
- **Acceptance:** identical output on OpenGL and Vulkan within tolerance;
  pass toggles live; shader error disables only the pass; works with Sodium;
  turns off with Iris packs active.
- **Benchmark:** GPU frame-time delta (Tracy/timer queries) and P95/P99 frame
  time, pass on vs off.
- **Fallback:** pass off.
- **Risks:** Mixin targets in a young, changing renderer.
- **Go/no-go:** hook stable across a 26.3.x update; overhead acceptable.

### G3 — Render scale + spatial upscaling

- **Objective:** render the world at a lower resolution and upscale (EASU/RCAS
  style, independently written), UI at native resolution.
- **Prerequisites:** G2.
- **Acceptance:** correct UI, screenshots, F1, resizing, fullscreen switch;
  both backends; Sodium on.
- **Benchmark:** FPS and frame-time percentiles at fixed scales, on the iGPU
  and a discrete GPU.
- **Fallback:** scale 1.0.
- **Go/no-go:** measurable FPS gain with acceptable image quality on target
  hardware.

### G4 — Native interop proof of concept (only if an SDK is pursued)

- **Objective:** load a trivial native library inside the client (FFM),
  receive vanilla's Vulkan handles, record one native compute dispatch on a
  Unified-owned image, synchronized with vanilla's submission.
- **Prerequisites:** Rust feasibility Q-RN1/Q-RN2 decided; Vulkan backend
  only.
- **Acceptance:** validation layers clean (`VK_LAYER_KHRONOS_validation` is
  installed locally); no leaks at exit; graceful inactive state on OpenGL,
  missing library, wrong ABI.
- **Benchmark:** boundary and synchronization cost per frame.
- **Risks:** access to vanilla internals; driver differences.
- **Go/no-go:** zero validation errors on RADV and one Windows vendor driver.

### G5 — Temporal reconstruction research

- **Objective:** motion vectors for terrain (depth reprojection) and a plan for
  entities/particles/translucency; decide whether temporal upscaling is
  achievable without owning Sodium's terrain path.
- **Acceptance:** a written evaluation with captured debug views; no
  production code required.
- **Go/no-go:** a credible path for moving objects, or stop at G3.

### G6 — FSR SDK integration exploration

- **Objective:** evaluate FSR 3.1/4 via the FidelityFX SDK on the Vulkan
  backend: licence, binaries per platform, hardware matrix, Linux support.
- **Prerequisites:** G4 go, G5 go, PO licence review.
- **Go/no-go:** licence acceptable and SDK runs on Windows and Linux targets.

### G7 — Shader-pack coexistence and frame generation research

- **Objective:** document coexistence with Iris and Vulkan pack loaders;
  study frame generation only if G6 succeeded.
- **Acceptance:** research documents; no promise of pack compatibility or FG.

## 11. Risks

- **Moving target:** renderpearl and the Vulkan backend are new and labelled
  experimental; Mixins into them will break across releases.
- **Ecosystem coupling:** Sodium owns terrain rendering; Iris owns OpenGL
  packs; Unified must avoid conflicts rather than replace them.
- **Licences:** Sodium (Polyform Shield) forbids competing reuse; Iris and
  VulkanMod are LGPL; FidelityFX SDK terms unverified.
- **Hardware coverage:** the only local GPU is an integrated RDNA-less AMD
  part on RADV; NVIDIA, Intel and Windows drivers need other machines.
- **Crash surface:** any native GPU code can end the process (device loss,
  driver bugs).

## 12. Open decisions

- **Q-G1 [Open]:** Graphics mod id and public name (e.g. `unified_graphics`),
  and whether to create the module before G1 is approved.
- **Q-G2 [Open]:** approve option GA (JVM on renderpearl) as the
  architecture, with native code only for SDK integration (GC)?
- **Q-G3 [Open]:** which hook points (frame graph passes, `RenderTarget`,
  post chains) Unified may Mixin into, and the Sodium/Iris coexistence rule.
- **Q-G4 [Open]:** is FSR (SDK) a goal, given it would be Vulkan-only and
  hardware-limited, or is spatial upscaling (G3) enough?
- **Q-G5 [Open]:** shader-pack support: coexist only (recommended) or attempt a
  loader?
- **Q-G6 [Open]:** device-loss behavior: accept vanilla's crash, or explore
  recovery once vanilla's handling is understood?
- **Q-G7 [Open]:** may Unified suggest or set "Prefer Vulkan" for features
  that need it?

## Sources

- Local, Verified: `~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar`
  (`javap`), `mojang_minecraft_info.json`; Sodium and Iris JARs in the Prism
  26.3 instance (read-only); `vulkaninfo` on this machine.
- [VulkanMod releases](https://github.com/xCollateral/VulkanMod/releases)
  (GitHub API, 2026-11-28)
- crates.io API (2026-11-28): `ash`, `erupt`, `vulkano`, `wgpu`, `naga`,
  `gpu-allocator`, `shaderc`, `spirv-tools`, `rspirv`
- [FidelityFX SDK releases](https://github.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK/releases)
  and [VideoCardz: FSR 4 Vulkan](https://videocardz.com/newz/amd-finally-confirms-vulkan-support-for-fsr-4)
  (reported, to verify)
- Community reports (not authoritative): [Vitrail Shaders](https://modrinth.com/mod/vitrail-shaders),
  [Vultrum](https://github.com/DebZakari/Vultrum),
  [26.4 Vulkan default](https://doortogaming.com/blog/minecraft-26-4-vulkan-default-what-breaks)
