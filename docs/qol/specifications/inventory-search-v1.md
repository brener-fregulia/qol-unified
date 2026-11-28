# Inventory Search V1

Status labels follow [AGENTS.md](../../../AGENTS.md#decision-status-labels).
**Implemented 2026-11-28** under the PO's approved scope. §15 records what
was built, how it differs from the proposal in §4–§9, and how it was tested;
§1–§14 are the original discovery. Manual QA is pending (IS-01 to IS-08). Findings are marked
**Verified** (Minecraft 26.3 decompiled source, the repository's code, or
measured), **Inferred**, **Proposed** or **Open**.

Search primitives: [Unified search architecture](../../discovery/unified-search-architecture.md).

## 1. Problem

Finding one item in a full double chest, or in the player inventory, means
hovering slot by slot. Vanilla offers search only in the creative inventory
and the recipe book.

Inventory Search adds a text field to container screens. It highlights
matching slots and dims the others. It is purely visual: it never moves,
hides or transfers anything.

## 2. Verified Minecraft 26.3 behavior

### 2.1 Screens and menus (Verified, `MenuScreens`, block entities)

| Container | Menu | Screen | Size (w×h) |
| --- | --- | --- | --- |
| Chest, trapped chest, barrel, Ender Chest, chest minecart, chest boat | `ChestMenu.threeRows` (`GENERIC_9x3`) | `ContainerScreen` | 176×168 |
| Double chest | `ChestMenu.sixRows` (`GENERIC_9x6`) | `ContainerScreen` | 176×222 |
| Other 9×N menus (often plugin GUIs) | `GENERIC_9x1` to `9x6` | `ContainerScreen` | 176×(114 + 18·rows) |
| Shulker Box | `SHULKER_BOX` | `ShulkerBoxScreen` | 176×167 |
| Hopper | `HOPPER` | `HopperScreen` | 176×133 |
| Dispenser, dropper | `GENERIC_3x3` | `DispenserScreen` (centered title) | 176×166 |
| Player inventory | `InventoryMenu` | `InventoryScreen` (recipe book, title at x 97) | 176×166 |
| Creative | — | `CreativeModeInventoryScreen` | Has its own search |

- **Position.** `leftPos = (width − imageWidth) / 2` and
  `topPos = (height − imageHeight) / 2`, in GUI-scaled pixels.
- **Title row.** The title is drawn at (8, 6). Slots start at y 18.

### 2.2 What the client knows (Verified)

- The open menu's `slots` hold the stacks that the server synchronized. These
  are current and client-visible, including data components such as names,
  enchantments and `container` contents.
- Nothing about unopened containers is known.
- Target Info's `ShulkerSnapshots` are "last seen" data and are explicitly not
  a source here (§8).

### 2.3 Input order (Verified, `AbstractContainerScreen`)

- **Mouse.** `mouseClicked` gives child widgets the click first
  (`super.mouseClicked`). A click on an `EditBox` therefore never reaches the
  slot or "clicked outside, drop item" logic.
- **Keyboard.** In `keyPressed`, children come first, then:
  - the **inventory key closes the screen**;
  - the hotbar number keys and offhand swap act on the hovered slot;
  - pick-item and drop (`Q`) act on the hovered slot.
- **Text fields.** `EditBox.keyPressed` consumes only editing keys
  (Backspace, arrows and so on). Printable characters arrive through
  `charTyped`.
  - So a focused field alone does **not** stop `E` from closing the screen,
    or `1`–`9` from swapping items.
  - The recipe book avoids this: when its `searchBox.capturesInput()` is true,
    it consumes every key except Escape (`RecipeBookComponent.keyPressed`).
    `T` (the chat key) focuses its search.
  - The creative screen uses the same `ignoreTextInput` pattern.

### 2.4 Rendering order (Verified)

`extractRenderState` draws, in order:

1. `extractContents`, which draws, in order:
   - the background;
   - the labels;
   - the hovered-slot highlight (back);
   - **`extractSlots`**, which draws every active slot;
   - the hovered-slot highlight (front).
2. The carried item, on a new stratum.
3. The tooltip.

Slots are drawn in a matrix translated by `leftPos` and `topPos`.

### 2.5 Vanilla search facilities (Verified)

- **The search trees.** `SessionSearchTrees` builds `FullTextSearchTree`s on a
  background executor for fixed sets: creative items by tooltip lines and id,
  creative items by tag, and recipes by result tooltips.
  - They are based on a `SuffixArray`, lowercase with `Locale.ROOT`, and do no
    accent folding.
  - Creative supports `#tag`. `namespace:path` intersects namespace and path.
- **Tooltip lines.** Tooltip lines for search come from
  `getTooltipLines(context, null, TooltipFlag.NORMAL)` with formatting
  stripped, the same rule vanilla uses for the recipe tree.
- **Why not reuse the trees for slots.** They suit large static sets built
  once. Container contents are small and change with every click, so a
  rebuilt suffix array would cost more than it saves (see the
  [architecture](../../discovery/unified-search-architecture.md) §3).

## 3. Existing components to reuse

| Need | Reuse | Status |
| --- | --- | --- |
| Screen hooks | Fabric `ScreenEvents.AFTER_INIT`, `Screens.getButtons`, `ScreenKeyboardEvents.allowKeyPress`, `ScreenEvents.afterTick` (already used by Mouse Tweaks) | Verified |
| Slot under the mouse | `ContainerScreenSlotsAccessor` (Mouse Tweaks) | Verified (existing) |
| Text field | Vanilla `EditBox` with the hint `gui.recipebook.search_hint` ("Search...") | Verified |
| Matching | The shared `unified.qol.search` core: `SearchQuery.matchesAll` over prepared `SearchableText` ([architecture](../../discovery/unified-search-architecture.md) §7) | Implemented 2026-11-28 |
| Container contents of item stacks | `ShulkerContents` (Shulker Preview): reads the stack's own `container` component, which is current, not a cache | Verified (existing); use is Q-IS4 |

## 4. Proposed behavior

- **Screens (V1).** `InventoryScreen`, `ContainerScreen` (every 9×N menu),
  `ShulkerBoxScreen` and `HopperScreen`. `DispenserScreen`, mount
  inventories, crafter and workstations are deferred (Q-IS2). Creative is
  excluded, because it has its own search.
- **Subclasses.** Mod screens that subclass these classes get the field only
  if their image size and title row match the vanilla class's (Q-IS3).
  Otherwise nothing is added. No universal modded support is promised.
- **The field.** A compact field. Typing filters live, on each change.
  - **Matching slots** draw normally, with a thin highlight frame.
  - **Other occupied slots** are dimmed with a translucent dark overlay.
  - **Empty slots** are dimmed too.
  - An empty query restores the normal look completely.
- **What a query matches (V1):**
  - the item's display name (`getHoverName`, so renamed items match their
    custom name);
  - the item id (`minecraft:diamond_pickaxe`), with `namespace:path` matched
    as in creative;
  - enchantment names from the `enchantments` and `stored_enchantments`
    components (enchanted books).
