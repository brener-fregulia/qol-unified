# Vanilla World Compatibility Contract

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).
Applies to every Unified mod (QoL, Optimizations, the future Graphics) and to
any future native (Rust, C or C++) component of them.

Each finding also carries an evidence label:

| Evidence | Meaning |
| --- | --- |
| **Verified** | Checked in this repository, in Minecraft 26.3's bytecode, or by running it on 2026-11-28. |
| **Inferred** | Follows from verified facts or documentation, but was not run. |
| **Proposed** | A rule or test suggested here, awaiting approval. |
| **Open** | A question for the Product Owner. |

Written on 2026-11-28 from the Rust/AOT discovery brief
([Rust feasibility](../discovery/rust-native-aot-feasibility.md),
[Graphics discovery](../discovery/unified-graphics-rust-vulkan.md)).

## 1. The principle [Approved 2026-11-28]

The Product Owner made this the most important architectural requirement
(discovery brief of 2026-11-28):

> Every Unified mod must be removable or disableable without making existing
> Minecraft worlds dependent on Unified.

After every Unified mod is removed:

- vanilla Minecraft 26.3 still loads existing worlds;
- blocks and items keep their identities and contents;
- inventories, entities and player data stay valid;
- world-generation data stays readable;
- advancements and statistics stay valid;
- chunks and region files stay accessible;
- no custom storage format is needed to open a world;
- no missing Unified registry id can invalidate persisted content;
- no background operation leaves a partially written world;
- no proprietary save-format migration is needed to return to vanilla.

**No Unified-specific persistent world data in V1** unless the Product Owner
explicitly approves it. Client-local configuration and disposable caches are
allowed when their absence cannot prevent a world from loading.

## 2. What "compatible" covers

Removing a Unified mod may change several things. Only the first is a hard
guarantee.

| Dimension | After removal | Guarantee |
| --- | --- | --- |
| **Save compatibility** | The world opens in vanilla with its content intact. | **Hard invariant** (§1). |
| **Runtime behavior** | Vanilla behavior returns (e.g. the client waits on "Saving world" again without FastQuit). | Expected change; never data loss. |
| **Visual appearance** | Vanilla rendering returns (no custom shaders, upscaling or HUD elements). | Expected change. |
| **Performance** | FPS, latency, startup and shutdown times may change. | Expected change. |
| **Client configuration** | `config/<mod_id>/` files stay on disk unused; Unified key bindings disappear from `options.txt` the next time vanilla saves options. | Harmless leftovers; never read by vanilla. |

## 3. Current state of the repository (Verified, HEAD `735889f`)

| Check | Result |
| --- | --- |
| Registries | No production source registers a block, item, entity, data component, attachment or any other registry entry (`grep` for `Registry.register`, `registerForHolder`, `DataComponentType` creation, `AttachmentRegistry`, `SavedData` in `mods/*/src/main` and `libs/*/src/main`). The only `DataComponentType` reference is a QoL tooltip Mixin parameter. |
| World files | No production code resolves a world path (`LevelResource`, `getWorldPath`) or writes under `saves/`. Only the Optimizations game test reads `session.lock` of a disposable test world. |
| Data and resources | No `data/` directory (no datapack content) in any mod; `assets/` holds only language files and QoL HUD sprites. |
| Writes | QoL writes `config/qol_unified/` (atomic, with backups) and, through vanilla `Options.save()`, `options.txt` (Keyboard Map). Optimizations writes `config/unified_optimizations/config.json` atomically. Unified Settings stores nothing of its own beyond these. |
| Inventory | Inventory Mouse Tweaks sends ordinary container clicks through `MultiPlayerGameMode.handleContainerInput`; the server (or integrated server) stays authoritative. |
| Saving | FastQuit changes only *who waits* for the integrated server's unchanged vanilla shutdown ([FastQuit V1](../optimizations/specifications/fastquit-v1.md) §6–§7). |

Conclusion (**Verified**): today no Unified mod writes world data, so removing
them cannot make a world depend on Unified. This contract keeps it that way.

## 4. Storage classes [Proposed]

### 4.1 Allowed without further approval

| Class | Location | Rule |
| --- | --- | --- |
| Client configuration | `config/<mod_id>/` | Each mod owns its directory; atomic writes; unknown keys preserved (existing behavior). |
| Vanilla client options | `options.txt`, through vanilla's own `Options` API only | Only vanilla's code writes the file. A mod never rewrites `options.txt` itself. |
| Disposable caches | A per-mod cache directory outside `saves/` (location: Q-VWC1) | Deleting it at any time must be harmless; contents are versioned and rebuilt; never needed to open a world. Includes shader and native-library caches. |
| Logs | Vanilla `logs/` through SLF4J | Feature id in each line (existing diagnostics rule). |

