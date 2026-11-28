package unified.qol.feature.keyboardmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import unified.qol.feature.keyboardmap.BindingFilter.Visibility;
import java.util.List;
import org.junit.jupiter.api.Test;

class BindingCatalogTest {
    static final InputId W = InputId.key(26);
    static final InputId Q = InputId.key(20);
    static final InputId KEY_4 = InputId.key(4);
    static final InputId MOUSE_4 = InputId.mouse(4);

    static Binding binding(String id, String name, String category, String label, InputId input) {
        return new Binding(id, name, category, label, input, input);
    }

    static BindingCatalog sample() {
        return new BindingCatalog(List.of(
                binding("key.forward", "Walk Forwards", "Movement", "W", W),
                binding("key.drop", "Drop Selected Item", "Gameplay", "Q", Q),
                binding("key.mod.zoom", "Zoom", "Example Mod", "Q", Q),
                binding("key.mod.ação", "Ação Rápida", "Example Mod", "A", KEY_4),
                binding("key.mod.back", "Go Back", "Example Mod", "Mouse 4", MOUSE_4),
                binding("key.spectatorOutlines", "Highlight Players", "Miscellaneous", "", null)));
    }

    @Test
    void classifiesUnboundSingleAndOverlap() {
        BindingCatalog c = sample();
        assertEquals(InputState.SINGLE, c.state(W));
        assertEquals(InputState.OVERLAP, c.state(Q), "two mappings on Q, whatever they do");
        assertEquals(InputState.UNBOUND, c.state(InputId.key(5)));
        assertEquals(InputState.UNBOUND, c.state(c.binding("key.spectatorOutlines").orElseThrow()));
        assertEquals(2, c.on(Q).size());
        assertEquals(List.of(), c.on(null));
    }

    @Test
    void keyboardAndMouseCodesAreSeparateInputs() {
        BindingCatalog c = sample();
        assertEquals(InputState.SINGLE, c.state(KEY_4));
        assertEquals(InputState.SINGLE, c.state(MOUSE_4));
        assertEquals("key.mod.back", c.on(MOUSE_4).getFirst().id());
    }

    @Test
    void moddedMappingsTakePartLikeVanillaOnes() {
        BindingCatalog c = sample();
        assertTrue(c.on(Q).stream().anyMatch(b -> b.id().equals("key.mod.zoom")));
        assertEquals(List.of("Movement", "Gameplay", "Example Mod", "Miscellaneous"), c.categories());
    }

    @Test
    void overlapsIfBoundExcludesTheMappingItself() {
        BindingCatalog c = sample();
        assertEquals(List.of("key.drop", "key.mod.zoom"),
                c.overlapsIfBound("key.forward", Q).stream().map(Binding::id).toList());
        assertEquals(List.of("key.mod.zoom"), c.overlapsIfBound("key.drop", Q).stream().map(Binding::id).toList());
        assertTrue(c.overlapsIfBound("key.forward", InputId.key(5)).isEmpty());
    }

    private static List<String> ids(BindingCatalog c, BindingFilter f) {
        return c.bindings().stream().filter(b -> f.matches(b, c)).map(Binding::id).toList();
    }

    @Test
    void searchMatchesNameCategoryAndKeyIgnoringCaseAndAccents() {
        BindingCatalog c = sample();
        assertEquals(List.of("key.forward"), ids(c, new BindingFilter("walk", null, Visibility.ALL)));
        assertEquals(List.of("key.mod.ação"), ids(c, new BindingFilter("acao", null, Visibility.ALL)));
        assertEquals(List.of("key.mod.ação"), ids(c, new BindingFilter("AÇÃO", null, Visibility.ALL)));
        assertEquals(3, ids(c, new BindingFilter("example", null, Visibility.ALL)).size(), "category");
        assertEquals(List.of("key.drop", "key.mod.zoom"), ids(c, new BindingFilter("key:q", null, Visibility.ALL)));
        assertEquals(5, ids(c, new BindingFilter("key:", null, Visibility.ALL)).size(), "every bound mapping");
    }

    @Test
    void sharedSearchCoreKeepsPhraseSemanticsAndOrder() {
        BindingCatalog c = sample();
        // Phrase in one field, as before the migration: words in another order or across fields do not match.
        assertEquals(List.of("key.forward"), ids(c, new BindingFilter("walk forwards", null, Visibility.ALL)));
        assertEquals(List.of(), ids(c, new BindingFilter("forwards walk", null, Visibility.ALL)));
        assertEquals(List.of(), ids(c, new BindingFilter("walk movement", null, Visibility.ALL)));
        // Whitespace is normalized on both sides; the key prefix ignores case.
        assertEquals(List.of("key.forward"), ids(c, new BindingFilter("  walk   forwards ", null, Visibility.ALL)));
        assertEquals(List.of("key.drop", "key.mod.zoom"), ids(c, new BindingFilter("KEY: Q", null, Visibility.ALL)));
        assertEquals(List.of("key.mod.ação"), ids(c, new BindingFilter("acao rapida", null, Visibility.ALL)));
        // Results keep the catalog's order.
        assertEquals(List.of("key.mod.zoom", "key.mod.ação", "key.mod.back"),
                ids(c, new BindingFilter("example", null, Visibility.ALL)));
        // The prepared fields come from the snapshot: name, category, input label.
        Binding drop = c.binding("key.drop").orElseThrow();
        assertEquals("drop selected item", c.searchText(drop).field(0));
        assertEquals("q", c.searchText(drop).field(2));
    }

