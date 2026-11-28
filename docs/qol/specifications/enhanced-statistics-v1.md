# Enhanced Statistics V1 (discovery and specification)

Status labels follow [AGENTS.md](../../../AGENTS.md#decision-status-labels).
Every decision here is **[Proposed]** unless marked otherwise; nothing is
implemented. Findings carry one of: **Verified** (read in Minecraft 26.3's
decompiled source, `genSources`, or measured), **Inferred** (follows from
verified facts, not run in game), **Proposed**, **Open**.

Search primitives: [Unified search architecture](../../discovery/unified-search-architecture.md).

## 1. Problem

Vanilla's Statistics screen holds a lot of data but is hard to use:

- no search;
- no filters;
- an unstable item order;
- a General list that mixes 78 unrelated counters;
- no totals.

The goal is to make the **existing** counters easy to find, compare and read.
V1 adds no new data collection.

## 2. Verified Minecraft 26.3 behavior

### 2.1 Data model (Verified, `net.minecraft.stats`)

- A **statistic** is `Stat<T>`: a `StatType<T>` plus a registry value plus a
  `StatFormatter`.
- Statistic types are registered in `BuiltInRegistries.STAT_TYPE`. Custom
  counters are registered in `BuiltInRegistries.CUSTOM_STAT`.
- Values are `int`. `StatsCounter.increment` saturates at `Integer.MAX_VALUE`
  (2,147,483,647), so a counter stops growing rather than overflowing.
  - For distances (in cm) the cap is about 21,475 km per statistic.
  - For times (in ticks) the cap is about 3.4 years.
- Nine statistic types exist:

| Type id | Registry | Unit | Recorded by (Verified award sites) |
| --- | --- | --- | --- |
| `minecraft:mined` | blocks | count | `Block.playerDestroy` |
| `minecraft:crafted` | items | count | `ItemStack.onCraftedBy` |
| `minecraft:used` | items | count | item and block interactions (many sites) |
| `minecraft:broken` | items | count | `ServerPlayer` (tool broke) |
| `minecraft:picked_up` | items | count | `ItemEntity` pickup |
| `minecraft:dropped` | items | count | `ServerPlayer` drop |
| `minecraft:killed` | entity types | count | `Player` kill |
| `minecraft:killed_by` | entity types | count | `ServerPlayer` death |
| `minecraft:custom` | custom stats | per formatter | see §2.2 |

### 2.2 Custom ("General") statistics: 78 in 26.3 (Verified, `Stats.java`)

Every one of the 78 is awarded somewhere in game code (none is dead).

**Time, 5 statistics.** Stored in ticks; `TIME` formats them as s, min, h, d
or y.

| Id | Meaning |
| --- | --- |
| `play_time` | Ticks played. Not counted while singleplayer is paused. |
| `total_world_time` | Ticks with the world open. Also counted while paused (`IntegratedServer.tickPaused`). |
| `time_since_death` | Reset on death. |
| `time_since_rest` | Reset on death and by sleeping. |
| `sneak_time` | Time spent sneaking. The constant is named `CROUCH_TIME`. |

**Distance, 17 statistics.** Stored in cm; `DISTANCE` formats them as cm, m
or km.

| Group | Ids |
| --- | --- |
| On foot | `walk_one_cm`, `crouch_one_cm`, `sprint_one_cm`, `climb_one_cm` |
| In or on water | `swim_one_cm`, `walk_on_water_one_cm`, `walk_under_water_one_cm` |
| Flying | `fly_one_cm`, `aviate_one_cm` (elytra) |
| Falling | `fall_one_cm` |
| Riding | `minecart_one_cm`, `boat_one_cm`, `pig_one_cm`, `horse_one_cm`, `strider_one_cm`, `happy_ghast_one_cm`, `nautilus_one_cm` |

**Damage, 7 statistics.** Stored as tenths of a health point; `DIVIDE_BY_TEN`
shows two decimals:

- `damage_dealt`, `damage_dealt_absorbed`, `damage_dealt_resisted`
- `damage_taken`, `damage_blocked_by_shield`, `damage_absorbed`,
  `damage_resisted`

**Counts, 49 statistics.** Plain integers (`DEFAULT`).

| Group | Ids |
| --- | --- |
| Combat and life | `deaths`, `mob_kills`, `player_kills`, `animals_bred`, `fish_caught`, `jump`, `drop`, `leave_game`, `raid_trigger`, `raid_win`, `target_hit` |
| Rest | `sleep_in_bed`, `sleep_in_straw_bed` |
| Villagers | `talked_to_villager`, `traded_with_villager` |
| Opening containers | `open_chest`, `open_enderchest`, `open_shulker_box`, `open_barrel`, `trigger_trapped_chest`, `inspect_dropper`, `inspect_hopper`, `inspect_dispenser` |
| Workstations | `interact_with_crafting_table`, `_furnace`, `_blast_furnace`, `_smoker`, `_brewingstand`, `_beacon`, `_anvil`, `_grindstone`, `_smithing_table`, `_stonecutter`, `_loom`, `_cartography_table`, `_lectern`, `_campfire` (each `interact_with_…`), `enchant_item` |
| Cauldrons and cleaning | `fill_cauldron`, `use_cauldron`, `clean_armor`, `clean_banner`, `clean_shulker_box` |
| Blocks and music | `play_noteblock`, `tune_noteblock`, `play_record`, `pot_flower`, `bell_ring`, `eat_cake_slice` |

All ids are in the `minecraft:` namespace. Translation keys are
`stat.minecraft.<id>` (in en_us, `play_time` is "Time Played" and
`total_world_time` is "Time with World Open"; Verified in the client JAR).

### 2.3 Storage, ownership and synchronization (Verified)

- **Storage.** The server owns the data: `ServerStatsCounter`, one per player
  UUID, saved as JSON with a `DataVersion` in
  `<world>/players/stats/<uuid>.json` (`LevelResource.PLAYER_STATS_DIR`).
- **Scope.** Statistics are per world and per player: a singleplayer world, or
  a server's world.
- **Client copy.** The client keeps its own `StatsCounter`.
  - It is created on login (`ClientPacketListener`, `new StatsCounter()`).
  - It is kept across respawn and dimension change.
  - It is discarded on disconnect.
- **Request.** The Stats screen sends
  `ServerboundClientCommandPacket(REQUEST_STATS)` in `init()`. Nothing else in
  the client sends it.
- **Reply.** The server answers with `ClientboundAwardStatsPacket`, a map of
  statistic to value.
  - The reply holds only the statistics that changed since the last reply
    (`ServerStatsCounter.getDirty()`).
  - On join, `PlayerList` calls `markAllDirty()`, so the first request in a
    session returns every recorded statistic.
- **Client update.** `ClientPacketListener.handleAwardStats` writes each value
  into the client counter, then calls `StatsScreen.onStatsUpdated()` only if
  the open screen `instanceof StatsScreen`.
- **Missing entries.** A statistic never recorded is absent from the map and
  reads as 0. Zero entries exist only for counters that were reset
  (`time_since_death`, `time_since_rest`).
- **Client visibility.** Every recorded statistic of the local player is
  visible to the client after one request. Vanilla hides none. The client
  never sees other players' statistics.
- **Singleplayer.** It uses the same packets over the in-memory connection.
  Reading the integrated server's counter or the JSON file directly is not
  needed and is excluded (§8).

### 2.4 Paper (Inferred, not run)

Paper implements the same packets. Spigot's configuration can disable saving
statistics or force values (`stats.disable-saving`, `stats.forced-stats`), and
plugins can set statistics through the Bukkit API. The client shows whatever
the server sends, so these remain the server's numbers. The client cannot tell
them apart from vanilla values.

Whether Paper rate-limits `REQUEST_STATS` is unknown (Q-ES6). V1 sends no extra
requests.

### 2.5 The vanilla screen (Verified, `StatsScreen`)

- **Opening.** The Pause menu's Statistics button opens it. It shows "Retrieving
  statistics..." until the first reply, then builds three tabs once.
- **General tab.**
  - Every registered custom statistic, **zeros included**.
  - Sorted by translated name.
  - No sorting controls.
  - Values are read every frame, so they stay live.
- **Items tab.**
  - Lists items with any item statistic above 0, and blocks with `mined` above
    0 (as `block.asItem()`).
  - **Order:** unsorted, it follows an identity hash set, so the order looks
    random and is not stable.
  - **Sorting:** six column buttons sort descending, then ascending, then
    "off". In the "off" state the comparator returns 0, so the previous order
    stays. Ties are broken by numeric item id.
  - Non-block items show "-" in the mined column.
- **Mobs tab.**
  - Entity types with `killed` or `killed_by` above 0, in registry order.
  - The text is computed when the row is created, so it does not update while
    the screen is open.
- **Rows added later.** The lists are built once. Rows for things first
  recorded while the screen is open appear only after reopening it.
- **Empty tabs.** An empty Items or Mobs tab is disabled, with "No statistics
  found."
- **Formatting.**
  - `DEFAULT` uses `Locale.US` grouping (1,234) in every language.
  - Decimals use `Locale.ROOT`.
  - Unit suffixes (`cm`, `m`, `km`, `s`, `min`, `h`, `d`, `y`) are hard-coded
    English.
- **Navigation.** Tabs use `MenuTabBar`, so keyboard tab switching, focus and
  narration are vanilla's. The narration reads "name value".

### 2.6 Gaps found (Verified unless noted)

| # | Gap |
| --- | --- |
| G1 | No search or filter in any tab. |
| G2 | The Items tab's default order is arbitrary (identity hash). |
| G3 | The General tab interleaves time, distance, damage and counts, and shows zeros. |
| G4 | No totals. For example, there is no "blocks mined" total, although every per-block counter exists. |
| G5 | Mined counts for blocks whose item maps to another block are never shown. A wall torch's `mined` count is stored under `minecraft:wall_torch`, but its item is the torch (`StandingAndWallBlockItem.registerBlocks`), and the torch row reads the torch block's counter. Blocks with no item (`asItem()` is air) are dropped. The exact list of affected blocks is **Inferred** and must be enumerated in game (ES-T3). |
| G6 | Mob rows are static while the screen is open; no tab adds new rows. |
| G7 | Numbers and units are not localized (Locale.US, English suffixes). |
| G8 | Damage is shown as health points with two decimals, without a unit. |
| G9 | Statistic types added by mods are never shown. Only the nine vanilla types have UI. Mod items, blocks, entities and custom stats do appear automatically. |

### 2.7 Modded statistics (Verified)

- A mod's custom statistic (`CUSTOM_STAT`) appears in General under
  `stat.<namespace>.<path>`, with the mod's formatter.
- Mod items, blocks and entities appear in the Items and Mobs tabs.
- A new `StatType` has no UI in vanilla. Enhanced Statistics should list it as
  a generic "ID: value" table (V1.1, Q-ES5).

## 3. Existing APIs and components to reuse

| Need | Reuse | Status |
| --- | --- | --- |
| Data | `LocalPlayer.getStats()`, `Stats.*`, `StatType.contains/get`, `Stat.format`, registries | Verified, public |
| Refresh | Vanilla's own `REQUEST_STATS` sent by `StatsScreen.init()`, and `onStatsUpdated()` | Verified |
| Layout | `MenuTabBar`, `HeaderAndFooterLayout`, `ObjectSelectionList`, `ItemDisplayWidget`, `EditBox` | Verified, public |
| Text | Vanilla translation keys `stat.*`, `stat_type.*`, `gui.stats.*` | Verified |
| Search | The shared `unified.qol.search` core: `SearchQuery.matchesAll` over prepared `SearchableText` ([architecture](../../discovery/unified-search-architecture.md) §7) | Implemented 2026-11-28 |
| Settings | QoL `FeatureDescriptor`, Configuration Service, Unified Settings workspace | Verified (existing) |

## 4. Proposed behavior

When `enhanced_statistics` is on, opening Statistics shows an enhanced screen.
It has the same title, tabs, Done button and loading text as vanilla, plus the
controls in §5. When the feature is off, the vanilla screen opens unchanged.

V1 features, all computed from the client's counter:

1. **Search** (every tab). It matches the translated name and the id
   (`minecraft:stone`, `play_time`). It ignores case and accents, and every
   word must match. Mod names are matched through the namespace.
2. **Sorting.**
   - **Items:** by any column, descending or ascending, with a stable tie-break
     on name.
   - **General:** by name or by value. Values are compared only within one unit
     group (§4, item 3).
   - **Mobs:** by name, kills or deaths.
   - **Default:** the Items tab is sorted by name. This replaces the arbitrary
     order (G2).
3. **General groups.** A group filter: All, Time, Distance, Damage, Counts, and
   Other (mod statistics with unknown formatters). Group headers appear when
   All is selected.
4. **Hide zeros.** On by default for General. The Items and Mobs tabs already
   omit zeros.
5. **Live rows.** Rows are rebuilt when `onStatsUpdated()` arrives or the
   filter changes, so new items and mobs appear and mob values update (G6).
6. **Totals and highlights**, as a small summary at the top of General. Each is
   exact, from existing values (§4.1).
7. **Hidden mined blocks** (G5). Blocks with a `mined` count that the Items tab
   cannot show get their own rows, named after the block. Scope is Q-ES3.
8. **Formatting.** Same formatters as vanilla in V1, for parity. Localized
   number grouping and unit names are Q-ES2.

### 4.1 Derived metrics (only exact ones)

| Metric | Definition | Unit | Status |
| --- | --- | --- | --- |
| Total blocks mined | Σ `mined` over all blocks | count | Proposed |
| Total items crafted / used / picked up / dropped / broken | Σ over items, per type | count | Proposed |
| Total distance by group | Σ of the cm statistics of each group in §2.2, each group shown separately | distance | Proposed |
| Total distance travelled | Σ of all groups except falling | distance | Proposed (Q-ES4: include falling?) |
| Top 5 per column | The five highest rows of a column | as the column | Proposed |
| Average per hour played | value ÷ (`play_time` ÷ 72,000) | per hour | Proposed. Shown only once `play_time` is at least 1 hour, and labelled "lifetime average". |

- Sums are computed as `long`, because each source counter is a saturated
  `int`.
- If any counter in a sum is saturated, the total shows "≥" (at least).
- Units are never mixed. Time, distance, damage and counts are never added
  together.

**Rejected.** These metrics would need data that does not exist:

- trends, sessions or "this week";
- any rate based on `total_world_time`, because it counts paused time;
- kill/death ratios that mix players and mobs;
- per-session charts. They would need a separate collection mechanism, which
  is outside V1 (Q-ES7).

## 5. UX design

The layout follows vanilla's screen; only an extra row is added under the tab
bar:

```
[ General ][ Items ][ Mobs ]                       (vanilla MenuTabBar)
[ Search...                 ] [Group: All ▾] [Hide zeros ☑] [Sort: Name ▾]
| Summary: 12,345 blocks mined · 87.3 km travelled · …   (General only)
| ── Time ──
| Time Played                                      3.25 d
| ...
                         [ Done ]
```

- **Controls.**
  - The search box is a vanilla `EditBox` with the hint "Search...".
  - Group, hide zeros and sort are `CycleButton`s, which have vanilla narration
    and keyboard behavior.
  - On the Items tab, the column header buttons remain the sort controls,
    because they are vanilla's.
- **Layout.** Rows use vanilla's 280 px row width, alternating colors and
  narration ("name value"). The extra row is 24 px high. Below
  `HeaderAndFooterLayout`'s content height of about 120 px, the summary
  collapses to one line.
- **Keyboard.**
  - Tab order: tab bar, search, filters, list, Done.
  - Ctrl+F focuses the search box (Q-ES8).
  - Escape clears a non-empty search first, then closes the screen.
- **State.** The search text, group, sort and hide-zero choice live only while
  the screen is open. Persisting them is Q-ES1.
- **Not in V1:**
  - favorites and recently viewed, which would need a persistent list (Q-ES1);
  - inline bars and charts. Proportional bars in the distance summary are a
    V1.1 candidate.

## 6. Architecture

- **Screen.** `EnhancedStatsScreen extends StatsScreen`.
  - Because it is a subclass, vanilla's `instanceof StatsScreen` check still
    delivers `onStatsUpdated()`. Other mods that check for `StatsScreen` keep
    working.
  - It overrides `init()`, `onStatsUpdated()` and `repositionElements()`. It
    sends the same single `REQUEST_STATS` that vanilla's `init()` sends, and no
    other packet.
  - Its lists are its own `ObjectSelectionList` subclasses. Vanilla's private
    inner list classes are not mixed into.
- **Opening it.** One small Mixin (ES-1) at the head of `Gui.setScreen`
  replaces an instance whose class is exactly `StatsScreen` with
  `EnhancedStatsScreen`, keeping the same `lastScreen` and `stats`. This only
  happens when the feature is on and active. Subclasses from other mods are
  left alone. The alternative, a Mixin on `PauseScreen`'s button lambda, would
  be more fragile.
- **Model.** The model is free of Minecraft types and unit-testable:
  - `StatRow(id, name, group, value, formatted)`;
  - `StatsQuery`, which handles search, group, zero filter and sort, using the
    shared search primitives;
  - `StatTotals`, which computes the long sums and saturation flags.
- **Caching.** Translated names are folded once per screen build, because the
  language cannot change while the screen is open. About 1,200 item rows take
  about 1 ms to fold, measured (§9).
- **Code layout.** `feature/enhancedstats/`, with no dependency on other
  features.

## 7. Configuration

| Key | Type | Default | Notes |
| --- | --- | --- | --- |
| `enabled` | switch | **On** (Q-ES9) | Category **HUD & Information** (`hud_information`): it is information, and there is no better existing category. Live: it applies the next time Statistics opens. |

There are no other settings in V1; the screen controls are not settings.
Persisting view choices is Q-ES1.

The feature id is `enhanced_statistics`. It is snake_case like every QoL id
and does not collide with any id in the
[catalog](../product/feature-catalog.md).

## 8. Compatibility and world safety

- **Read-only.** The feature reads only the client's `StatsCounter` that
  vanilla filled. It never writes statistics, never sends a packet other than
  vanilla's request, and never reads `players/stats/*.json` or the integrated
  server's objects.
- **Removal.** Statistics, worlds and servers are unaffected
  ([contract](../../architecture/vanilla-world-compatibility.md)). With the
  feature off or uninstalled, vanilla's screen opens.
- **Servers.** No server component is needed. It works the same on vanilla,
  Paper and LAN servers.
- **Other mods.** Mods that replace or extend `StatsScreen` are untouched:
  - an exact-class check leaves their subclasses alone;
  - a mod that modifies vanilla's inner list classes would not affect the
    enhanced screen (risk R2).
- **Unified Settings.** The switch lives in the normal QoL workspace. No other
  Unified module is needed.

## 9. Performance

Measured with a disposable JVM fixture (Ryzen 5 5625U, Java 25). The cost of
folding is about 1.8 µs per entry of 8 lines. Matching cached text takes 0.1 ms
for 2,500 entries.

| Criterion | Target |
| --- | --- |
| Building the rows when the screen opens or stats update | ≤ 5 ms for 1,500 rows |
| One search or filter change | ≤ 1 ms for 1,500 rows; no work per frame |
| Rendering | Same order as vanilla: only visible rows are drawn |
| Network | Zero extra packets |
| Memory | Rows for one screen; released on close |

## 10. Acceptance criteria

- **ES-AC1:** With the feature off, the screen is vanilla's (class, tabs,
  order).