- **Matching rules.** Case and accents are ignored, and every word must match.
  The rules are the same as Keyboard Map's and the Settings search.
- **Match count.** "3 found" appears next to the field, so the player knows
  when matches are outside the visible area. The text is localized.
- **Scope of slots.** Every active slot in the menu is searched: the
  container's slots and the player's.
- **Lifetime.** The query lives only while the screen is open. It is cleared
  when the screen closes (Q-IS5).
- **Deferred:**
  - full tooltip text: lore, potion effects, and other mods' tooltip lines
    (Q-IS1);
  - `#tag` and `@mod` prefixes (Q-IS6);
  - matching inside a Shulker Box or bundle stack's contents (Q-IS4).

The feature never sends a packet, clicks a slot, reorders anything or changes
a tooltip.

## 5. UX design

### 5.1 Placement (Inferred from §2.1 sizes; to be confirmed in game, IS-T2)

GUI-scaled height at auto GUI scale, and the resulting `topPos`:

| Window | Scaled height | Double chest `topPos` | Inventory `topPos` |
| --- | --- | --- | --- |
| 1920×1080 | 270 | 24 | 52 |
| 1366×768 | 256 | 17 | 45 |
| 1280×720 or 2560×1440 | 240 | 9 | 37 |

A 12 px field plus a 2 px gap needs 14 px **above** the container. A double
chest does not have that at 720p or 1440p. The placement is therefore:

