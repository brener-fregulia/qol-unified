# Feature Catalog: Unified: QoL

Status labels follow [AGENTS.md](../../../AGENTS.md#decision-status-labels).

The feature list and categories below are **[Approved]**. Feature identifiers,
milestone assignments and the restart notes are **[Proposed]** unless marked
otherwise. "Inspired by" names describe behavior only; no third-party code or
assets may be copied.

## Identifier rules [Approved 2026-11-21]

- Pattern: `^[a-z][a-z0-9_]{1,63}$` (lowercase, digits, underscores).
- Stable forever once released: identifiers key configuration and data files.
- Renaming a released identifier requires a configuration migration.
- Reserved for a possible future feature: `fast_startup` (not scheduled;
  [Approved 2026-11-21](../discovery/startup-performance-minecraft-26.3.md#decisions-2026-11-21)).

## Categories [Approved]

| Category | Proposed id |
| --- | --- |
| HUD & Information | `hud_information` |
| Inventory | `inventory` |
| Visuals | `visuals` |
| Controls | `controls` |
| Chat & Social | `chat_social` |
| Performance & Network | `performance_network` |
| Maps & Navigation | `maps_navigation` |
| Audio | `audio` [Approved 2026-11-23] |

## Features

Performance & Network features moved to
[Unified: Optimizations](../../optimizations/README.md) on 2026-11-26 with the
monorepo migration: `server_ping`, `fast_ip_ping` and `fast_world_exit`
(FastQuit). Their history and decisions are kept there. The
`performance_network` category stays in Unified: QoL's code for compatibility
and currently has no QoL feature.

| Feature | Category | Proposed id | Milestone | Inspiration / notes |
| --- | --- | --- | --- | --- |
| Food information | HUD & Information | `food_hud` | M1 [Approved: first feature after Core] | AppleSkin; spec: [Food HUD V1](../specifications/food-hud-v1.md) [Approved 2026-11-22]; implemented |
| Block and entity inspector (Target Info) | HUD & Information | `target_info` [Approved 2026-11-23; was `inspector`] | M1.4 [Approved 2026-11-23] | Jade, WTHIT; spec: [Target Info V1](../specifications/target-info-v1.md) [Approved 2026-11-23]; implemented |
| Enhanced statistics screen | HUD & Information | `enhanced_statistics` [Proposed] | [Open] | Search, sort, filters and totals over vanilla's own counters; spec: [Enhanced Statistics V1](../specifications/enhanced-statistics-v1.md) [Proposed]; not implemented |
| Inventory sorting | Inventory | `inventory_sort` | M2 [Proposed] | Must use vanilla click actions only |
| Inventory search (highlight matching slots) | Inventory | `inventory_search` [Approved 2026-11-28] | V1 [Approved 2026-11-28] | Visual only; spec: [Inventory Search V1](../specifications/inventory-search-v1.md); implemented 2026-11-28, manual QA pending (IS-01 to IS-08) |
| Item transfer | Inventory | `item_transfer` | M2 [Proposed] | Must use vanilla click actions only |
| Protected slots | Inventory | `protected_slots` | M2 [Proposed] | Must not lose items on failure |
| Shulker preview | Inventory | `shulker_preview` [Approved 2026-11-25] | M1.6 [Approved 2026-11-25] | Combined behavior of Shulker Box Tooltip and Simple Shulker Preview; spec: [Shulker Preview V1](../specifications/shulker-preview-v1.md) [Approved 2026-11-25]; implemented |
| Inventory Mouse Tweaks (drag and wheel transfers) | Inventory | `mouse_tweaks` [Approved 2026-11-26] | M1.9 [Approved 2026-11-26] | Mouse Tweaks; spec: [Inventory Mouse Tweaks V1](../specifications/mouse-tweaks-v1.md) [Approved 2026-11-26]. Vanilla clicks only; overlaps the planned `item_transfer` |
| Distinct potion identification | Visuals | `potion_identification` | [Open] | |
| Keybinding search | Controls | `keybind_search` | [Open] | |
| Keybinding conflict detection | Controls | `keybind_conflicts` | [Open] | |
| Visual keyboard and key-binding search | Controls | `keyboard_map` | M1.3 [Approved 2026-11-23] | Controlling, Keyboard Wizard CE, Keymap; spec: [Keymap V1](../specifications/keymap-v1.md) [Approved 2026-11-23]; implemented. Would cover `keybind_search` and `keybind_conflicts` (Q-KM10) |
| Camera zoom | Controls | `zoom` [Approved 2026-11-26] | M1.8 [Approved 2026-11-26] | Zoomify, Ok Zoomer; spec: [Zoom V1](../specifications/zoom-v1.md) [Approved 2026-11-26] |
| Player heads in chat | Chat & Social | `chat_heads` | M1.2 [Approved 2026-11-22] | Chat Heads; spec: [Chat Heads V1](../specifications/chat-heads-v1.md) [Approved 2026-11-22]; implemented. Uses vanilla's skin cache only; see Q-F3 |
| Minimap | Maps & Navigation | `minimap` | M4 [Approved] | |
| World map | Maps & Navigation | `world_map` | M4 [Approved] | |
| Waypoints | Maps & Navigation | `waypoints` | M4 [Approved] | |
| Persistent exploration data | Maps & Navigation | `exploration_data` | M4 [Approved] | Stored separately from configuration |
| Individual sound volume and mute (Sound Control) | Audio | `sound_control` [Approved 2026-11-23] | M1.5 [Approved 2026-11-23] | Extreme Sound Muffler, Sound Controller, Decibel; spec: [Sound Control V1](../specifications/sound-control-v1.md) [Approved 2026-11-23]; implemented. Event-level only: TNT and Creeper explosions share one sound event |

M3 and M4 assignments follow directly from the milestone names in the
[vision](vision.md#milestones-approved).

## Open questions

- **Q-F1 [Open]** Which features belong to M1 (Essentials) besides Food HUD,
  and which to M2?
- **Q-F2 [Open]** Is persistent exploration data a user-toggleable feature or
  a shared service owned by the map features?
- **Q-F3 [Open]** May chat heads fetch skins from the network, or only use
  skins the client already has? The Chat Heads V1 proposal uses only
  vanilla's own skin pipeline and cache (no extra service).
- **Q-F4 [Open]** Should inventory features be disabled by default on
  multiplayer servers (some servers forbid automated inventory actions)?
- **Q-F5 [Resolved 2026-11-23]** Audio category added for `sound_control`
  (Q-SC2 in the Sound Control V1 spec).
