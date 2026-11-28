# Unified Search Architecture (discovery)

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).
Option C, its placement and both migrations are **[Approved 2026-11-28]**
(Q-SA1 to Q-SA4) and implemented as Search Core V1 (§7). Findings are marked
**Verified**, **Inferred**, **Proposed** or **Open**.

This document asks whether Enhanced Statistics and Inventory Search justify a
shared Search API. Its consumers are:

- [Enhanced Statistics](../qol/specifications/enhanced-statistics-v1.md);
- [Inventory Search](../qol/specifications/inventory-search-v1.md);
- Keyboard Map;
- Sound Control;
- the Unified Settings workspace.

## 1. Search in the repository today (Verified)

| Consumer | Code | Folding | Tokens | Fields and filters |
| --- | --- | --- | --- | --- |
| Unified Settings workspace (library) | `WorkspaceNavigation.normalize` / `contains` (Kotlin, `libs/settings`) | NFD, strips accents, lowercase (`Locale.ROOT`) | Whole query as one substring | Feature name, description, id; setting label, key, option labels |
| Keyboard Map | `BindingFilter.fold` (Java, QoL) | Same as Settings (duplicated) | Whole query as one substring; prefix `key:` | Name, category, input label; category and visibility filters |
| Sound Control | `SoundSearch.words` (Java, QoL) | **Lowercase only, no accent folding** | Words, all must match | Name, id, namespace, mod name, category; category and namespace filters; minimum query length 2; row cap 200 |

**Findings:**

- **Two copies of the folding.** The same accent folding exists twice: once in
  the Settings library and once in Keyboard Map.
- **Sound Control does not fold accents.** In pt_br, "musica" does not find
  "Música". This is a real inconsistency, recorded and not changed here
  (Q-SA2).
- **Three tokenization rules.** One substring, one substring with a prefix,
  and all words.
- **One UI.** Every consumer already uses vanilla's `EditBox` plus its own
  list. No shared widget exists or is needed.

## 2. What the two new features need