- **Chests, Shulker Box, hopper: in the title row, right-aligned.**
  - Position: x = `imageWidth − 8 − w`, y = 4, height 12. This stays above the
    first slot row at y 18.
  - Width: w = min(90, 160 − title width − 6).
  - Fallback 1: if w < 50 (a long custom or plugin title), the field moves
    above the container when `topPos` ≥ 14.
  - Fallback 2: otherwise it is not shown, and Ctrl+F still opens it as an
    overlay at the same place (Q-IS7).
- **Player inventory: above the container, right-aligned.**
  - Its title row holds the player model and armor, so there is no room
    there. `topPos` is at least 37 at every auto scale above.
  - It follows `leftPos` when the recipe book opens or closes, because the
    recipe book moves the container.
- **What it avoids:**
  - the container title;
  - the recipe book panel, which is to the left;
  - the recipe book button, which is inside the container;
  - status effects, which are drawn at the sides;
  - JEI's and REI's panels. Both are on 26.3 (Verified on Modrinth). Their
    search fields are at the bottom center and their item lists at the sides
    (Inferred; IS-07).
  - EMI has no 26.3 build (Verified).

```
┌──────────────────────────────────────┐
│ Large Chest          [diam      ] 3  │  ← title row: name left, field right
│ ▢▢▢▢▣▢▢▢▢                            │  ▣ match (normal + frame), ▢ dimmed
│ ...                                  │
```

### 5.2 Keyboard and accessibility

- **Focus.** Clicking the field focuses it. Ctrl+F focuses it from anywhere in
  the screen (Q-IS7). `T` is not used, because the recipe book uses it.
  - Vanilla's offhand-swap check (`keySwapOffhand.matches`) looks only at the
    key code, so an unconsumed Ctrl+F would also swap the hovered item.
  - The `allowKeyPress` hook therefore consumes Ctrl+F before vanilla sees it
    (Verified: `KeyMapping.matches` compares only the key code).
- **While focused:**
  - every key except Escape is consumed, the recipe book's rule (§2.3). `E`,
    `1`–`9`, `F` and `Q` type text instead of acting;
  - Escape unfocuses the field. A second Escape closes the screen as usual;
  - Enter unfocuses and keeps the query.
- **While not focused,** every vanilla key works unchanged.
- **Narration.** The field narrates its hint, its value and the match count. A
  slot's tooltip and narration stay vanilla's.

### 5.3 Dimming

- **How.** A 16×16 fill of `0xB0000000` (about 70 % black) over each
  non-matching active slot. It is drawn at the end of `extractSlots` (§2.4),
  so it sits under the hover highlight front, the carried item and the
  tooltip.
- **Matching slots** get a 1 px frame in a high-contrast color.
- **Accessibility.** Dimming is never the only signal; the frame and the count
  also show the result. Color choice is Q-IS8.

## 6. Architecture

- **`InventorySearchHooks`**, registered through `ScreenEvents.AFTER_INIT`
  like Mouse Tweaks:
  - adds the `EditBox` with `Screens.getButtons`;
  - registers `allowKeyPress`, to consume keys while the field is focused;
  - registers `afterTick`, to refresh the index and reposition the field;
  - registers `remove`, to clear state.
- **`SlotIndex`.** Per screen, one entry per slot: the folded search text and
  the last indexed item and components.
  - A slot is re-indexed at most once per client tick, and only when its stack
    is no longer `isSameItemSameComponents` with the indexed one.
  - Count changes cost nothing.
  - Matches are recomputed only when the query or an entry changes. They are
    stored as a `BitSet`.
