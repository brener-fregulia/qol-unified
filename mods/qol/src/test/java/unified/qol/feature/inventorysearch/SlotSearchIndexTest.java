package unified.qol.feature.inventorysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import unified.qol.search.SearchQuery;
import unified.qol.search.SearchableText;

/** The per-screen slot index (inventory-search-v1.md §6), with plain values standing in for item stacks. */
class SlotSearchIndexTest {
    /** An item stand-in: id, components (as text) and count. The count never affects search. */
    record Stack(String id, String components, int count) {
    }

    /** Display names per language, as the game would supply them. */
    private static final Map<String, Map<String, String>> NAMES = Map.of(
            "en_us", Map.of("minecraft:diamond_sword", "Diamond Sword", "minecraft:enchanted_book", "Enchanted Book",
                    "minecraft:stone", "Stone"),
            "pt_br", Map.of("minecraft:diamond_sword", "Espada de Diamante", "minecraft:enchanted_book",
                    "Livro Encantado", "minecraft:stone", "Pedra"));

    private String language = "en_us";
    private final List<Stack> slots = new ArrayList<>();
    private final SlotSearchIndex<Stack> index = new SlotSearchIndex<>(
            (a, b) -> a.id().equals(b.id()) && a.components().equals(b.components()),
            s -> new Stack(s.id(), s.components(), 1),
            s -> SearchableText.of(NAMES.get(language).get(s.id()), s.id(), s.components()));

    private boolean refresh() {
        return index.refresh(slots.size(), language, slots::get);
    }

    private List<Integer> matching(String query) {
        SlotMatches m = index.matches(SearchQuery.of(query));
        var out = new ArrayList<Integer>();
        for (int i = 0; i < slots.size(); i++) {
            if (m.isMatch(i)) {
                out.add(i);
            }
        }
        return out;
    }

    private void fill(Stack... stacks) {
        slots.clear();
        slots.addAll(Arrays.asList(stacks));
        refresh();
    }

    @Test
    void preparesEachSlotOnceAndMatchesNamesIdsAndEnchantments() {
        fill(new Stack("minecraft:diamond_sword", "Sharpness", 1), null, new Stack("minecraft:stone", "", 64),
                new Stack("minecraft:enchanted_book", "Mending", 1));
        assertEquals(3, index.getPreparedCount(), "empty slots are not prepared");
        assertEquals(List.of(0), matching("diamond"));
        assertEquals(List.of(0), matching("minecraft:diamond_sword"));
        assertEquals(List.of(0), matching("DIAMOND   sword"), "words, any case and spacing");
        assertEquals(List.of(3), matching("mending"), "enchantment names");
        assertEquals(List.of(0, 2, 3), matching("minecraft"), "deterministic slot order");
        assertEquals(List.of(), matching("diamond stone"), "every word must match one slot");
        assertFalse(refresh(), "nothing changed");
        assertEquals(3, index.getPreparedCount(), "an unchanged refresh prepares nothing");
    }

    @Test
    void emptyQueryMarksNothing() {
        fill(new Stack("minecraft:stone", "", 1), null);
        SlotMatches m = index.matches(SearchQuery.of("   "));
        assertFalse(m.active());
        assertEquals(0, m.count());
        assertFalse(m.isDimmed(0), "vanilla look");
        assertFalse(m.isMatch(0));
    }

    @Test
    void dimsOnlyOccupiedNonMatchingSlotsAndCounts() {
        fill(new Stack("minecraft:stone", "", 1), null, new Stack("minecraft:diamond_sword", "", 1),
                new Stack("minecraft:stone", "", 5));
        SlotMatches m = index.matches(SearchQuery.of("stone"));
        assertTrue(m.active());
        assertEquals(2, m.count());
        assertTrue(m.isMatch(0) && m.isMatch(3));
        assertTrue(m.isDimmed(2));
        assertFalse(m.isDimmed(1), "an empty slot is never dimmed");
        assertSame(m, index.matches(SearchQuery.of(" STONE ")), "cached for an equal query");
    }

    @Test
    void countChangesAreFreeButComponentChangesPrepareAgain() {
        fill(new Stack("minecraft:diamond_sword", "", 1));
        long prepared = index.getPreparedCount();
        int version = index.getVersion();
        slots.set(0, new Stack("minecraft:diamond_sword", "", 7));
        assertFalse(refresh(), "a count change is not a search change");
        assertEquals(prepared, index.getPreparedCount());
        slots.set(0, new Stack("minecraft:diamond_sword", "Sharpness", 7));
        assertTrue(refresh());
        assertEquals(prepared + 1, index.getPreparedCount());
        assertTrue(index.getVersion() > version);
        assertEquals(List.of(0), matching("sharpness"));
    }

    @Test
    void slotUpdatesEmptyingResizingAndReplacing() {
        fill(new Stack("minecraft:stone", "", 1), new Stack("minecraft:stone", "", 1));
        SlotMatches before = index.matches(SearchQuery.of("stone"));
        assertEquals(2, before.count());
        slots.set(1, null);
        assertTrue(refresh(), "a slot emptied");
        assertNull(index.text(1));
        assertEquals(1, index.matches(SearchQuery.of("stone")).count(), "matches follow the change");
        slots.set(0, new Stack("minecraft:diamond_sword", "", 1));
        refresh();
        assertEquals(List.of(), matching("stone"));
        slots.remove(1);
        assertTrue(refresh(), "fewer slots");
        assertEquals(1, index.size());
        slots.add(new Stack("minecraft:stone", "", 1));
        slots.add(new Stack("minecraft:stone", "", 1));
        refresh();
        assertEquals(List.of(1, 2), matching("stone"), "more slots");
    }

    @Test
    void languageChangePreparesEverySlotAgain() {
        fill(new Stack("minecraft:diamond_sword", "", 1), new Stack("minecraft:stone", "", 1));
        assertEquals(List.of(), matching("espada"));
        long prepared = index.getPreparedCount();
        language = "pt_br";
        assertTrue(refresh());
        assertEquals(prepared + 2, index.getPreparedCount(), "every slot prepared in the new language");
        assertEquals(List.of(0), matching("espada diamante"));
        assertEquals(List.of(1), matching("pedra"));
        assertEquals(List.of(0), matching("diamond sword"), "the id still matches in any language");
    }

    @Test
    void snapshotsAreStoredSoInPlaceMutationIsDetected() {
        // A real stack can change in place; the index compares against its own copy, not the live object.
        var mutable = new Stack[] {new Stack("minecraft:stone", "", 1)};
        var single = new SlotSearchIndex<Stack[]>((a, b) -> a[0].equals(b[0]), s -> new Stack[] {s[0]},
                s -> SearchableText.of(s[0].id()));
        single.refresh(1, "en_us", i -> mutable);
        mutable[0] = new Stack("minecraft:diamond_sword", "", 1);
        assertTrue(single.refresh(1, "en_us", i -> mutable), "the live object changed under the same reference");
        assertTrue(single.matches(SearchQuery.of("diamond")).isMatch(0));
    }
}
