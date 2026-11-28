# Changelog: Unified: QoL

All notable changes to Unified: QoL (formerly "QoL: Unified") are documented in this file. Tags use `qol/v<version>`.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/).
Versions follow the Minecraft-aligned, SemVer-compatible scheme described in
the [versioning policy](../../docs/development/versioning.md). No version has been
released yet.

## [Unreleased]

### Added

- Target Info shows a placed Shulker Box's contents icon (first occupied slot,
  or the predominant item per Shulker Preview's setting) once you have opened
  that box in the current session, labelled "Contents as last seen".
  Minecraft does not send placed boxes' items to clients, so boxes you have not
  opened show no icon.

### Fixed

- Sound Control search ignores accents, so `musica` finds `Música`; any
  whitespace now separates words. Keyboard Map search also treats repeated
  spaces as one and no longer re-reads the action names for every key on
  every frame. Both now use one shared search rule; their results are
  otherwise unchanged.
- Inventory Mouse Tweaks no longer blocks vanilla's Shift + double-click with an
  item on the cursor, which moves every matching stack between a container and
  the player inventory.

### Changed

- Settings open in the shared **Unified Settings** workspace (key `U` or
  Options → Unified Settings…) with the same layout, controls and auto-apply;
  no intermediate module screen. Configuration file and schema are unchanged.
- The "Open Unified: QoL Settings" key binding is removed. A custom binding of
  it is moved once to **Open Unified Settings** unless that key was already
  customized.

- Requires Fabric Language Kotlin (`>=1.14.2+kotlin.2.4.21`) in addition to
  Fabric API: new code is written in Kotlin. Install it next to the mod.
- Project Unified: the mod is now published as "Unified: QoL" from the
  `Unified` monorepo (module `mods/qol`), and its JAR is
  `unified-qol-<version>.jar`. The mod id `qol_unified`, configuration file,
  key bindings and feature ids are unchanged; no migration is needed.
- Fast IP Ping, server ping optimization and fast world exit moved to
  Unified: Optimizations' catalog.
- Settings workspace visual polish: search box aligned with the workspace, compact
  footer, refined sidebar hierarchy and selection, controls sized to their text
  and consistent comparison boxes.

### Added

- Unified Settings: one **Open Unified Settings** key (default `U`) and a
  **Unified Settings…** button in Minecraft's Options screen list the
  installed Unified mods and open this mod's settings. The mod carries
  Unified Settings (Library) 1.0.0 inside its JAR. The old "Open Unified: QoL
  Settings" key still works for now; M2.1.2 merges it.
- Inventory Mouse Tweaks: right-drag places one item per slot, left-drag
  gathers matching items, Shift + drag moves stacks, and the wheel moves items
  between inventory sections, all through vanilla container clicks. Six
  settings; suppressed by default with Mouse Tweaks, Mouse Wheelie or Item
  Scroller; not active in the creative inventory.
- Zoom: hold (or toggle) Z to zoom the camera smoothly, scroll to zoom
  further (2–14×), with lower mouse sensitivity while zoomed. Nine settings in
  the Controls category; suppressed by default when Zoomify, Ok Zoomer or
  Logical Zoom is installed.
- Settings can declare a rule across several values; a rejected combination
  is explained at the field and never saved.
- Spec-Driven Development documentation: product vision, feature catalog,
  architecture overview, configuration policy and the M0 Platform
  Foundations specification.
- Agent guide (`AGENTS.md`) with decision status labels and engineering rules.
- Versioning, release and changelog policy.
- Minimal Fabric client mod for Minecraft 26.3 (Java 25, Fabric Loader,
  Fabric API) that logs a startup message; English and Brazilian Portuguese
  resources.
- GitHub Actions build verification on Java 25 (no publishing).
- Core feature registry and lifecycle foundation: validated feature and
  setting descriptors, a frozen explicit registry, safe enable/disable with
  rollback and failure records, and desired vs effective settings. No
  features are registered yet.
- Global configuration file `config/qol_unified/config.json` (schema version
  1): validation with safe defaults, preservation of unknown and invalid
  values, backups of corrupt or migrated files, read-only mode for newer
  schema versions and atomic saves.
- Detection of mods declared as conflicting with a feature: such features are
  disabled by default unless the player explicitly chose otherwise, and each
  conflict is logged at startup.
- Settings screen built from native Minecraft widgets, opened with a
  configurable key binding (no default key): categories, search, per-feature
  settings, status and conflict indicators, and Done/Cancel. English and
  Brazilian Portuguese.
- Food HUD (`food_hud`), the first feature: a saturation overlay on the
  hunger bar; blinking previews of the hunger, saturation and estimated
  health that held food would restore (main or off-hand, following vanilla's
  item-use order); food values as icons in tooltips (off, while holding
  Shift, or always); and food and saturation in an optional F3 line. It
  follows the vanilla icon animations, applies settings without a restart,
  and is disabled by default when AppleSkin is installed. It is client-only
  and never shows exhaustion, which the server does not send.