- **Mixin IS-1.** It injects at the TAIL of
  `AbstractContainerScreen.extractSlots`. It only draws the overlay from the
  `BitSet`: no input handling and no allocation per frame.
- **Mouse Tweaks.** Its handlers act only when `slotAt` finds a slot. Clicks on
  the field are handled by the field first (§2.3), and Inventory Search never
  registers mouse handlers. The two features are therefore independent
  (Inferred; IS-T3 and IS-05 confirm it).
- **Code layout.** The package is `feature/inventorysearch/`. It has no
  dependency on Mouse Tweaks, Shulker Preview or Target Info.

## 7. Configuration

| Key | Type | Default | Notes |
| --- | --- | --- | --- |
| `enabled` | switch | **On** (Q-IS9) | Category **Inventory** (`inventory`). Live: it applies the next time a screen opens. |

There are no other settings in V1. The id `inventory_search` follows the
catalog's snake_case convention and is unused.

## 8. Compatibility and world safety

- **Visual only.** No packet, no slot click, no item movement and no change to
  server-side behavior. Removing the mod changes nothing in worlds or on
  servers ([contract](../../architecture/vanilla-world-compatibility.md)).
- **Nothing persisted.** No inventory is indexed persistently. The index lives
  only while the screen is open and holds only that screen's slots.
- **Shulker snapshots.** `ShulkerSnapshots` (Target Info) are never read.
- **Plugin GUIs.** Paper plugin GUIs use `ContainerScreen`. Highlighting their
  button items is harmless, because nothing is clicked.
- **Recipe viewers.** JEI, REI or any mod with its own inventory highlighting
  might overlap (Q-IS10). V1 checks no mod ids. Declared conflicts are added
  only after a verified clash.
- **Recipe book.** While the recipe book's search is focused, ours is not, and
  each field consumes keys only while it is focused.
- **Feature off.** No widget, no hook action and no overlay.

## 9. Performance

Measured with the disposable fixture (Ryzen 5 5625U, Java 25):

- indexing 90 entries of about 8 lines each, with accent folding: 0.16 ms;
- matching cached text: 2.5 µs for 90 entries and 0.47 ms for 10,000.

The cost of `getHoverName` and the enchantment lookup in game is not yet
measured (IS-T4).

| Criterion | Target |
| --- | --- |
| Per frame | Overlay only: ≤ 0.05 ms for 90 slots; zero allocations |
| Per query change | ≤ 0.2 ms for 90 slots, ≤ 2 ms for 1,000 (large modded containers) |
| Per tick, contents unchanged | Identity and component check only: ≤ 0.05 ms for 90 slots |
| Network and disk | None |
| Memory | One entry per slot of the open screen; released on close |

Full tooltip text (Q-IS1) would be built only when the stack changes, never
per frame. Its cost must be measured before it is approved.

## 10. Acceptance criteria

- **IS-AC1:** With an empty query, the screen draws exactly as vanilla.
- **IS-AC2:** The search never sends a serverbound packet or calls
  `slotClicked`.
- **IS-AC3:**
  - A slot is highlighted if and only if every query word is found in its
    name, id or enchantment names;
  - case and accents are ignored (pt_br included);
  - the count equals the number of highlighted slots.
- **IS-AC4:** While the field is focused:
  - `E`, `1`–`9`, `F` and `Q` type text and do not act on slots;
  - Escape unfocuses;
  - without focus, every vanilla key works.
- **IS-AC5:** These are unchanged:
  - clicks, Shift-click, Shift-double-click and drag-spread;
  - Mouse Tweaks gestures;
  - tooltips;
  - clicking the field while carrying an item (nothing is dropped).
- **IS-AC6:** The field never overlaps the title, any slot or the recipe book,
  at GUI scales 1–4 and at 1280×720, 1920×1080 and 2560×1440.