### 4.2 Allowed runtime optimizations

A change that alters **how or when** vanilla code runs, but not **what** is
persisted, is allowed after the normal spec approval. Example: FastQuit lets
the vanilla save run while the client shows the title screen.

Requirements:

- vanilla's own save code produces every persisted byte;
- no save step is skipped, reordered or deferred past game exit;
- the world lock (`session.lock`) semantics stay vanilla;
- the optimization can be disabled live or by removing the mod, with vanilla
  behavior returning and no migration.

### 4.3 Requires explicit Product Owner approval

- Any file inside a world directory (`saves/<world>/`), even a "sidecar".
- Any change to the content of persisted data (NBT, region files, `level.dat`,
  `playerdata/`, `advancements/`, `stats/`, `data/`), even when vanilla can
  read the result.
- Changing vanilla client options on the player's behalf (for example setting
  `preferredGraphicsBackend` in `options.txt`; see
  [Graphics discovery](../discovery/unified-graphics-rust-vulkan.md) §9).
- Background threads or processes that run while a world is being saved.
- Any native code reachable from a save, chunk-IO or world-lock path.

### 4.4 Prohibited (no exception in V1)

- Registering blocks, items, entities, block entities, data components,
  attachments, dimensions, biomes, structures or other registry entries that
  reach saved data.
- Writing custom chunk, region or NBT formats, or compressing world data in a
  non-vanilla format.
- Datapacks or world-generation changes persisted in `level.dat`.
- A migration or conversion step that a player must undo to return to vanilla.
- Skipping or reordering vanilla save steps, or ending the process before the
  integrated server's thread finishes.
- Native code writing anything under `saves/`.

## 5. Save and shutdown ordering [Proposed]

Builds on the verified 26.3 lifecycle in
[FastQuit V1](../optimizations/specifications/fastquit-v1.md) §3.

1. The integrated server thread owns saving. No Unified feature saves world
   data from another thread.
2. A world's storage is opened only through vanilla's `LevelStorageSource`
   (which takes `session.lock`).
3. Game exit waits for any in-progress integrated server shutdown (FastQuit's
   existing guarantee); a feature that adds work at exit must finish before
   that wait, or must not touch world data.
4. A Unified background task is cancelled or completed **before** the server
   begins `stopServer()` if it reads world state; it never holds world locks.
5. Native code (when it exists) never runs on the server thread during
   `stopServer()` and never between `halt` and the end of the server thread.

## 6. Failure handling [Proposed]

| Situation | Required behavior |
| --- | --- |
| Feature fails before it changes anything | Disable the feature, log with its id, continue in vanilla behavior. |
| Feature fails while changing client-only state | Revert to vanilla behavior; configuration left as last saved. |
| Configuration unreadable | Existing rule: move aside as `.invalid`, use defaults. |
| Cache unreadable or of a different version | Delete and rebuild; never block world loading. |
| Native library missing, wrong architecture, ABI mismatch | Feature stays inactive from startup; vanilla path used ([Rust feasibility](../discovery/rust-native-aot-feasibility.md) §10). |
| Native panic or crash | **Cannot be caught in general.** A Rust panic with `panic = "abort"` ended the JVM with SIGABRT (exit 134) and no Java exception (**Verified**, fixture of 2026-11-28). A crash is equivalent to `kill -9`; hence §4.3 keeps native code out of save paths. |
| Power loss, `kill -9`, JVM `halt` during a save | Not preventable by a mod; same exposure as vanilla (§7). |

## 7. Limits that vanilla itself does not guarantee