- **ES-AC2:** With the feature on, exactly one `REQUEST_STATS` is sent per
  opening, and no other serverbound packet.
- **ES-AC3:** Every value shown equals `StatsCounter.getValue` for its
  statistic, formatted by its own `StatFormatter`.
- **ES-AC4:** Search matches names and ids, ignoring case and accents, in
  en_us and pt_br. Every word must match.
- **ES-AC5:**
  - Sorting is deterministic, with ties broken by name.
  - The default order of the Items tab is by name.
  - Value sorting in General never orders across unit groups.
- **ES-AC6:** Hide zeros removes exactly the rows whose value is 0.
- **ES-AC7:** Totals equal the long sum of their sources. A saturated source
  shows "≥".
- **ES-AC8:** Rows and values update on `onStatsUpdated()` without reopening.
- **ES-AC9:** Keyboard alone can reach every control, narration reads each
  row, and the screen works at GUI scales 1–4 and on a 320×240 scaled window.
- **ES-AC10:** No file is written apart from QoL's own configuration.

## 11. Test plan

- **Unit tests** (`StatsQuery`, `StatTotals`):
  - search folding;
  - word AND;
  - group filters;
  - sort stability;
  - saturation;
  - hour average threshold.
- **Focused game test** (`runGameTestEnhancedstats`), on a disposable world:
  - award known statistics with `/` commands or by playing scripted actions;
  - open Statistics and assert the rows, totals, order and the single request
    packet;
  - with the feature off, assert the vanilla class.