- **IS-AC7:** Contents changing while the screen is open (hopper input,
  another player) update the highlight within one tick.
- **IS-AC8:** With the feature off, no widget is added.

## 11. Test plan

- **Unit tests:** query parsing and matching over sample names and ids, and
  the index's re-indexing rule.
- **Focused game test (`runGameTestInventorysearch`), on a disposable world:**
  - open a chest and a double chest with known items, type queries, and assert
    the `BitSet`, the count and that no packet or click was sent;
  - press `E` while the field is focused and assert the screen stays open;
  - take screenshots at two GUI scales for placement (IS-T2);
  - run a Mouse Tweaks drag with an active query (IS-T3).
- **IS-T4:** measure the time to index 90 slots in game, with real items.
- **Manual QA:** IS-01 to IS-08 in the
  [QA backlog](../../qa/pending-manual-tests.md).

## 12. Risks

| # | Risk | Mitigation |
| --- | --- | --- |
| R1 | A focused field swallowing keys surprises players | Escape always unfocuses; focus is only gained by click or Ctrl+F |
| R2 | Overlap with mod GUIs that draw in the title row | Exact-size rule for subclasses (Q-IS3); field hidden when there is no room |
| R3 | Plugin GUIs with long titles | Width rule and fallback (§5.1) |
| R4 | Mods drawing slot overlays (other highlighting mods) | Visual only; reported in QA, conflicts declared after verification |
| R5 | Tooltip search (Q-IS1) could be slow with heavy-tooltip mods | Deferred; index only on change; measure first |

## 13. Open questions for the PO

- **Q-IS1 [Approved 2026-11-28: no tooltip text in V1]:** Search full tooltip text in V1? Proposed: no; name, id and
  enchantments first.
- **Q-IS2 [Open]:** Which further screens: dispenser and dropper, crafter,
  horse and llama, nautilus?
- **Q-IS3 [Open]:** Modded container screens: only exact vanilla layouts
  (proposed), or an opt-in "all container screens" mode?
- **Q-IS4 [Approved 2026-11-28: no contents in V1]:** Should a Shulker Box or bundle stack match when its
  contents match (from its own current component, never the Target Info
  cache)?
- **Q-IS5 [Approved 2026-11-28: the query resets when the screen closes]:** Remember the last query per screen type for the session?
- **Q-IS6 [Open]:** Prefix filters `#tag` and `@mod`, as in creative, JEI and
  REI?
- **Q-IS7 [Approved 2026-11-28: Ctrl+F; rebinding stays open]:** Ctrl+F to focus, and whether it should be rebindable?
- **Q-IS8 [Approved 2026-11-28: no visual settings in V1]:** Dimming strength and frame color, and whether they need an
  accessibility setting.
- **Q-IS9 [Approved 2026-11-28: on]:** Default on?
- **Q-IS10 [Open]:** (V1 leaves Ctrl+F to JEI, REI and EMI and keeps the field) Should the field be hidden when REI or JEI provides its
  own inventory highlighting?

## 14. Recommended MVP

- **Screens:** the player inventory, chests (every 9×N menu), the Shulker Box
  and the hopper.
- **Field:** one field, placed as in §5.1, that consumes keys while focused.
- **Matching:** display name, id and enchantment names.
- **Display:** a frame on matches, dimming on the rest, and a match count.
- **Excluded:** no settings beyond the switch, no packets, no clicks, no
  persistence, and no tooltip or contents search.

## 15. V1 implementation [Implemented 2026-11-28]

### 15.1 Approved scope (PO, 2026-11-28)