- Chat Heads (`chat_heads`): shows the sender's head, with its hat layer,
  in player chat, either directly before the name (`<[head]Name> text`) or at
  the start of the line. Heads come only from the sender profile sent with
  the message, never from reading names in the text, so system messages,
  command output and plugin broadcasts get no head. It uses vanilla's own
  player-head text glyph and skin cache, changes only the displayed copy of a
  message (signatures, trust indicators, reports and narration are
  untouched), updates messages already in chat when it or its position is
  changed, and is disabled by default when the original Chat Heads mod is
  installed. TAB is not changed.
- Keyboard Map (`keyboard_map`): a visual keyboard of all vanilla and modded
  key bindings, with US (ANSI) and Brazilian (ABNT2) layouts and key names
  from your system. Free keys are gray, keys with one action green, and keys
  shared by several actions red with a count. A searchable, filterable list
  sits beside the keyboard. Selecting a key highlights its actions and vice
  versa. Bindings can be changed, cleared and reset with Minecraft's own
  controls (saved to `options.txt` as usual), with a confirmation before a
  key is shared. Open it from Options → Controls, from the QoL: Unified
  settings, or with an optional key (unbound by default).
- Keyboard Map redesign: a calmer keyboard with muted colors and grouped
  sections, a drawn mouse with clickable left, right, wheel-click and side
  buttons, and one side panel. With nothing selected the panel lists all
  actions. Selecting a key, mouse button or action shows only what belongs
  to it, with Change, Clear and Reset on each action. Search matches are
  outlined on the keyboard. Filters sit in a compact popup.
- Target Info (`target_info`): a compact panel at the top center of the
  screen that names the block or entity under the crosshair, with its icon
  and source mod. For blocks it shows the tools that break it faster, a
  check or cross for the held item based on the game's own drop rules, the
  required tool when the held one falls short, and a thin mining-progress
  bar while breaking. It also shows beehive honey, redstone states (wire
  power, lever, button, lamp, repeater, comparator) and crop growth. For
  mobs it shows health and, for tamed animals, the owner when the client
  knows the name. Players show their name only. Eight settings, all
  applied live. Disabled by default when Jade or WTHIT is installed, unless
  you enable it yourself.
- Sound Control (`sound_control`, new Audio category): lower any individual
  sound from 0% to 100%, or mute it, without touching vanilla's volume
  sliders. Mute keeps the volume you set, also after a restart. A Recent
  tab lists the last 128 sounds heard this session (muted ones included,
  so they are easy to restore). Rules and All sounds tabs, with search by
  name, id or mod, category and mod filters, per-sound reset and Reset all.
  Works for modded sounds too. Changes apply after Done, including to
  sounds that are already playing. Open it from the QoL: Unified settings
  or with the new Sound Control... button in Music & Sound Options. Sounds
  that share one event change together: TNT and Creeper explosions are the
  same sound and can't be told apart. Disabled by default when Extreme
  Sound Muffler, Sound Controller or Decibel is installed, unless you
  enable it yourself.
- Configuration: a map setting type for per-item rules (used by Sound
  Control). Invalid or extra entries are ignored and kept in the file; no
  schema version change.
- Sound Control live preview: moving a slider, muting, unmuting or
  resetting a sound is heard immediately, including on sounds already
  playing, so volumes can be set by ear. Nothing is saved until you confirm.
  Cancel or Escape restores what you heard before, and canceling the QoL
  settings after Done in Sound Control restores the saved rules.
- Shulker Preview (`shulker_preview`, Inventory): shulker boxes in any
  inventory, container, hotbar or creative screen show their main item in
  the top-right corner and a fill bar like the bundle's. Hovering shows a
  summary (main item and total, the other items largest first, used slots,
  total items and kinds); holding Shift shows the exact 9×3 contents.
  Custom names and lore are kept. Uses only what the game already sends;
  nothing is shown when the contents are unknown. Three settings, all
  applied live. Disabled by default when Shulker Box Tooltip or Simple
  Shulker Preview is installed, unless you enable it yourself.
- Shulker Preview: a "Content icon" setting chooses whether the small slot
  icon shows the first occupied slot (new default) or the predominant item,
  and the hover hint now shows "Shift" in gold.
- Unified settings workspace: categories expand into their features in a
  sidebar, and choosing a feature shows all its settings in place, with a
  green ON / red OFF switch, a "Vanilla / QoL ON" example for Food HUD and
  Shulker Preview, and buttons for Keyboard Map and Sound Control. Search
  now also finds individual settings and option names and jumps to them.
  Narrow windows get a collapsible sidebar.
- Settings apply and save immediately: flipping a switch or choosing an
  option takes effect at once, and Back (or Escape) just closes the screen.
  Number fields save when you press Enter or leave the field. If a change
  cannot be saved, nothing is applied and you can retry or undo it. A
  refreshed ON/OFF switch, a "Vanilla / QoL ON" example for Target Info, and
  a tighter settings panel.

### Removed

- The separate per-feature settings screen and its Settings buttons; their
  controls are now part of the settings workspace.
- Done and Cancel in the QoL: Unified settings; changes apply as you make
  them.