| Need | Enhanced Statistics | Inventory Search |
| --- | --- | --- |
| Case- and accent-insensitive matching | Yes | Yes |
| Words, all must match | Yes | Yes |
| Several fields per entry (name, id) | Yes | Yes (plus enchantments) |
| Fold once, match many times | Yes (static per screen) | Yes (index on change) |
| Stable result order | Yes (sort is the consumer's) | Not applicable (slots keep their place) |
| Filter expressions | Group and zero filters (consumer-side) | `#tag` and `@mod` later (Q-IS6) |
| Autocomplete | No | No |
| Debounce | No (≤ 1 ms per change) | No (≤ 0.2 ms per change) |

Measured with a disposable fixture (Ryzen 5 5625U, Java 25):

| Entries | Folding (index) | Matching (cached) |
| --- | --- | --- |
| 90 | 0.16 ms | 2.5 µs |
| 2,500 | 4.8 ms | 0.12 ms |
| 10,000 | 19 ms | 0.47 ms |

Folding costs about 30–60× more than matching. The rule that matters is
therefore **fold once, never per frame**. Data structures beyond a list are
unnecessary at these sizes.

## 3. Option A: vanilla's search facilities (Verified)

`net.minecraft.client.searchtree` provides:

- `SearchTree`;
- `SuffixArray`;
- `IdSearchTree`, for `namespace:path`;
- `FullTextSearchTree`, which combines text and ids.

`SessionSearchTrees` builds trees asynchronously on `Util.backgroundExecutor()`
for creative items, creative tags and recipes, and rebuilds them after a
language change.

- **Strengths.**
  - Fast substring queries over large static sets.
  - Proven `namespace:path` semantics.
  - The tooltip-line extraction rule (`getTooltipLines(…, NORMAL)`, formatting
    stripped).
- **Limits.**
  - Lowercase with `Locale.ROOT` only, with no accent folding.
  - Building is costly and meant to run once, asynchronously.
  - Client-only API with no stability promise.
  - Not suited to slot contents that change with every click.
- **Reuse.** Reuse its *conventions* (`namespace:path`, `#tag`, the tooltip
  rule), and use the trees only if a consumer ever indexes thousands of static
  entries.

## 4. Option B: depend on Searchables (Verified from source and Modrinth)

- **Project.** `searchables` by Jaredlll08. MIT. Source:
  github.com/jaredlll08/searchables, branch `26.3`, commit `7fd4e98`
  (2026-10-09).
- **Version.** Fabric build 1.0.3 for Minecraft 26.3, published 2026-10-09.
- **Dependencies.**
  - Its 26.3 Fabric release declares only Fabric API as required (Modrinth
    metadata).
  - Its `fabric.mod.json` pins the exact `minecraft` version.
  - It ships one accessor Mixin on `EditBox`.
  - It has about 2,650 lines of Java.
- **API.**
  - `SearchableType<T>` is built from `SearchableComponent<T>` values, each a
    key plus a `toString` and a filter.
  - One component is the default; it is used for bare words.
  - `filterEntries(list, query)` returns the matches.
- **Query language.**
  - A hand-written scanner and parser (`SLScanner`, `SLParser`) produce
    `key:value` pairs and bare literals.
  - Quotes are `'`, `"` and `` ` ``.
  - All terms must match, in any order.
  - There is no OR or NOT.
- **Matching.** `StringUtils.containsIgnoreCase`, which is case-insensitive
  with **no accent folding**. The query is parsed again on every
  `filterEntries` call, and then every entry is scanned (O(n) per query).
  There is no index.
- **Autocomplete.** `AutoCompletingEditBox` and `AutoComplete` suggest
  component keys and the values present in the entries.
  - The suggestions widget's `updateWidgetNarration` is empty (`TODO`), so the
    narrator says nothing about suggestions.
  - Hard-coded colors.
- **Ecosystem.** Controlling's 26.3 Fabric build *requires* Searchables
  (Modrinth), so many players already have it.
- **Integration cost.**
  - A new production mod dependency, which needs PO approval: either
    installed by players or nested in QoL's JAR (MIT allows it; keep the
    notice).
  - It is pinned to the exact Minecraft version, so every update waits for
    it.
  - Accent folding would still need wrapping in every component filter.
  - The accessibility gap remains.
  - The query language exceeds what V1 needs.
- **Copying.** Not proposed. If an idea is reused, it is reimplemented from
  behavior. The MIT notice applies to any copied code.

## 5. Option C: a minimal internal Search API

A few pure-Java classes in QoL, with no Minecraft types, so unit tests cover
them fully.

## 6. Comparison and recommendation

| Criterion | A: vanilla trees | B: Searchables | C: internal minimal |
| --- | --- | --- | --- |
| Accent folding (pt_br) | No | No | Yes (existing rule) |
| Fits changing slot data | No (rebuild) | Yes (linear) | Yes (fold on change) |
| Fits static stats lists | Yes, but no folding | Yes | Yes |
| `key:value` filters | `#tag`, `ns:path` only | Yes, general | Only fixed prefixes a consumer defines |
| Autocomplete | No | Yes (no narration) | No (not needed) |
| New dependency | None | A mod, with an exact MC pin | None |
| License duties | None | MIT notice if nested | None |
| Maintenance | Mojang's; may change | External release cadence | About 150 lines owned by us |
| Interoperability | Vanilla conventions | Shared with Controlling and others | Follows vanilla's and Keyboard Map's conventions |
| Accessibility | Vanilla `EditBox` | Suggestions not narrated | Vanilla `EditBox` |

**Decision [Approved 2026-11-28, Q-SA1]: Option C**, keeping vanilla's
conventions from Option A.

- **Why not B.** Searchables' main value, a general query language with
  autocomplete, is something V1 explicitly avoids. Its matching would still
  need our folding, and the dependency would tie each QoL update to its
  release.
- **Why not A.** Vanilla's trees fit only large static sets.
- **Why C.** A shared algorithm is justified: four QoL consumers need the same
  fold-and-words rule, and two copies of it already exist.

### 6.1 The contract as first proposed (superseded by §7)

Package `unified.qol.search`:

```java
/** Case- and accent-insensitive form: NFD, combining marks removed, lowercase (Locale.ROOT). */
public static String fold(String text);

/** A parsed query: folded words (all must match) and optional prefixed terms the consumer declared. */
public record SearchQuery(List<String> words, Map<String, String> prefixed) {
    public static SearchQuery parse(String raw, Set<String> prefixes); // e.g. Set.of("key") for Keyboard Map
    public boolean isEmpty();
    /** True when every word occurs in at least one of the folded fields. */
    public boolean matchesAll(List<String> foldedFields);
}
```

- **Out of scope.**
  - Ordering stays with each consumer.
  - So do filters (category, group, visibility) and UI.
  - No query language, plugin registry, autocomplete or debouncing.
- **Caching.** It is the consumer's job: fold the fields once, store the
  strings, and call `matchesAll` per query.
- **Tests.** Unit tests cover folding (pt_br, combined characters), word
  splitting, prefixes, empty queries and the AND rule.

### 6.2 Consumers and migration

| Consumer | Use | Behavior change |
| --- | --- | --- |
| Inventory Search | New | — |
| Enhanced Statistics | New | — |
| Keyboard Map | `fold` replaces `BindingFilter.fold`; `key:` becomes a declared prefix | None, if the whole-query substring rule stays (Q-SA3) |
| Sound Control | `fold` would add accent folding | **Yes** (better pt_br results), so it needs approval (Q-SA2) |
| Unified Settings workspace | Not migrated | — |

The Settings library keeps its own `normalize`:

- It is a separate published library with its own version range
  (`>=1.2.0 <2.0.0`). Exposing search from it would make the search a public
  API with compatibility duties for every module that includes it.
- QoL already depends on the library, but `WorkspaceNavigation` is a UI class,
  not a contract.
- Unified: Optimizations has no search.

**Placement [Approved 2026-11-28, Q-SA4]:** in QoL (`unified.qol.search`).
No new Gradle library. A move to a shared library is reconsidered only when
another independently installed module needs search. Module builds stay
independent.

## 7. Implementation: Search Core V1

Implemented 2026-11-28 in `mods/qol/src/main/kotlin/unified/qol/search/`
(Kotlin, callable from Java; no Minecraft types, no dependencies, no threads).

### 7.1 API

| Type | Role |
| --- | --- |
| `SearchText.fold(text)` | The search form of any text: NFD, combining marks removed, whitespace runs collapsed to one space and trimmed, lowercase (`Locale.ROOT`). Compatibility forms (NFKD) are not folded, so `²` ≠ `2`. Pure; ASCII text skips the Unicode normalizer. |
| `SearchableText.of(fields…)` | Immutable folded fields of one entry, in the consumer's order. `contains(term)` tests one field at a time, so a term never spans two fields; `field(i)` serves field-specific rules. Value equality. |
| `SearchQuery.of(raw)` | Immutable folded query: `text()` and `terms()`. `matchesAll` = every term in some field (AND, any order); `matchesPhrase` = the whole text inside one field; `afterPrefix("key:")` for consumer-defined prefixes. `EMPTY` matches everything; consumers decide what an empty query shows. |

- **Ownership.** Prepared data belongs to the consumer that builds it and is
  kept next to the data it describes. There is no global or static cache.
- **When to prepare.**
  - Queries are folded once per text change.
  - Entries are folded once per data change: a snapshot, the screen opening, a
    changed label.
  - Nothing is folded per frame.
- **Out of scope:**
  - ordering, filters and UI, which stay with each consumer;
  - any query language, regular expressions, autocomplete, translation or
    debouncing.

### 7.2 Migrated consumers

| Consumer | Change | Behavior |
| --- | --- | --- |
| Keyboard Map | `BindingFilter` is an immutable class that folds its query once. `BindingCatalog` prepares each binding's fields (name, category, input label) once per snapshot. `KeyboardMapScreen` keeps its filter until the query, category or visibility changes. | Phrase rule unchanged; `key:` unchanged. New: repeated or unusual whitespace counts as one space. Fixed: the per-key search highlight used to refold the query and every name on every frame. |
| Sound Control | `SoundSearch.Candidate` carries its prepared `SearchableText`. `SoundControlScreen` caches candidates per sound id for the screen's lifetime (remade only when a sound's last-heard category changes) and localized category labels per category. | AND of words as before, now accent-insensitive (`musica` finds `Música`). Ordering (`BY_NAME`, recent order), filters, the 2-character and 200-row limits, live preview and rollback are untouched. |
| Unified Settings workspace | **Not changed.** `WorkspaceNavigation.normalize` (NFD, marks removed, lowercase; whole query trimmed but inner whitespace kept) stays a copy of the same rule. `libs/settings` must not depend on QoL. | — |