| Item | Decision |
| --- | --- |
| Identity | `inventory_search`, "Inventory Search" / "Busca no Inventário", category Inventory, **on by default** (Q-IS9), client-only, LIVE, one switch and no other settings |
| Screens | Player inventory, every 9×N chest menu (single and double chests, barrels, Ender Chests, chest minecarts and boats), Shulker Boxes, hoppers. Not: creative (own search), dispensers, crafters, workstations, mounts (Q-IS2 stays open). Modded subclasses of those classes with a 176 px wide container: best effort (Q-IS3) |
| Matches | Display name (custom name when renamed), the item's own localized name, id with namespace, enchantment and stored-enchantment names (only when the item's tooltip shows them). No tooltip text (Q-IS1), no contents of container items (Q-IS4), never Target Info's Shulker snapshots |
| Rules | The shared search core: case- and accent-insensitive, every word must match (AND), deterministic |
| Keys | Ctrl+F focuses (Q-IS7). It is left to JEI (`jei`), REI (`roughlyenoughitems`) or EMI (`emi`) when one is installed, since they bind Ctrl+F (Q-IS10); the field is still reachable with the mouse |

### 15.2 Behavior as built

- **Look.** With a query, matching occupied slots get a 1 px gold frame
  (`#FFD24A`) inside the 16×16 item area. Non-matching occupied slots get a
  55 % dark overlay (`#8C101010`). Empty slots, slot borders and slot
  positions are untouched. The item icons and counts stay recognizable.
  Tooltips, the carried item and the hover highlight draw on top.
- **Empty query.** Nothing is drawn, so the screen looks exactly like
  vanilla.
- **Match count.** Shown inside the field's right end, red when 0. The
  field's narration reads "Search items: N found".
- **Placement** (`SearchFieldLayout`, unit-tested):
  - **Chests, Shulker Boxes, hoppers:** in the title row, right-aligned
    (y 4–16, above the slot borders at y 17). Width: up to 88 px, and only
    the space after the title plus a 4 px gap. Never over the title or a
    slot.
  - **Fallback 1:** if fewer than 50 px remain (a long custom name), the
    field goes above the container when there are 14 px.
  - **Fallback 2:** otherwise a 12 px search icon sits at the row's right
    end. Ctrl+F or a click opens it into a field in the title row, which then
    covers the end of the title. That only happens while the player is
    searching, and the field closes again when focus leaves an empty field.
  - **Player inventory:** above the container, right-aligned. Its title row
    holds the player model. The field follows the container when the recipe
    book opens, so it never covers the book. With less than 14 px above
    (windows under about 194 px tall), it uses the icon.
  - **GUI scale has little effect.** Minecraft caps the GUI scale so the
    scaled screen is at least 320×240 (`Window.calculateScale`). The
    inventory therefore always has 37 px above it, and a double chest 9 px.
- **Draw order.** Vanilla draws container labels after its widgets, so the
  field and icon skip the screen's widget pass and are drawn at the end of
  `extractContents` instead (Mixin IS-2). This is after the labels and slots,
  but before the carried item and tooltip. It was found from game-test
  screenshots, where the expanded field had been written over by the title.
- **Focus and keys.** These run through Fabric's per-screen events only, so
  nothing global is hooked.
  - **While the field has focus:** every key press is consumed. `E`, `Q`,
    `F`, `1`–`9` and every other hotkey type text. Editing keys reach the
    field.
  - **Escape** or **Enter** unfocuses. A second Escape closes the screen as
    usual.
  - **A click anywhere outside the field** unfocuses it. The click itself
    stays a normal vanilla click.
  - **Without focus,** vanilla's keys are untouched. The only exception is
    Ctrl+F, which is consumed so vanilla's offhand swap (which ignores Ctrl)
    does not fire.
- **Lifetime.**
  - The state lives in the screen object (`InventorySearchHolder`, added by
    a Mixin), so there is no global map.
  - It survives a resize, and the query resets when the screen closes.
  - Turning the feature off hides the field and stops indexing and drawing
    on the next tick. Turning it on works without a restart.

### 15.3 Indexing (`SlotSearchIndex`, Kotlin, Minecraft-free)

- **Refresh.** Once per client tick, plus once when the screen opens and when
  the query changes.
- **What triggers re-preparing a slot.** A slot's prepared `SearchableText`
  is rebuilt only when its stack is no longer
  `ItemStack.isSameItemSameComponents` with the stored one-item copy. A
  count change costs nothing.
