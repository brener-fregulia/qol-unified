# Unified: Optimizations

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).

Client-side performance and network optimizations for Minecraft Java 26.3
(Fabric), part of [Project Unified](../../README.md). Mod id
`unified_optimizations`, module `:mods:optimizations`, changelog
[mods/optimizations/CHANGELOG.md](../../mods/optimizations/CHANGELOG.md).

## Principles [Approved 2026-11-26]

- **Independent:** installs and runs without Unified: QoL or any other Unified
  mod, and shares no runtime library with them.
- **Client-only:** no server component, custom packets or protocol changes.
- **Verified problems only:** an optimization is specified only for a problem
  confirmed in the current Minecraft code, and no speed claim is made without
  a benchmark.

## Foundation (2026-11-26, extended in M2.2)

- `core/OptimizationFeature`: the implemented features (id, category,
  default, conflicting mod ids). Only implemented features are listed.
- `core/OptimizationsConfig`: `config/unified_optimizations/config.json`,
  `{"schemaVersion":1,"features":{"<id>":{"enabled":true}}}`. Only explicit
  choices are stored; nothing is written until the player changes a switch;
  unknown keys are preserved; invalid values fall back to the default; an
  unreadable file is moved to `config.json.invalid` before the next save; a
  newer schema is never rewritten; writes are atomic (temporary file, then
  atomic replace).
- `core/Optimizations`: lock-free enabled/active state (active = enabled and
  no conflicting mod), save-then-apply switches (a failed save changes
  nothing and is reported).
- Settings: `OptimizationsWorkspace` in the shared Unified Settings workspace
  (Network → Fast IP Ping, Performance → FastQuit), using the library's
  shared `FeatureSwitch` (Unified Settings 1.2.0).
- Mixins in Java (`unified_optimizations.mixins.json`), logic in Kotlin.
  Requires Fabric API's lifecycle events (already required by the nested
  library). Unified: QoL's Core is not copied.

## Catalog

Moved from the Unified: QoL catalog on 2026-11-26. Labels and history are
unchanged.

| Feature | Proposed id | Milestone | Status / notes |
| --- | --- | --- | --- |
| Fast IP Ping (no reverse DNS for server-list pings of non-public literal IPs) | `fast_ip_ping` | M2.2 | **Implemented 2026-11-26**, Network, default on. [Spec](specifications/fast-ip-ping-v1.md) §15. Open: Q-FIP8 (confirm scope), Q-FIP3 (public IPs), Q-FIP2 [Deferred], Q-FIP6 |
| FastQuit (return to the menu while the world saves) | `fast_quit` | M2.3 | **Implemented 2026-11-26**, Performance, default **off**. [Spec](specifications/fastquit-v1.md). Open: Q-FQ1 (default) |
| Server ping optimization | `server_ping` | M3 [Approved] | Likely restart-required. Relationship to `fast_ip_ping`: Q-FIP6 [Open] |

## Documents

- [Fast IP Ping V1](specifications/fast-ip-ping-v1.md)
- [Rust, native code and AOT feasibility](../discovery/rust-native-aot-feasibility.md)
  (discovery 2026-11-28: no migration proposed)
- [Vanilla world compatibility](../architecture/vanilla-world-compatibility.md)
- [Monorepo architecture](../architecture/monorepo.md)
- [Versioning](../development/versioning.md)
