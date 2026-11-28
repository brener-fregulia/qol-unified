package unified.qol.feature.inventorysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import unified.qol.feature.inventorysearch.SearchFieldLayout.Box;
import unified.qol.feature.inventorysearch.SearchFieldLayout.Kind;
import unified.qol.feature.inventorysearch.SearchFieldLayout.Placement;

/** Placement rules (inventory-search-v1.md §5.1) with Minecraft 26.3's container sizes. */
class SearchFieldLayoutTest {
    private static final int W = 176;
    /** The first slot row's border, relative to the container top. */
    private static final int SLOT_TOP = 17;

    private static int top(int screenHeight, int imageHeight) {
        return (screenHeight - imageHeight) / 2;
    }

    private static void clearOfSlotsAndTitle(Box b, int left, int top, int titleEnd) {
        if (b.getPlacement() == Placement.TITLE_ROW) {
            assertTrue(b.getY() + b.getHeight() <= top + SLOT_TOP, "above the first slot row: " + b);
            assertTrue(b.getX() >= left + titleEnd, "after the title: " + b);
        } else if (b.getPlacement() == Placement.ABOVE) {
            assertTrue(b.getY() >= 0 && b.getY() + b.getHeight() < top, "fully above the container: " + b);
        }
        assertTrue(b.getX() + b.getWidth() <= left + W - 7, "inside the slot columns' right edge: " + b);
    }

    @Test
    void chestsUseTheTitleRow() {
        // Single chest (168 high) and double chest (222 high) at 1280×720, GUI scale 3 (480×240).
        for (int imageHeight : new int[] {168, 222, 167, 133}) {
            int left = (480 - W) / 2;
            int top = top(240, imageHeight);
            int title = 60; // "Large Chest" / "Shulker Box" / "Item Hopper" are about 55–60 px
            Box b = SearchFieldLayout.place(Kind.CONTAINER, left, top, W, 8, title);
            assertEquals(Placement.TITLE_ROW, b.getPlacement());
            assertEquals(SearchFieldLayout.MAX_WIDTH, b.getWidth());
            assertEquals(top + 4, b.getY());
            clearOfSlotsAndTitle(b, left, top, 8 + title);
        }
    }

    @Test
    void aLongTitleNarrowsTheFieldThenMovesItAbove() {
        int left = 100;
        int top = 30;
        Box narrowed = SearchFieldLayout.place(Kind.CONTAINER, left, top, W, 8, 90); // "Caixa de Shulker Roxa"
        assertEquals(Placement.TITLE_ROW, narrowed.getPlacement());
        assertEquals(W - 7 - (8 + 90 + 4), narrowed.getWidth());
        clearOfSlotsAndTitle(narrowed, left, top, 98);
        Box above = SearchFieldLayout.place(Kind.CONTAINER, left, top, W, 8, 130); // a long plugin title
        assertEquals(Placement.ABOVE, above.getPlacement());
        clearOfSlotsAndTitle(above, left, top, 138);
        Box collapsed = SearchFieldLayout.place(Kind.CONTAINER, left, 9, W, 8, 130); // double chest at 720p
        assertEquals(Placement.COLLAPSED, collapsed.getPlacement());
        assertEquals(SearchFieldLayout.ICON_SIZE, collapsed.getWidth());
        assertTrue(collapsed.getY() + collapsed.getHeight() <= 9 + SLOT_TOP);
    }

    @Test
    void inventoryGoesAboveOrCollapses() {
        int left = (480 - W) / 2;
        Box above = SearchFieldLayout.place(Kind.INVENTORY, left, top(240, 166), W, 97, 0);
        assertEquals(Placement.ABOVE, above.getPlacement());
        clearOfSlotsAndTitle(above, left, top(240, 166), 0);
        Box collapsed = SearchFieldLayout.place(Kind.INVENTORY, left, 10, W, 97, 0);
        assertEquals(Placement.COLLAPSED, collapsed.getPlacement());
        assertEquals(left + W - 7 - 12, collapsed.getX(), "right end of the title row, after \"Crafting\"");
    }

    @Test
    void expandedFieldStaysInTheTitleRow() {
        Box b = SearchFieldLayout.expanded(100, 9, W);
        assertEquals(Placement.TITLE_ROW, b.getPlacement());
        assertTrue(b.getY() + b.getHeight() <= 9 + SLOT_TOP);
        assertEquals(SearchFieldLayout.MAX_WIDTH, b.getWidth());
    }

    @Test
    void scalesKeepTheRules() {
        // GUI-scaled heights at auto scale: 1080p → 270, 768p → 256, 720p and 1440p → 240; plus forced large scales.
        for (int height : new int[] {270, 256, 240, 216, 180}) {
            for (int imageHeight : new int[] {166, 168, 222}) {
                int top = top(height, imageHeight);
                Kind kind = imageHeight == 166 ? Kind.INVENTORY : Kind.CONTAINER;
                Box b = SearchFieldLayout.place(kind, 40, top, W, kind == Kind.INVENTORY ? 97 : 8, 40);
                clearOfSlotsAndTitle(b, 40, top, kind == Kind.INVENTORY ? 0 : 48);
            }
        }
    }
}
