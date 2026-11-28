# Keymap V1 (M1.3)

Status labels follow [AGENTS.md](../../../AGENTS.md#decision-status-labels).

The technical lead approved Keymap V1 on 2026-11-23 with the corrections in
§0. Where §0 and a later section differ, §0 wins; superseded proposals are
marked. Implementation notes are in §17.

## 0. Approval and corrections [Approved 2026-11-23]

- **Identity:** feature id **`keyboard_map`** (not `keymap`; resolves Q-KM2), category Controls, enabled by default, LIVE, client-side only, no networking, no new key-binding persistence format.
- **Approved behavior:** physical keyboard view; US ANSI and ABNT2; transparent in-world background; scrollable side panel with names and categories; search and combined filters; unbound actions; mouse bindings; highlighting between keys and rows; contextual labels for the hovered or selected key only; **native rebinding, clearing and resetting** (resolves Q-KM6); keyboard navigation and responsive layout; en_us and pt_br.
- **Visual design:** the keyboard is the dominant central element. Gray = unassigned, green = one assignment, red = two or more, blue outline = selected or highlighted; counts and warning markers so color is never the only cue. **No amber in V1** (resolves Q-KM4). Right-hand panel with a searchable, scrollable list; toolbar with category, visibility filter and layout. Restrained vanilla-like styling, translucent panels, world visible.
- **Keyboard model:** SDL scancodes; physical scancode, OS label and drawn geometry are kept separate. Hand-authored `us_ansi` and `abnt2`, default `us_ansi` (resolves Q-KM3), switchable from the toolbar and persisted through the Core configuration. No claim of automatic detection. Bindings not on the drawn layout stay visible in the list.
- **Conflict states:** only **UNBOUND, SINGLE, OVERLAP**. Two or more assignments on one input is an overlap, without guessing whether the actions compete. All actions are listed with a neutral explanation. Modded `KeyMapping`s take part the same way. **No mapping from translation namespaces to mods** (resolves Q-KM5: no mod column). Mouse and keyboard identities are separate. This replaces the Shared/Conflict policy and the known-compatible list in §8.
- **Remapping:** select a binding → Change → the next supported keyboard or mouse input; Escape cancels; Clear unbinds; Reset restores the declared default. Then `KeyMapping.resetMapping()` and `Options.save()`. If the new input would create an overlap, the overlapping actions are shown and the player confirms or cancels. No change to input dispatch, no chords, profiles or custom listeners. Navigation, search and closing can never remap. Automated tests use only disposable options.
- **Entry points:** an "Open Keymap" key (unbound), a button in Options → Controls near Key Binds that overlaps nothing (Fabric screen events, no Mixin, checked with Controlling where feasible), and an action in the QoL: Unified settings. Disabled feature → no entry point opens it. Returning goes to the previous screen.
- **Configuration:** `layout` (`us_ansi`/`abnt2`, default `us_ansi`), `show_unbound_keys` (true), `contextual_labels` (true), `default_filter` (`all`/`overlaps`/`assigned`/`unbound`, default `all`), all LIVE, through the Core descriptors.
- **Separate:** the general QoL: Unified button in Minecraft's Options screen is approved as a future Core UI task (Q-KM9) and is **not** part of this work.
- **Still open:** Q-KM1 (comparison with the PO's screenshots), Q-KM7 (declared conflicts), Q-KM10 (catalog rows).

## 1. Product goals and non-goals

### 1.1 Goals (from the PO's brief) [Approved 2026-11-23]

| Id | Goal |
| --- | --- |
| **G-1** | Understand assigned key bindings spatially, on a picture of a physical keyboard. |
| **G-2** | Find unused keys and keys with more than one binding. |
| **G-3** | Search and filter bindings quickly. |
| **G-4** | Keep the Minecraft world visible behind the screen. |
| **G-5** | Support common physical layouts: US ANSI and Brazilian ABNT2 first. |
| **G-6** | Over time, offer a better configuration experience than vanilla's Key Binds screen. |

### 1.2 Feature identity [Approved 2026-11-23, id corrected]

| Id | Proposal |
| --- | --- |
| **K-1** | Feature id `keyboard_map`, category Controls, part of QoL: Unified. |
| **K-2** | Enabled by default, LIVE toggle. |
| **K-3** | Client-side only: no networking, no gameplay change. |
| **K-4** | No change to Minecraft's key-binding storage (`options.txt`); no new persistence for bindings. |

### 1.3 Non-goals for V1 [Approved 2026-11-23]

- Modifier combinations (Ctrl+key and similar): vanilla has none (F-5). Adding them would change how input is dispatched.
- Key-binding profiles or presets.
- A HUD overlay of pressed keys.
- Mod-specific hotkey systems that don't use vanilla `KeyMapping` (for example MaLiLib's).
- Controller or gamepad bindings.
- Replacing vanilla's Key Binds screen.

## 2. Sources

- **S-1** The unobfuscated Minecraft Java 26.3 client JAR used by this project, inspected with `javap`.
- **S-2** Fabric API 0.166.2+26.3 (`fabric-key-mapping-api-v1`, `fabric-screen-api-v1`, `fabric-lifecycle-events-v1`), already a dependency.
- **S-3** Reference mods for Fabric 26.3, downloaded from Modrinth on 2026-11-23 with their SHA-512 checked against Modrinth's. Only their metadata (`fabric.mod.json`), Mixin lists, resource file names and option texts (`en_us.json`), plus the Modrinth descriptions, were inspected. Their code, textures and translations were not read or copied.
- **S-4** The PO's three reference screenshots: **not available in this session.** No image was attached to the brief or reached this environment, so nothing here claims to have inspected them. §6 is a representative design built from the brief's written description. It must be compared with the screenshots before approval (Q-KM1).

## 3. Reference comparison (S-3)

| Mod | Version for 26.3 | Mod id | License | Verified capabilities | Notes |
| --- | --- | --- | --- | --- | --- |
| [Controlling](https://modrinth.com/mod/controlling) | 26.3.4 (2026-10-09) | `controlling` | MIT | Search box in vanilla Key Binds; "Show All / Show Conflicts / Show Unbound"; "Available Keys"; sorting A→Z, Z→A, by key; "Toggle Free"; confirmation for reset (option texts). | About 43 M downloads. Requires Searchables and Fabric API. Its Mixins are accessors on the key-binds list, `InputConstants.Key`, `KeyBindsScreen`, `KeyMapping` and `OptionsSubScreen`. It augments vanilla's screen rather than replacing it. No keyboard picture. |
| [Keyboard Wizard CE](https://modrinth.com/mod/keyboard-wizard-ce) | 1.1.1 (2026-11-17) | `keyboard_wizard_community_edition` | GPL-3.0-or-later | Visual keyboard; green = one binding, red = conflict, white = unbound; search and category filters; rebinding by clicking keys; clear, reset and reset-all; main, numpad and auxiliary layouts; filters for Ctrl, Shift, Alt and no modifier; opens with F7; one Mixin adds an entry to vanilla's Controls screen (description, option texts, Mixin list). | Only en_us and zh_cn. No layout selection by country is listed. |
| [Keymap [Maintained]](https://modrinth.com/mod/keymap-maintained) | 0.11.3 (2026-10-04) | `keymap` | ISC | Visual remapping; layout files for en_us, en_us_colemak, en_us_dvorak, da_dk, de_de, fr_fr, fr_fr_afnor, fr_fr_bepo, ja_jp, no_no; a layout selection screen; Ctrl/Shift/Alt modifiers implemented by the mod; opens with the grave key; Ctrl+F focuses search (description, file names). | **Its mod id is `keymap`, the same as the proposed feature id** (Q-KM2). No ABNT2 layout. The description says its modifiers don't block the modifier keys' own use (Ctrl+Q also sprints). |
| [KeyMap Viewer](https://modrinth.com/mod/keymap-viewer) | 1.0.2+26.3 (2026-10-02) | `keymapviewer` | All rights reserved | Keyboard view, conflict detection, a HUD of pressed keys, MaLiLib hotkeys (description). | Requires MaLiLib. Not studied further. |
| [Visual Keymap](https://modrinth.com/mod/visual-keymap) | 1.4.0 for **26.2 only** | — | GPL-3.0-or-later | Keyboard layout view; reachable from "Controls → Visual Keymap"; MaLiLib and CommandKeys integration (description). | Not available for 26.3. |

**Takeaways [Proposed]:**
- Search, "conflicts only" and "unbound only" are the features players already expect from Controlling.
- A keyboard picture with green, red and neutral key colors is established (Keyboard Wizard CE).
- An entry point in vanilla's Controls screen is common (Keyboard Wizard CE, Visual Keymap).
- Nobody verified here offers ABNT2.
- Every visual reference treats "more than one binding" as a conflict. This spec doesn't (§8).
- Modifier systems are done by replacing input handling, which V1 avoids.

**Unverified:** what Controlling's "Available Keys" and "Toggle Free" look like, how Keyboard Wizard CE draws its keyboard and lists modifiers, and how Keymap [Maintained] stores modifiers. These come from option texts and descriptions only; the mods were not run.

## 4. Minecraft 26.3 technical findings (S-1, S-2)

| Id | Finding | Evidence |
| --- | --- | --- |
| **F-1** | Minecraft 26.3 reads input through **SDL3** (LWJGL's `org.lwjgl.sdl`), not GLFW. | `com.mojang.blaze3d.platform.SDLEventHandler`, `InputConstants$Type` calls `SDLKeyboard` |
| **F-2** | Keyboard key values in `InputConstants.Key` are **SDL scancodes**, which are USB-HID physical key positions: `KEY_A = 4`, `KEY_Z = 29`, `KEY_ESCAPE = 41`, `KEY_GRAVE = 53`, `KEY_LSHIFT = 225`. The logical, layout-dependent key codes are separate (`KEYCODE_A = 97`, from `KeyEvent.keycode()`). **A bound key is therefore a physical position.** It is the same key on US and ABNT2 hardware, whatever the OS layout says. | `InputConstants` constants, `KeyEvent(key, keycode, modifiers)` |
| **F-3** | A key's **label** comes from the OS layout at runtime: `SDL_GetKeyFromScancode` then `SDL_GetKeyName`, with a few overrides in `InputQuirks.keyboardTranslationKey`. On a Brazilian layout, scancode 51 (US `;`) shows `Ç`. Labels can change if the player switches the OS layout while playing. | `InputConstants$Key` bytecode |
| **F-4** | Scancodes without a named constant still work. `InputConstants.Type.KEYBOARD.getOrCreate(int)` creates a key named `key.keyboard.<number>`. This covers the ABNT2 keys with no US counterpart: the extra key left of Z (HID `NonUS \|`, 100) and the `/?` key left of right Shift (HID International1, 135). | `InputConstants$Type.getOrCreate` |
| **F-5** | `KeyMapping` binds **one key, no modifiers**. Its fields are name, `defaultKey`, `Category`, `key` and order. Mouse bindings use `InputConstants.Type.MOUSE` (left = 1). | `KeyMapping`, `InputConstants$Type` |
| **F-6** | **Every** mapping bound to a pressed key receives it. `KeyMapping.click` and `set` go through `forAllKeyMappings(key, …)` over a `Map<Key, List<KeyMapping>>`. Duplicates are therefore not silently ignored: they all fire. Whether that is a real problem depends on context (§8). | `KeyMapping.MAP`, `forAllKeyMappings` |
| **F-7** | Vanilla's only conflict check is `KeyMapping.same(other)` (same key). `KeyBindsList$KeyEntry` marks such entries in red and lists the others. It doesn't look at context. | `KeyBindsList$KeyEntry` |
| **F-8** | Context is implicit in vanilla. Some mappings are read only in game (movement, attack), some by specific screens (`keyInventory`, hotbar keys inside containers), and F3 is itself a mapping (`keyDebugModifier`) used for hard-coded debug combinations in `KeyboardHandler`. Nothing in `KeyMapping` declares a context. | `Options`, `KeyboardHandler` |
| **F-9** | All mappings, vanilla and modded, are in `Options.keyMappings`. Fabric's `KeyMappingHelper.registerKeyMapping` adds modded mappings there. `KeyMapping.Category` has a translated `label()`; modded categories register their own. | `Options.keyMappings`, S-2 |
| **F-10** | Vanilla's own rebinding is public API: `KeyMapping.setKey(key)`, then `KeyMapping.resetMapping()`, then `Options.save()` (as `KeyBindsScreen.keyPressed` does). Bindings stay in `options.txt` through `saveString()`. | `KeyBindsScreen` bytecode |
| **F-11** | Screens opened in a world already draw the world behind them. `Screen.extractBackground` uses `extractTransparentBackground` in game UI, otherwise vanilla's blur (`extractBlurredBackground`, strength from Options → Menu Background Blur) and the translucent in-world menu background. A screen therefore gets a visible, blurred world with no new rendering code. | `Screen.extractBackground` |
| **F-12** | While a `Screen` is open, keyboard and mouse go to the screen; gameplay mappings aren't updated from input. Escape closes through `shouldCloseOnEsc` and `onClose`. | `Screen`, `KeyMapping.releaseAll` / `restoreToggleStatesOnScreenClosed` |
| **F-13** | Fabric's `ScreenEvents.AFTER_INIT` and `Screens.getWidgets` can add a widget to any screen without a Mixin. The settings key binding already uses Fabric's screen events. Vanilla's `ControlsScreen` lays out its contents in `addOptions()` (an options list plus "Key Binds…"). A button placed **inside** that list needs a Mixin; one placed outside it doesn't. | `ControlsScreen`, S-2, `SettingsKeyBinding` |
| **F-14** | Bindings change only when something calls `setKey` (vanilla screen, mods) or `options.txt` is loaded. There is no change event. | `KeyMapping` |

**Consequence:** physical positions are reliable (F-2), and layout is mostly
a matter of **geometry and labels**. US ANSI and ABNT2 differ in key shapes
and in a few extra keys, not in what scancode a position sends. The OS
supplies the labels (F-3).

## 5. Keyboard-layout strategy [Approved 2026-11-23]

- **KL-1 Model.** A layout is a static, hand-written list of keys: scancode, row, x, width, height, in quarter-key units. Two layouts ship with V1: **US ANSI** (104 keys) and **ABNT2** (107 keys). They are written from public HID tables and key geometry, not copied from any mod.
- **KL-2 Labels.** A key's label is vanilla's display name for its scancode (F-3), so it follows the OS layout. A short fixed label is used only where the OS name is too long for a keycap: Shift, Ctrl, Alt, Super, Enter, Backspace, Tab, Caps, the arrows, and the numpad (for example "Num 7").
- **KL-3 ABNT2 specifics.**
  - Scancode 51 is labeled by the OS (`Ç` on a Brazilian layout).
  - The extra key left of Z is scancode 100. The `/?` key left of right Shift is scancode 135. Both are drawn only on ABNT2 and are bindable through F-4.
  - The key left of Enter on the home row (`]` on ABNT2) is scancode 50 (HID `NonUS #`); ANSI's backslash (49) isn't on ABNT2.
  - ABNT2's Enter is drawn as a tall key, and left Shift is shorter.
  - On the ABNT2 numpad, the extra separator key is scancode 133 (keypad comma).
- **KL-4 Selection.** The player selects the layout; there is no automatic detection. SDL reports scancodes and OS key names, not the physical keyboard model, and USB doesn't report the printed layout reliably. Default: `us_ansi` (Q-KM3).
- **KL-5 Bindings not on the drawn layout.** A binding whose scancode isn't on the selected layout (for example 100 while US ANSI is selected, or F13–F24) is still listed, with the note "Not on this keyboard". It is never hidden.
- **KL-6 Layout change while playing.** Labels are read when the screen opens (§11). An OS layout switch shows on the next open. Geometry never changes on its own.
- **KL-7 Sections.**
  - The main block: Esc and function row, the alphanumeric block, the navigation cluster (Ins, Del, Home, End, PgUp, PgDn, arrows; PrtSc, ScrLk, Pause) and the numpad.
  - Mouse buttons are drawn as a separate row of chips: Left, Right, Middle, 4, 5, and any other button that a binding uses.
- **KL-8 Out of scope for V1:** ISO-UK, AZERTY, QWERTZ, JIS, Dvorak and Colemak drawings. Labels already follow the OS, so on these layouts US ANSI geometry shows the right labels; only the key shapes differ.

## 6. UI layout [Approved 2026-11-23, see §0 for colors]

Representative design. The PO's screenshots weren't available (S-4).

```
┌ Toolbar ────────────────────────────────────────────────────────────────┐
│ [Search…………………]  Category [All ▾]  Show [All ▾]  Layout [US ▾]   ⓘ     │
├ Keyboard (central) ───────────────────────────────┬ Bindings (side) ──────┤
│ Esc  F1 F2 F3 F4  F5 F6 F7 F8  F9 F10 F11 F12    │ Movement              │
│ `  1  2  3  4 …                     Bksp  Ins Hm │  Walk Forwards     W  │
│ Tab  Q [W] E  R …                          Del … │  Strafe Left       A  │
│ Caps  [A][S][D] …                 Enter          │ Gameplay              │
│ Shift  Z  X  C …                   Shift   ↑     │ ⚠ Drop Item   Q  2×   │
│ Ctrl Alt   [ Space ]    Alt Ctrl         ← ↓ →   │ …                     │
│ Mouse: (Left) (Right) (Middle) (4) (5)           │ Unbound               │
│  Legend: □ free  ■ 1 binding  ⚠ 2+ bindings      │  Spectator Outlines –  │
└──────────────────────────────────────────────────┴───────────────────────┘
```

- **UI-1 Keyboard.** Keys are drawn as flat rectangles with text, using vanilla GUI primitives and the font. No textures are needed.
- **UI-2 States, never by color alone:**

| State | Fill | Marker |
| --- | --- | --- |
| No binding | gray, low opacity | none |
| One binding | green | count dot `1` (optional, §12) |
| 2+ bindings, same context (§8) | red | `⚠` and count `2×` |
| 2+ bindings, context differs or unknown | amber | `≈` and count |
| Selected or highlighted | blue outline (2 px) | outline |

  Yellow/amber is the "context-dependent" state from the brief; whether to show it in V1 is Q-KM4. Colors are drawn from a fixed palette that stays distinguishable with deuteranopia and protanopia; red versus green is never the only cue (`⚠` and counts).
- **UI-3 Side panel.** A scrollable list, grouped by vanilla category (`KeyMapping.Category.label()`). Each row shows the action name, the key name (or "Unbound"), the conflict marker, and "Not on this keyboard" when KL-5 applies. Mod name per row is Q-KM5.
- **UI-4 Toolbar.**
  - Search box: one unified search, which also handles "key filter" and "name filter".
  - Category: All or one category.
  - Show: All, Conflicts, Assigned, Unbound.
  - Layout switcher.
  - Help button with the legend.
- **UI-5 Contextual labels.** Only for the hovered or selected key. A small panel next to the key lists its actions, with at most one short connector line to the panel. Only one key's labels are ever shown; no lines are drawn in the default view.
- **UI-6 Background.** Vanilla's in-world background (F-11): the world stays visible, blurred by the player's own Menu Background Blur setting, under the translucent in-world dimming. The keyboard and side panel sit on their own semi-opaque dark panels (about 70% black), so text stays readable on snow, in darkness, in rain and in bright skies. No dirt or panorama in a world. From the title screen, vanilla's panorama shows instead.

## 7. Interaction model [Approved 2026-11-23]

| Id | Behavior |
| --- | --- |
| **I-1** | Clicking a key selects it (blue outline). The list scrolls to and highlights its bindings, and its labels appear (UI-5). Clicking it again clears the selection. |
| **I-2** | Clicking a binding in the list selects it and outlines its key (or its mouse chip). An unbound action selects no key. |
| **I-3** | Hovering a key shows its labels without changing the selection. |
| **I-4** | Search and filters apply to the list and the keyboard together. Keys with no matching binding are dimmed, not hidden, so the keyboard shape stays stable. |
| **I-5** | Filters combine with AND: search ∧ category ∧ show-mode. |
| **I-6** | Unbound actions are always reachable: in "All" (in their category, marked "Unbound") and in "Unbound". |
| **I-7** | Search matches the translated action name, the category label and the key's display name, without regard to case or accents (so `ç` finds `Ç`). A query of the form `key:w` matches only key names. |
| **I-8** | While the screen is open, all keyboard and mouse input goes to it (F-12). Pressing a bound key doesn't trigger its action. Typing goes to the search box when it has focus. |
| **I-9** | Escape: if the search box has text, the first Escape clears it; otherwise Escape closes the screen and returns to where it was opened from. Ctrl+F focuses the search box. |
| **I-10** | Keyboard navigation: Tab and Shift+Tab move between toolbar, keyboard and list (vanilla focus navigation); arrow keys move the selection in the list; Enter on a list row selects its key. |

**Remapping.** Remapping **R-1 is approved (2026-11-23)** with the §0 rules:
- With a binding selected, a "Change" button waits for the next key or mouse press (Escape cancels). It applies through vanilla's own path (F-10): `setKey`, `resetMapping`, `Options.save()`. "Reset" restores `getDefaultKey()`.
- No new storage, no modifiers, no "reset all".
- It adds about one screen state and a few calls. It touches the player's real bindings, so it needs its own acceptance criteria (KM-AC-R1…R3) and approval (Q-KM6). An "Open vanilla Key Binds" button is offered either way.

## 8. Conflict-classification policy [Superseded by §0: UNBOUND / SINGLE / OVERLAP]

The smallest reliable model. It uses only what `KeyMapping` exposes (F-5,
F-6, F-8) and explains every verdict in the tooltip.

| State | Rule | Shown as |
| --- | --- | --- |
| **Unbound** | `isUnbound()` | listed as "Unbound"; key not involved |
| **Single** | exactly one mapping on the key | green |
| **Shared** | 2+ mappings on the key, and **every** pair is in the known-compatible list (CC-2) | green with `2×` and an "expected overlap" tooltip |
| **Conflict** | 2+ mappings on the key and at least one pair is not known-compatible | red, `⚠`, count; tooltip lists the actions and says "All of these run when you press this key" (F-6) |

- **CC-1 Wording.** "Conflict" means vanilla will fire every listed action (F-6). It doesn't claim the result is harmful; the tooltip says what happens.
- **CC-2 Known-compatible pairs.** A short, reviewed list of vanilla pairs whose contexts don't overlap. The candidates must each be verified in game before they are listed, for example: hotbar keys and spectator-menu keys, and `keyDebugModifier` (F3) with keys only used in game. Modded pairs are never assumed compatible.
- **CC-3 Mouse.** Mouse buttons are compared with mouse buttons only. A keyboard key and a mouse button never conflict.
- **CC-4 Modifiers.** None in V1 (F-5). A mod that implements its own modifiers outside `KeyMapping` isn't visible. Its overlaps are not judged.
- **CC-5 Modded bindings** are treated exactly like vanilla ones (F-9).
- **CC-6 "Unknown or context-dependent overlap"** (the amber state in UI-2) is Q-KM4. The recommendation is to **leave it out of V1** and use only Single, Shared and Conflict, because `KeyMapping` declares no context (F-8) and any guess would be unexplainable.

## 9. Core integration [Approved 2026-11-23]

- **CI-1 Registry and lifecycle.** `keymap` is registered in `Features` like Food HUD and Chat Heads. Enabling installs its entry points once (an idempotent hooks pattern, as in the existing features). Disabling hides the entry points and closes nothing in use; an open Keymap screen stays usable until closed. LIVE, cannot fail.
- **CI-2 Configuration.** Through the Core setting descriptors only (§10). No new setting types.
- **CI-3 Bindings.** Read from `Options.keyMappings` (F-9). V1 writes nothing to bindings unless R-1 is approved.
- **CI-4 Localization.** All new text uses translation keys in `en_us` and `pt_br`. Action, category and key names come from vanilla, mods and the OS, untranslated by Keymap.
- **CI-5 Compatibility detection.** Candidates for the declared-conflict list (Q-KM7):
  - **Not a conflict:** Controlling, which changes vanilla's Key Binds screen rather than adding a second keyboard view.
  - **Overlap, not a functional clash:** Keyboard Wizard CE and Keymap [Maintained], which both add a keyboard screen. Keymap [Maintained] also changes input dispatch for its modifiers, which makes its bindings partly invisible to `KeyMapping` (CC-4).
  - **Recommendation:** declare no conflict and add a QA note. Q-KM7 decides.
- **CI-6 No Mixins.** Entry points use Fabric screen events (F-13) and the existing settings screen. The Keymap screen is an ordinary `Screen`. No access widener.

### 9.1 Entry points [Approved 2026-11-23]

| Id | Entry point | Recommendation |
| --- | --- | --- |
| **EP-1** | Key binding "Open Keymap", **unbound by default** (like the settings key; vanilla and many mods already use most free keys) | Yes |
| **EP-2** | Button in Options → Controls. Placed outside the options list (F-13): next to the "Done" footer button, or a small button in the header's right corner, to avoid a Mixin and overlap. The final position must be checked with Controlling installed. | Yes, Q-KM8 |
| **EP-3** | "Open Keymap" action on the feature's row in the QoL: Unified settings screen | Yes |

### 9.2 QoL: Unified button in Minecraft's Options screen [Approved 2026-11-23 as separate future Core UI work; not part of Keymap]

Not part of Keymap. Proposed for later approval (Q-KM9).
- Vanilla's `OptionsScreen` uses a two-column `GridLayout` with no free slot. Adding a full-width row would push or overlap other mods' widgets.
- **Recommendation:** a small (20×20) icon button with a tooltip, added through `ScreenEvents.AFTER_INIT`. It goes in the header beside the title, at the right edge, where vanilla draws nothing. Mods that commonly add buttons there (for example Mod Menu's "Mods" button, which goes in the grid) must be checked in QA.
- No change to Options in this task.

## 10. Configuration [Approved 2026-11-23]

| Key | Type | Default | Apply |
| --- | --- | --- | --- |
| (feature toggle) | — | enabled | LIVE |
| `layout` | ENUM `us_ansi` / `abnt2` | `us_ansi` (Q-KM3) | LIVE |
| `show_unbound_keys` | BOOLEAN | `true`: free keys drawn in gray; `false`: dimmed further | LIVE |
| `contextual_labels` | BOOLEAN | `true` | LIVE |
| `default_filter` | ENUM `all` / `overlaps` / `assigned` / `unbound` | `all` | LIVE (next open) |

All of these are existing Core setting types (BOOLEAN, ENUM). The layout
switcher in the toolbar writes the same `layout` setting through the Core,
so it persists like any other setting.

## 11. Performance [Approved 2026-11-23]

- **P-1 Refresh on open.** The binding catalog (mappings, keys, labels, states) is built when the screen opens, plus after a remap (R-1) or a layout change. It is never rebuilt per frame. With a few hundred mappings this is a single pass.
- **P-2 While closed:** no work at all, besides the entry points' existing event listeners (one key-binding check per tick for EP-1, which the settings key already does).
- **P-3 Drawing** costs one rectangle and one label per key (about 110) plus visible list rows.
- **P-4 No change detection.** Bindings can only change outside the screen (F-14), so refresh-on-open is enough for V1.
- **P-5 No network.**

## 12. Accessibility and responsiveness [Approved 2026-11-23]

- **A-1 Minimum size.** Keys are at least 12 GUI pixels high with a readable label. If the keyboard doesn't fit at that size, it doesn't shrink further:
  - First the numpad and navigation cluster move to a "More keys" section, which can be toggled (vanilla's main, numpad and auxiliary split is a known pattern; S-3).
  - Then the side panel becomes a tab, switching between the keyboard and the list.
- **A-2 854×480 at GUI scale 2** (427×240 GUI pixels): main block plus the list as a tab; numpad and navigation through "More keys". At GUI scales 3–4 in a small window, vanilla lowers the scale automatically.
- **A-3 Ultrawide.** The keyboard is centered with a maximum width. The extra space goes to the side panel, up to a maximum, so the layout doesn't stretch.
- **A-4 Long labels.** pt_br strings are longer. Toolbar labels are cut with "…" and a tooltip; key labels never wrap.
- **A-5 Color vision.** See UI-2: markers and counts on every state, outlines for selection.
- **A-6 Focus.** The search box isn't focused on open, so Escape closes immediately (I-9). Ctrl+F focuses it.

## 13. Acceptance criteria [Approved 2026-11-23; "Conflict" reads OVERLAP, Shared is dropped]

| Id | Criterion |
| --- | --- |
| **KM-AC-1** | Every mapping in `Options.keyMappings` appears exactly once in the list, with its translated name, category and key name (unit test on the catalog, plus a game test). |
| **KM-AC-2** | Each drawn key's state (none, single, shared, conflict) matches the mappings bound to that scancode (unit test). |
| **KM-AC-3** | US ANSI shows 104 keys. ABNT2 shows 107, including scancodes 100 and 135 and the keypad comma, with labels from the OS. The geometry passes a no-overlap check (unit test). |
| **KM-AC-4** | Clicking a key highlights all its bindings in the list; selecting a binding outlines its key or mouse chip. |
| **KM-AC-5** | Search, category and show-mode combine (AND). The keyboard dims keys with no matching binding and the list shows only matches (unit test on the filter). |
| **KM-AC-6** | Unbound actions appear in "All" and "Unbound" and can be found by search. |
| **KM-AC-7** | Two mappings on one key (not on the known-compatible list) show as Conflict with `⚠` and a count. A known-compatible pair shows as Shared. A single binding is never marked. |
| **KM-AC-8** | Mouse bindings appear as mouse chips and never conflict with keyboard keys. |
| **KM-AC-9** | A binding registered by a Fabric mod (a dev-fixture mapping) appears in its category and takes part in conflict detection. |
| **KM-AC-10** | A binding on a scancode not on the selected layout is listed with "Not on this keyboard". |
| **KM-AC-11** | In a world, the world is visible behind the screen, with vanilla blur; text contrast stays readable in daylight, at night, in rain and on snow (screenshots). |
| **KM-AC-12** | While the screen is open, pressing bound keys (W, Space, Q, number keys) causes no player movement, item drop or slot change (game test). Escape closes it. |
| **KM-AC-13** | At GUI scales 1–4 and at 854×480, no key is smaller than 12 GUI pixels high, nothing overlaps, and every key is reachable (directly, through "More keys" or by scrolling). |
| **KM-AC-14** | `layout` and the other settings persist across restarts. |
| **KM-AC-15** | Turning the feature off removes the entry points without a restart; turning it on restores them. |
| **KM-AC-16** | All new text exists in `en_us` and `pt_br` (`LocalizationTest`). |
| **KM-AC-17** | Food HUD and Chat Heads game tests still pass. Vanilla's Key Binds screen behaves the same with Keymap on and off. |
| **KM-AC-18** | On the PO's authenticated Paper server: opens, shows bindings, sends no packets, never disconnects. |
| **KM-AC-19** | Opening the screen does one catalog build; nothing in Keymap runs per frame while it is closed (instrumented game test). |
| **KM-AC-R1** | (R-1 only) Remapping through Keymap writes the same `options.txt` value as vanilla's screen would. |
| **KM-AC-R2** | (R-1 only) Escape cancels a pending remap without changes. |
| **KM-AC-R3** | (R-1 only) Reset restores the mapping's default key. |

## 14. Manual QA checklist [Approved 2026-11-23]

Use the PO's Prism testing instance and Paper server for item 9. Use
disposable game tests for everything else. Never change server
configuration or authentication.

1. Open Keymap from each entry point (key, Controls, QoL: Unified settings).
2. With a US keyboard: check that the labels match the physical keys.
3. With an ABNT2 keyboard and the Brazilian layout: `Ç`, the key left of Z, the `/?` key and the numpad comma show, are labeled correctly, and can be selected.
4. Bind two actions to one key in vanilla's screen. Keymap shows Conflict and lists both.
5. Bind an action to Mouse 4. It shows on the mouse chip.
6. Search for an action, then for `key:q`, then combine with "Conflicts".
7. In a world, open Keymap in daylight, at night, in rain and on snow. The world is visible and the text readable.
8. Press W, Q and 1–9 with the screen open. Nothing happens in game.
9. On the Paper server: open Keymap, check the bindings, close it.
10. Try GUI scales 1–4, an 854×480 window and an ultrawide window.
11. Switch to Português (Brasil).
12. Check that Food HUD, Chat Heads and vanilla Key Binds still work, with and without Controlling installed.

## 15. Technical risks

- **TR-1** SDL key names for unusual scancodes may be empty or generic on some OSes. Fixed fallback labels (KL-2) cover this.
- **TR-2** The geometry of two layouts is hand-written. A mistake shows a key in the wrong place, but the binding data is always right, because bindings are scancodes (F-2). Mitigated by the no-overlap and scancode-coverage tests (KM-AC-3).
- **TR-3** The known-compatible list (CC-2) can be wrong for unusual play. Each entry must be verified, and the tooltip still lists every action.
- **TR-4** Mods that dispatch input outside `KeyMapping` (own modifiers, MaLiLib hotkeys) are invisible. Documented as a limitation (CC-4).
- **TR-5** The Controls-screen button (EP-2) can collide with other mods that also add buttons there. QA with Controlling and Keyboard Wizard CE.
- **TR-6** The `keymap` feature id equals the Keymap [Maintained] mod id (Q-KM2). Feature ids and mod ids are separate namespaces in the Core, so there is no technical clash, but it may confuse logs and support.

## 16. Open decisions

- **Q-KM1 [Open]** Compare §6 with the PO's three screenshots, which were not available here, and adjust the layout.
- **Q-KM2 [Approved 2026-11-23]** Id `keyboard_map`.
- **Q-KM3 [Approved 2026-11-23]** Default `us_ansi`, switchable from the toolbar.
- **Q-KM4 [Approved 2026-11-23]** No amber in V1.
- **Q-KM5 [Approved 2026-11-23]** No mod column; no namespace-to-mod mapping.
- **Q-KM6 [Approved 2026-11-23]** Native rebinding, clearing and resetting in V1.
- **Q-KM7 [Open]** Declare conflicts with Keyboard Wizard CE and Keymap [Maintained]? The recommendation is no, QA note only.
- **Q-KM8 [Approved 2026-11-23]** Near vanilla's Key Binds entry, overlapping nothing; exact spot recorded in §17.
- **Q-KM9 [Approved 2026-11-23]** As a separate future Core UI task, not in Keymap.
- **Q-KM10 [Open]** The catalog lists `keybind_search` and `keybind_conflicts` as separate [Open] features. Fold them into `keymap`? Keymap covers both.
- **Q-KM11 [Deferred]** More layouts (ISO-UK, AZERTY, QWERTZ, JIS, Dvorak, Colemak), modifier combinations and binding profiles.

## 16a. UX refinement M1.3.1 [Approved and implemented 2026-11-23]

The presentation is redesigned by [Keyboard Map UX refinement](../design/keyboard-map-ux-v1.md)
(Concept A). The binding model, states, scancodes, remapping semantics,
settings, entry points and persistence in §0 are unchanged. Where §6, §7
and §17.3 describe the V1 screen layout, the design document now governs;
its §13 records the implementation.

## 17. Implementation notes [Implemented 2026-11-23]

Implementation of §0. Nothing here changes an approved decision; §17.4 lists
findings for the PO.

### 17.1 Code (`feature/keyboardmap`)

- **Pure model, unit-tested:** `InputId` (keyboard scancode or mouse button, separate identities), `KeyCap` and `KeyboardLayout` (geometry only), `KeyboardLayouts` (`us_ansi` 104 keys, `abnt2` 107 keys, plus short fallback names), `Binding`, `BindingCatalog` (UNBOUND/SINGLE/OVERLAP), `BindingFilter` (search, category, visibility; AND; folding from the shared [search core](../../discovery/unified-search-architecture.md#7-implementation-search-core-v1) since 2026-11-28 [Approved, Q-SA3], with each binding's text prepared once per `BindingCatalog` snapshot and the query once per filter; the query stays a phrase within one field), `Selection`, `RemapSession` (capture, confirm, cancel, clear, reset) and `KeyboardMapGeometry` (responsive arrangement).
- **Minecraft side:** `MinecraftBindings` reads `Options.keyMappings` once per open or change and writes only through `KeyMapping.setKey`, `KeyMapping.resetMapping()` and `Options.save()`. `KeyboardMapScreen` is an ordinary `Screen`. `KeyboardMapHooks` holds the entry points.
- **Core UI:** `ui/FeatureActions`, an internal registry so a feature can offer a button on its settings page; shown only while the feature is active. `SettingsKeyBinding.category()` shares the "QoL: Unified" key category.
- **No Mixin, no access widener, no new dependency.**

### 17.2 Entry points

- **EP-1** "Open Keyboard Map", unbound. Fabric accepts key mappings only at client start, so it is registered then (`QolUnifiedClient`), even if the feature starts disabled; while disabled it opens nothing.
- **EP-2** A "Keyboard Map…" row added to vanilla's own Controls options list (`ScreenEvents.AFTER_INIT`, `OptionsList.addSmall`). It sits under Mouse Settings / Key Binds and the vanilla options, overlaps nothing and scrolls with the list. Verified without and with Controlling 26.3.4 (screenshots). Not added while the feature is disabled.
- **EP-3** "Keyboard Map…" on the feature's page in the QoL: Unified settings. From there the layout switcher writes into the settings draft (saved with Done), so Done can't overwrite it with a stale value. From EP-1 and EP-2 it saves through the Core at once.
- All return to the screen they were opened from (Escape or Done).

### 17.3 Behavior as built

- **States:** gray (no binding), green (one), red with a `!N` marker and count (N ≥ 2). Blue double outline for the selected or highlighted input, white outline on hover. No amber.
- **List:** grouped by vanilla category. Each row shows the action and its key. An overlap is prefixed `!` and drawn in red; an unbound action reads "Not bound"; a key not on the drawn layout gets `*` and a tooltip.
- **Labels:** for the pointed key, next to it; for the selected key, in the free space under the keyboard when there is room. The box lists up to 6 actions and, for an overlap, the neutral note "N actions use this input; Minecraft runs all of them when it is pressed", wrapped, with one connector line.
- **Search and filters:** search dims keys with no matching action and filters the list. Escape first clears a non-empty search; Ctrl+F focuses search; Up and Down move through the list.
- **Remapping:**
  - Change waits for one key or mouse button.
  - Escape cancels and does not close the screen.
  - A new overlap shows "X is also used by: …. Use it anyway?" with "Use anyway" and "Cancel"; nothing is written before that.
  - Clear and Reset act only when they change something.
  - Searching, clicking keys or rows, and closing never write.
- **Keycap labels:** OS name when it fits, else the short fallback (Esc, Ins, PgUp, Ctrl…), shrunk to at least half size before being cut.
- **Mouse chips:** buttons 1–5 plus any other bound button, by name, or by number when the names don't fit.
- **Responsive:**
  - The full keyboard beside the list when keys can be at least 16 px (4 px per quarter key).
  - Otherwise "More keys" splits it: main block, then navigation and numpad.
  - Keys are never under 12 px.
  - Very narrow windows switch keyboard and list as tabs.
  - On ultrawide screens, keys stop growing at 36 px.

### 17.4 Findings

- **Vanilla 26.3 overlaps.** The F3 debug combinations are ordinary key mappings in 26.3 (category "Debug"), bound to A, C, D, G, L, P, T, X, 1–4, F3 and F4 among others. Under the approved policy these keys show as OVERLAP on a fresh install. This is accurate (vanilla runs every mapping on a key; F-6), and the labels say so neutrally. Controlling 26.3.4 also marks them (screenshot).
- **ABNT2 `]` key.** On paper it is HID "Non-US #" (50). On a Windows ABNT2 system the game reports it as backslash (49), and scancode 50 shows an unrelated `#`. The `abnt2` drawing therefore uses 49 there. The same system confirmed the OS labels `Ç` (51), `\` (100), `/` (135), `´` (47), `[` (48), `~` (52) and `;` (56).
- The screen pauses singleplayer like other option screens. In multiplayer the world keeps running behind it.

### 17.5 Tests

- **Unit tests:**
  - `KeyboardLayoutsTest`: counts, no overlapping rectangles, scancode coverage, ABNT2 positions, sections.
  - `BindingCatalogTest`: states, keyboard versus mouse, modded mappings, search with accents and `key:`, AND filters, unbound, selection.
  - `RemapSessionTest`: capture, Escape, overlap confirmation and cancel, same-input no-op, Clear and Reset, nothing outside a capture; in-memory store only.
  - `KeyboardMapFeatureTest`: descriptor, LIVE, persistence across restarts.
  - `KeyboardMapGeometryTest`: 12 window and scale combinations; minimum key size, fit, split, tabs, ultrawide cap.
  - `LocalizationTest` now also checks every `KeyboardMapTexts` key.
- **Game test** `./gradlew runGameTestKeyboardmap` (disposable world and `options.txt`; a dev-only Fabric mapping on Q and an unbound one). It asserts, with real clicks and key presses:
  - catalog = `Options.keyMappings`;
  - Q is an overlap including the modded mapping;
  - key → rows and row → key highlighting;
  - `key:q` search, Escape clearing it, category, overlaps and unbound filters;
  - Change → F7 saved in `options.txt`;
  - Escape cancels;
  - an overlap needs confirmation, Cancel keeps the old key, Use anyway keeps the overlap;
  - mouse button 5 captured;
  - Reset and Clear;
  - W, Q, 3 and Space do nothing in game while open;
  - keys ≥ 12 px at windows 854×480, 1280×720 and 1920×1080, GUI scales 1–4;
  - the ABNT2 choice persisted through the Core;
  - the Controls row opens it and Escape returns;
  - disabled → no Controls row and the key opens nothing; enabled → the key opens it.
- **Optional:** `./gradlew runGameTestKeyboardmapControlling -PcontrollingJars=<Controlling>;<Searchables>` repeats it with Controlling 26.3.4 and Searchables 1.0.3.

### 17.6 Not verified automatically

- A physical ABNT2 keyboard's own key presses (only the OS labels were seen) and a physical US keyboard.
- Readability on snow, at night and in rain (screenshots were at noon on grass), and color-vision simulation.
- Multiplayer on the PO's Paper server.
- Keyboard-only use of the toolbar (vanilla Tab focus; not scripted).
- Mods that dispatch input outside `KeyMapping` (TR-4).