- **Language change.** It prepares every slot again.
- **Other invalidation.**
  - A new screen starts a new index.
  - A different number of slots grows or shrinks the index.
- **Matches.** They are a cached immutable `SlotMatches` (bit sets plus a
  count), recomputed only when the query or the index version changes.
  Drawing reads it without allocating.

### 15.4 Measured in game (Ryzen 5 5625U, Java 25, dev client)

These are from `runGameTestInventorysearch`, on a double chest plus the
player inventory (90 slots, all occupied, varied items):

| Operation | Time |
| --- | --- |
| Cold index of 90 slots (names, ids, enchantments) | median 0.25 ms (best 0.21 ms) |
| Per-tick refresh with nothing changed | 6–8 µs |
| A new query (fold + match all slots) | 12–18 µs |

There is no per-frame text work, no thread, no packet and no file.

### 15.5 Tests

- **Unit tests** (all pass):
  - `SlotSearchIndexTest` (7): preparation, names, ids and enchantments,
    the empty query, component vs count changes, slot updates and resizing,
    language invalidation, stored snapshots;
  - `SearchFieldLayoutTest` (5): every vanilla container height and the
    auto-scale heights, clear of slots and titles, long titles, the icon;
  - `InventorySearchSettingsTest` (2).
  - `DescriptorTranslationsTest` now expects 9 features in en_us and pt_br.
- **Game test** (`runGameTestInventorysearch`, passes). It covers:
  - **Player inventory:** Ctrl+F; name, id, enchantment and stored-enchantment
    matches; AND.
  - **Keys while focused:** `E`, `Q`, `F`, `1` and `5` type text and act on
    nothing, and the client and server menus agree. Backspace edits.
  - **Clicks:** a slot click unfocuses and is a normal pickup.
  - **Unfocused:** `1` swaps as vanilla. Escape unfocuses, then closes. The
    query resets on reopen, and unfocused `E` closes.
  - **Recipe book:** the field follows the container.
  - **Containers:** a single chest (a mouse click focuses), a double chest,
    a barrel, an Ender Chest, a Shulker Box and a hopper.
  - **With an active query:**
    - Mouse Tweaks right-drag, left-drag collect, Shift + drag and the wheel;
    - vanilla Shift + double-click;
    - each followed by a client/server comparison and an item-total check.
  - **Switching off:** while open it hides the field, and Ctrl+F then reaches
    vanilla's offhand swap. A new screen shows no field and `E` closes.
    Switching back on works without a restart.
  - **Narrow layouts:** a renamed double chest at the minimum 427×240 gets
    the icon, which opens into the field. The inventory in a 400×180 window
    gets the icon.
  - **1920×1080 at scale 3.**
  - **pt_br:** `espada diamante`, `afiacao` and `remendo`.
  - **Timings:** §15.4.
- **Mouse Tweaks regression.** `runGameTestMousetweaks` passes with Inventory
  Search on, and its unit tests pass.
- **Screenshots.** They were inspected in
  `mods/qol/build/run-gametest-inventorysearch/screenshots/`.

### 15.6 Known limitations

- **Screens.** The supported screens only. Dispensers, crafters, furnaces,
  workstations and mount inventories have no field (Q-IS2). The creative
  inventory keeps its own search.
- **Modded screens.** A modded screen that subclasses a supported class but
  draws its own layout in the title row could overlap (Q-IS3). Other modded
  containers get nothing.
- **What is searched.** No lore, potion effects or other tooltip lines
  (Q-IS1). No contents of Shulker Box or bundle items (Q-IS4).
- **Hidden enchantments.** Enchantments that an item hides from its tooltip
  are not searched.
- **Recipe viewers.** With JEI, REI or EMI installed, Ctrl+F belongs to them.
  Their layouts were not tested (IS-07).
- **Icon mode.** When the search icon is opened, the field covers the end of
  a long title while searching.
- **Visual settings.** There are none in V1, such as colors or dimming
  strength (Q-IS8).