These are stated so that Unified never promises more than Minecraft does
(**Inferred** from vanilla's design and the verified FastQuit lifecycle):

- **Interrupted saves.** Power loss, `kill -9` or an OS crash during a vanilla
  save can corrupt region files or `level.dat` with or without Unified.
  Vanilla keeps `level.dat_old` as its own recovery copy.
- **Other mods.** Content added by non-Unified mods (blocks, items,
  dimensions) is outside this contract. Removing *them* may break a world;
  removing Unified must not add to that.
- **Downgrades.** Opening a world in an *older* Minecraft version than the one
  that last saved it is not supported by vanilla.
- **Multiplayer worlds** live on the server. Client-only Unified mods never
  write them; server data is outside this contract.
- **Deterministic content.** Normal play changes files (time, entity
  positions, chunk ticks); a world being *different* after play is not a
  compatibility failure (§8 compares meaning, not hashes).
- **Client options.** Vanilla drops unknown key-binding lines from
  `options.txt` when it rewrites the file; Unified key choices are then lost
  (harmless; they return to defaults if the mod is reinstalled).

## 8. Uninstall/reinstall acceptance test [Proposed]

A development-only, automated test on disposable worlds. No production world,
Prism instance or Paper server is ever used.

### 8.1 Procedure

1. **Create.** In a disposable game directory, create a vanilla-generated
   world with a fixed seed (Fabric with no Unified mods, or the vanilla
   dedicated server JAR from Loom's cache).
2. **Snapshot A.** Read the world with Minecraft's own classes (`NbtIo`,
   region reading) into a semantic snapshot (§8.2).
3. **Play with Unified.** Launch a client with the Unified mod(s) under test
   (Optimizations, later Graphics) and run a scripted session: place and break
   blocks, fill a chest and a Shulker Box, move items with Mouse Tweaks
   gestures when QoL is included, travel to load new chunks, earn one
   advancement, save (`/save-all`-equivalent and Save and Quit, with FastQuit
   on and off). Exit normally.
4. **Snapshot B** of the same world.
5. **Remove** every Unified JAR, Fabric Language Kotlin and Fabric API, so
   that step 6 runs truly vanilla code.
6. **Open in vanilla.** Start the **vanilla 26.3 dedicated server** (headless)
   on a copy of the world; it must load every dimension without errors,
   then stop cleanly. A manual QA step repeats this with the vanilla client.
7. **Snapshot C** after the vanilla run.
8. **Compare** A → B → C semantically (§8.3).

### 8.2 Semantic snapshot

| Area | Captured |
| --- | --- |
| `level.dat` | `DataVersion`, seed, world-gen settings, game rules, enabled datapacks, spawn |
| Chunks | Block states and block-entity NBT at a fixed list of coordinates; the set of generated chunk positions; per-chunk `DataVersion` and status |
| Containers | Item ids, counts and data components of the test chest and Shulker Box |
| Player | Inventory, ender chest, position (tolerance), game mode, `playerdata/<uuid>.dat` readability |
| Entities | Count by type in the test area; every entity's type id resolvable in vanilla registries |
| Progress | The advancement ids done; `stats/<uuid>.json` readability |
| Namespaces | Every resource id found anywhere in the world (blocks, items, entities, components, advancements) |

### 8.3 Acceptance criteria

- **WC-AC1** Vanilla loads the world after removal, with no "missing
  registry", data-fixer or chunk-load error in its log.
- **WC-AC2** No resource id in the namespaces `qol_unified`,
  `unified_optimizations`, `unified_settings`, `unified_graphics` (or any
  future Unified id) appears in any world file.
- **WC-AC3** Every block, container item and player-inventory item placed by
  the script is identical in B and C (id, count, components).
- **WC-AC4** Advancements earned in step 3 are still done in C.
- **WC-AC5** No file or directory created by Unified exists under the world
  directory.
- **WC-AC6** `session.lock` is free after each exit, and `level.dat` and
  `level.dat_old` parse.
- **WC-AC7** Reinstalling Unified and opening the world again works and
  changes nothing beyond normal play.
- **WC-AC8** The test reports the semantic differences it tolerated
  (positions, time, newly generated chunks) instead of hiding them.

### 8.4 Implementation notes

- Fits the existing pattern of focused, disposable game tests
  (`build/run-gametest-*`). The vanilla-server step needs only Loom's cached
  server JAR; no new dependency (**Inferred**, to confirm when implemented).
- File hashes are **not** a criterion: vanilla rewrites files during normal
  saves.
- Run when a feature touches saving, chunk loading, inventories or (later)
  rendering lifecycle, and before each release; not on every commit.
- Graphics: repeat once it exists; it must pass trivially (Graphics never
  writes worlds), and additionally verify that deleting its caches is
  harmless.

## 9. Open questions

- **Q-VWC1 [Open]:** location of disposable caches. Proposal: one directory
  per mod outside `saves/` and outside `config/` (for example
  `<gameDir>/unified-cache/<mod_id>/`), so deleting it is obviously safe.
- **Q-VWC2 [Open]:** implement the §8 test now (as an Optimizations
  development task) or when the first feature touching saves or chunks is
  proposed?
- **Q-VWC3 [Open]:** should Unified mods record nothing at all in worlds even
  after V1 (permanent rule), or may a later, approved feature add
  vanilla-ignorable data?
