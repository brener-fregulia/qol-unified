package unified.qol.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchCoreTest {
    @Test
    void foldsCaseAndAccents() {
        assertEquals("musica", SearchText.fold("Música"));
        assertEquals("acao", SearchText.fold("AÇÃO"));
        assertEquals("espada de diamante", SearchText.fold("Espada de Diamante"));
        assertEquals("naive cafe", SearchText.fold("Naïve CAFÉ"));
        assertEquals("", SearchText.fold(null));
        assertEquals("", SearchText.fold(""));
    }

    @Test
    void unicodeForms() {
        String composed = "Poção";
        String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);
        assertEquals(SearchText.fold(composed), SearchText.fold(decomposed), "NFC and NFD are the same text");
        assertEquals("istanbul", SearchText.fold("İstanbul"), "dotted capital I loses its dot");
        assertEquals("²", SearchText.fold("²"), "compatibility forms are kept (no NFKD)");
        assertEquals("straße", SearchText.fold("Straße"), "no expansion of ß");
        assertEquals("🎵 note", SearchText.fold("🎵 Note"), "supplementary characters survive");
    }

    @Test
    void normalizesWhitespace() {
        assertEquals("shift click", SearchText.fold("  Shift \t\n click  "));
        assertEquals("a b", SearchText.fold("a  b"), "no-break and em spaces");
        assertTrue(SearchQuery.of(" \t ").isEmpty());
        assertEquals(List.of("diamond", "sword"), SearchQuery.of("  Diamond   SWORD ").terms());
    }

    @Test
    void wordsMustAllMatch() {
        SearchableText item = SearchableText.of("Espada de Diamante", "minecraft:diamond_sword");
        assertTrue(SearchQuery.of("espada diamante").matchesAll(item));
        assertTrue(SearchQuery.of("diamante espada").matchesAll(item), "any order");
        assertTrue(SearchQuery.of("diamond sword").matchesAll(item), "words found in the id field");
        assertTrue(SearchQuery.of("espada sword").matchesAll(item), "words may come from different fields");
        assertFalse(SearchQuery.of("espada ferro").matchesAll(item));
        assertTrue(SearchQuery.of("shift click").matchesAll(SearchableText.of("Click while holding Shift")));
    }

    @Test
    void termsNeverSpanFields() {
        SearchableText text = SearchableText.of("Shift", "Click");
        assertFalse(SearchQuery.of("shift click").matchesPhrase(text));
        assertTrue(SearchQuery.of("shift click").matchesAll(text));
        assertFalse(SearchQuery.of("tcl").matchesAll(text), "no match across the field boundary");
    }

    @Test
    void phraseMatchesWithinOneField() {
        SearchableText text = SearchableText.of("Walk Forwards", "Movement");
        assertTrue(SearchQuery.of("walk  forw").matchesPhrase(text));
        assertFalse(SearchQuery.of("forwards walk").matchesPhrase(text));
    }

    @Test
    void emptyQueryMatchesEverything() {
        assertSame(SearchQuery.EMPTY, SearchQuery.of(""));
        assertSame(SearchQuery.EMPTY, SearchQuery.of(null));
        assertTrue(SearchQuery.EMPTY.matchesAll(SearchableText.EMPTY));
        assertTrue(SearchQuery.EMPTY.matchesPhrase(SearchableText.of("anything")));
    }

    @Test
    void fieldsKeepTheirPositions() {
        SearchableText text = SearchableText.of("Jump", null, "Space");
        assertEquals(3, text.size());
        assertEquals("", text.field(1));
        assertEquals("space", text.field(2));
        assertEquals(SearchableText.of("JUMP", "", "space"), text, "value equality");
    }

    @Test
    void prefixes() {
        SearchQuery q = SearchQuery.of("KEY:  Q ");
        assertEquals("q", q.afterPrefix("key:").text());
        assertTrue(SearchQuery.of("key:").afterPrefix("key:").isEmpty());
        assertNull(SearchQuery.of("keyboard").afterPrefix("key:"));
        assertNull(q.afterPrefix(""));
    }

    @Test
    void localizedLabelsAreSearchedAsGiven() {
        // The consumer supplies text in the active language; no translation happens in the core.
        SearchableText ptBr = SearchableText.of("Picareta de Netherita", "minecraft:netherite_pickaxe");
        assertTrue(SearchQuery.of("picareta").matchesAll(ptBr));
        assertTrue(SearchQuery.of("netherite pickaxe").matchesAll(ptBr), "the id still matches");
        assertFalse(SearchQuery.of("pickaxe netherita").matchesPhrase(ptBr));
    }

    @Test
    void resultsAreDeterministic() {
        List<SearchableText> entries = List.of(SearchableText.of("Música"), SearchableText.of("Musical Box"),
                SearchableText.of("Sound"), SearchableText.of("MÚSICA ambiente"));
        SearchQuery q = SearchQuery.of("musica");
        List<Integer> first = matching(entries, q);
        assertEquals(List.of(0, 1, 3), first, "input order is kept");
        assertEquals(first, matching(entries, SearchQuery.of(" MÚSICA ")), "same query, same result");
    }

    private static List<Integer> matching(List<SearchableText> entries, SearchQuery q) {
        var out = new ArrayList<Integer>();
        for (int i = 0; i < entries.size(); i++) {
            if (q.matchesAll(entries.get(i))) {
                out.add(i);
            }
        }
        return out;
    }
}