### 7.3 Validation

- **Unit tests.** `SearchCoreTest` has 11 tests:
  - case;
  - accents;
  - NFC/NFD;
  - dotted İ, `ß`, `²` and supplementary characters;
  - whitespace;
  - AND;
  - phrase;
  - field boundaries;
  - the empty query;
  - prefixes;
  - localized labels;
  - deterministic order.
- **Consumer tests.** These have regression cases:
  - `BindingCatalogTest`: the phrase rule, order, `KEY:` and whitespace;
  - `SoundSearchTest`: `musica` → `Música`, prepared text and candidate
    equality.
- **Game tests.** `runGameTestKeyboardmap` and `runGameTestSoundcontrol` pass,
  including the `key:q` search, Escape-clears and the Sound Control screen
  flows.

## 8. Shared UI widget: not proposed

Every consumer already uses vanilla's `EditBox`; their layouts differ:

- a toolbar in Statistics;
- a title-row field in containers;
- a header in Keyboard Map;
- the workspace's top bar in Settings.

A shared widget would couple screens without removing real duplication. Two
small conventions are proposed instead:

- the hint "Search..." (vanilla's `gui.recipebook.search_hint`);
- Escape clears or unfocuses before it closes.

## 9. Decisions

- **Q-SA1 [Approved 2026-11-28]:** Option C: an internal `unified.qol.search`,
  with no Searchables dependency.
- **Q-SA2 [Approved 2026-11-28]:** Sound Control migrated; accent-insensitive.
- **Q-SA3 [Approved 2026-11-28]:** Keyboard Map migrated. Its phrase rule and
  `key:` prefix are kept.
- **Q-SA4 [Approved 2026-11-28]:** No new Gradle library. A cross-module
  library is considered only when another independently installed module
  needs search.