    @Test
    void filtersAreValuesOfTheirInputs() {
        var a = new BindingFilter("Walk", "Movement", Visibility.ALL);
        assertEquals(a, new BindingFilter("Walk", "Movement", null));
        assertTrue(a.sameAs("Walk", "Movement", Visibility.ALL));
        assertFalse(a.sameAs("walk", "Movement", Visibility.ALL), "a changed query makes a new filter");
        assertEquals("", new BindingFilter(null, null, null).query());
    }

    @Test
    void filtersCombineWithAnd() {
        BindingCatalog c = sample();
        assertEquals(List.of("key.drop", "key.mod.zoom"), ids(c, new BindingFilter("", null, Visibility.OVERLAPS)));
        assertEquals(List.of("key.mod.zoom"), ids(c, new BindingFilter("", "Example Mod", Visibility.OVERLAPS)));
        assertEquals(List.of(), ids(c, new BindingFilter("walk", "Example Mod", Visibility.ALL)));
        assertEquals(5, ids(c, new BindingFilter("", null, Visibility.ASSIGNED)).size());
        assertEquals(6, ids(c, BindingFilter.all()).size());
        assertTrue(BindingFilter.all().isEmpty());
    }

    @Test
    void unboundActionsStayDiscoverable() {
        BindingCatalog c = sample();
        assertEquals(List.of("key.spectatorOutlines"), ids(c, new BindingFilter("", null, Visibility.UNBOUND)));
        assertTrue(ids(c, BindingFilter.all()).contains("key.spectatorOutlines"));
        assertEquals(List.of("key.spectatorOutlines"), ids(c, new BindingFilter("highlight", null, Visibility.ALL)));
        assertFalse(ids(c, new BindingFilter("key:", null, Visibility.ALL)).contains("key.spectatorOutlines"));
    }

    @Test
    void visibilityIdsMatchTheSettingValues() {
        for (Visibility v : Visibility.values()) {
            assertEquals(v, Visibility.byId(v.id()));
        }
        assertEquals(Visibility.ALL, Visibility.byId("nonsense"));
    }

    @Test
    void selectingAnInputHighlightsItsMappingsAndViceVersa() {
        BindingCatalog c = sample();
        var selection = new Selection();
        assertEquals(Selection.Mode.EXPLORE, selection.mode());
        selection.selectInput(Q);
        assertEquals(Selection.Mode.INPUT, selection.mode());
        assertEquals(Q, selection.highlightedInput(c));
        assertEquals(2, selection.highlightedBindings(c).size());
        assertTrue(selection.highlights(c.binding("key.mod.zoom").orElseThrow(), c));
        selection.selectInput(Q);
        assertEquals(Selection.Mode.EXPLORE, selection.mode(), "clicking the same key again returns to Explore");
        assertNull(selection.highlightedInput(c));

        selection.selectBinding(c, "key.forward");
        assertEquals(W, selection.highlightedInput(c), "a row selects its key");
        assertEquals("key.forward", selection.bindingId(), "with the row in focus");
        assertEquals(1, selection.highlightedBindings(c).size());
        selection.clear();
        assertTrue(selection.highlightedBindings(c).isEmpty());
    }

    @Test
    void theInputCardShowsOnlyThatInputWhateverTheFilters() {
        BindingCatalog c = sample();
        var selection = new Selection();
        selection.selectBinding(c, "key.drop");
        assertEquals(List.of("key.drop", "key.mod.zoom"),
                selection.highlightedBindings(c).stream().map(Binding::id).toList(), "both actions on Q");
        selection.selectInput(InputId.key(5));
        assertEquals(Selection.Mode.INPUT, selection.mode(), "an unused key still opens its (empty) card");
        assertTrue(selection.highlightedBindings(c).isEmpty());
        selection.selectInput(MOUSE_4);
        assertEquals(List.of("key.mod.back"), selection.highlightedBindings(c).stream().map(Binding::id).toList(),
                "mouse button 4, not scancode 4");
    }

    @Test
    void anUnboundActionOpensItsOwnCardForAssignment() {
        BindingCatalog c = sample();
        var selection = new Selection();
        selection.selectBinding(c, "key.spectatorOutlines");
        assertEquals(Selection.Mode.BINDING, selection.mode());
        assertNull(selection.highlightedInput(c), "no key to outline");
        assertEquals(List.of("key.spectatorOutlines"),
                selection.highlightedBindings(c).stream().map(Binding::id).toList());
    }

    @Test
    void afterARemapTheCardFollowsTheAction() {
        var selection = new Selection();
        selection.selectBinding(sample(), "key.forward");
        BindingCatalog moved = new BindingCatalog(List.of(
                binding("key.forward", "Walk Forwards", "Movement", "Q", Q),
                binding("key.drop", "Drop Selected Item", "Gameplay", "Q", Q)));
        selection.follow(moved, "key.forward");
        assertEquals(Q, selection.highlightedInput(moved));
        assertEquals(2, selection.highlightedBindings(moved).size());
        BindingCatalog cleared = new BindingCatalog(List.of(
                new Binding("key.forward", "Walk Forwards", "Movement", "", null, W)));
        selection.follow(cleared, "key.forward");
        assertEquals(Selection.Mode.BINDING, selection.mode(), "cleared: its own card, ready to assign again");
    }
}