- **ES-T3 experiment:** enumerate blocks whose `asItem()` is air or maps to
  another block, to size G5.
- **Manual QA:** ES-01 to ES-06 in the
  [QA backlog](../../qa/pending-manual-tests.md):
  - a Paper server;
  - long play time;
  - pt_br;
  - small window;
  - narrator;
  - a mod that adds statistics.

## 12. Risks

| # | Risk | Mitigation |
| --- | --- | --- |
| R1 | The `Gui.setScreen` Mixin conflicts with mods that also redirect screens | Exact-class check; inactive when a known conflicting mod is present |
| R2 | Vanilla's `StatsScreen` changes in a later version | The subclass overrides only public methods; the lists are our own |
| R3 | Derived metrics are misread as precise or current | Exact definitions, "lifetime" labels, and "≥" for saturated values |
| R4 | Server-modified statistics (Paper plugins) look wrong | Shown as received; nothing to fix on the client |

## 13. Open questions for the PO

- **Q-ES1 [Open]:** Persist view choices (sort, group, hide zeros) and add
  favorites? Both are client configuration, not world data. Proposed: no for
  V1.
- **Q-ES2 [Open]:** Localize number grouping and unit names, which differs
  from vanilla, or keep vanilla's formatting? Proposed: keep it in V1.
- **Q-ES3 [Open]:** Show the hidden mined-block rows (G5) in V1?
- **Q-ES4 [Open]:** Does "total distance" include falling? Proposed:
  excluded, shown separately.
- **Q-ES5 [Open]:** List mod statistic types (G9) in V1.1?
- **Q-ES6 [Open]:** A refresh button, which would send `REQUEST_STATS` again?
  Proposed: no; reopening the screen does the same thing.
- **Q-ES7 [Open]:** Ever collect per-session history? It would be a new,
  separate, opt-in local store, explicitly not vanilla statistics. Proposed:
  not planned.
- **Q-ES8 [Open]:** Ctrl+F to focus search (shared with Inventory Search)?
- **Q-ES9 [Open]:** Default on?

## 14. Recommended MVP

Build the enhanced subclass screen with these features:

- search;
- deterministic sorting in all three tabs (Items by name by default);
- General groups;
- hide zeros;
- live rows;
- the exact totals of §4.1, without the hour averages.

Formatting stays vanilla's. There are no new settings, no extra packets and no
persistence. The hour averages, hidden mined blocks, mod statistic types and
bars follow only after PO answers to Q-ES2 to Q-ES5.
