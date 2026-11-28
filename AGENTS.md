# Agent Guide

Instructions for coding agents (and humans) contributing to Project Unified:
Unified: QoL, Unified: Optimizations and, later, Unified: Graphics. Each is
an independent mod in this monorepo ([architecture](docs/architecture/monorepo.md)).
Read this file before making any change.

## Roles

- **Product Owner (PO)** and **technical lead** make product and architecture
  decisions.
- **Implementation agents** document those decisions faithfully, identify gaps,
  implement approved specifications and validate the results.

Agents do not make product decisions. When a decision is missing, record it as
an open question and ask; do not fill the gap silently.

## Methodology: Spec-Driven Development

SDD is a working practice here, not a tool. Do not install an SDD framework,
generator or template engine unless the PO explicitly approves it.

1. **Specify.** Behavior is written in the owning mod's
   `docs/<mod>/specifications/` before code.
2. **Approve.** The PO or technical lead approves the spec (or parts of it).
3. **Implement.** Only approved requirements are implemented.
4. **Validate.** Every acceptance criterion is checked and reported.
5. **Update.** If implementation reveals a gap or a needed change, update the
   spec first and flag it for approval.

## Decision status labels

Every decision in the documentation carries one of these labels:

| Label | Meaning | Agent may implement? |
| --- | --- | --- |
| **[Approved]** | Decided by the PO or technical lead. | Yes |
| **[Proposed]** | Technical proposal awaiting approval. | No, unless the task explicitly approves it |
| **[Open]** | Question with no proposal or with alternatives. | No |
| **[Deferred]** | Intentionally postponed to a later milestone. | No |

Never relabel a proposal as approved yourself. Promotion to **[Approved]**
happens only when the PO or technical lead says so; note the date in the
commit message or the document.

## Documentation map

| Path | Content |
| --- | --- |
| `docs/architecture/monorepo.md` | Modules, independence rules, commands, CI |
| `docs/architecture/unified-settings-integration.md` | Shared settings across Unified mods (discovery, [Proposed]) |
| `docs/architecture/vanilla-world-compatibility.md` | Vanilla world compatibility contract (principle [Approved], rules and tests [Proposed]) |
| `docs/discovery/` | Project-wide discoveries: [Rust/native/AOT feasibility](docs/discovery/rust-native-aot-feasibility.md), [Unified: Graphics, Vulkan and Rust](docs/discovery/unified-graphics-rust-vulkan.md) ([Proposed]) |
| `docs/qa/pending-manual-tests.md` | Pending manual QA backlog for every module |
| `docs/development/versioning.md` | Per-module versioning, tags and changelog policy |
| `docs/development/linux-setup.md` | Linux / WSL development setup |
| `docs/development/cachyos-environment-audit.md` | CachyOS environment audit (2026-11-28) |
| `docs/qol/product/vision.md` | Unified: QoL goals, principles, milestones |
| `docs/qol/product/feature-catalog.md` | Unified: QoL features and identifiers |
| `docs/qol/architecture/` | Unified: QoL overview, configuration, diagnostics |
| `docs/qol/design/`, `docs/qol/discovery/` | UX designs and closed investigations |
| `docs/qol/specifications/` | One Unified: QoL spec per milestone or feature |
| `docs/optimizations/README.md` | Unified: Optimizations principles and catalog |
| `docs/optimizations/specifications/` | Unified: Optimizations specs |
| `mods/<mod>/CHANGELOG.md` | Each mod's user-relevant changes |

## Engineering rules

- **Client-side only.** No server component, no custom packets that require a
  server-side mod. Respect vanilla synchronization and server authority.
- **Player data safety first.** Never risk inventory, world or navigation data.
  Prefer doing nothing over doing something unsafe.
- **Vanilla world compatibility [Approved 2026-11-28].** Removing any Unified
  mod must leave every world loadable and intact in vanilla. No
  Unified-specific persistent world data, registry entries or save-format
  changes without explicit PO approval; native code never runs in save paths
  ([contract](docs/architecture/vanilla-world-compatibility.md)).
- **Keep it simple.** No dependency injection framework, event bus,
  reflection-based discovery or plugin loader without demonstrated need and
  approval. No speculative abstractions.
- **Feature isolation.** A feature never reaches into another feature's
  private implementation. Shared needs go through the Core.
- **Dependencies.** Do not add a library, Gradle plugin or mod dependency
  without approval.
- **Versions.** Never assume a Minecraft, Fabric, Loom, mapping or library
  version. Verify it from an official source and cite it in the change.
- **No copied code or assets.** Features inspired by other mods are
  reimplemented from behavior, not from their code, textures or translations.
- **Localization.** All user-facing text uses translation keys, with entries in
  both `en_us` and `pt_br`.
- **No license decisions.** Do not add or change a license file.
- **Horizon.** Do not write speculative Horizon (Rust) code before M5.
- **Native code.** No Rust, C or C++ in any production module and no native
  library loaded at run time without an approved specification and benchmark
  ([Rust feasibility](docs/discovery/rust-native-aot-feasibility.md), Q-RN1).
  QoL and Unified Settings never require a native toolchain.
- **Startup performance.** Keep startup cost minimal: no work in initializers
  beyond what is needed, no eager loading of feature classes or data, no
  Mixins unless the feature requires them. Do not add startup optimizations
  without production-install benchmarks.
- **Diagnostics.** Log feature failures with the feature id. No remote
  telemetry, embedded write tokens or automatic public issue creation (see
  [diagnostics](docs/qol/architecture/diagnostics.md)).

## Git workflow

- Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`,
  `build:`, `chore:`).
- Small, focused commits. Run `git diff --check` before committing.
- Do not create GitHub Issues, milestones or releases unless the task asks.
- Push only when the task says so and validation passed.
- One `main` branch holds every module; no permanent per-mod branches.
- Validate with targeted checks while working (the affected module's
  compilation, unit tests and focused game test, `git diff --check`), and run
  the complete game-test suite only for milestone closure or large
  integration changes.

## Versioning and changelog [Approved]

Full policy: [docs/development/versioning.md](docs/development/versioning.md).

- Versions are `MC_MAJOR.MC_MINOR.MOD_REVISION[-PRERELEASE]`, set per module
  in `mods/<mod>/gradle.properties`; changing one never touches another.
  Published versions are immutable and never reused. Tags are namespaced:
  `qol/v…`, `optimizations/v…`.
- Never create tags or releases unless the task explicitly asks.
- Record meaningful changes under `[Unreleased]` in the affected mod's
  changelog ([QoL](mods/qol/CHANGELOG.md),
  [Optimizations](mods/optimizations/CHANGELOG.md)), including breaking changes, configuration
  migrations and removals. Never invent versions or dates.

## Task completion

Stop at the boundary of the assigned task; do not start the next milestone.
When a feature needs checks only a person can run, add them to the
[manual QA backlog](docs/qa/pending-manual-tests.md) and follow its
conventions; never mark a manual case passed without the PO's report.
Finish with a report containing: starting and final commit hashes, files
changed, decisions documented or implemented, open questions, validation
results, and commit/push status.
