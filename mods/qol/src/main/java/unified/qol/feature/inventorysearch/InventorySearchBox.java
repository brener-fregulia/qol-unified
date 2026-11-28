package unified.qol.feature.inventorysearch;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Vanilla's text field with the match count drawn inside its right end (inventory-search-v1.md §5). The text area is
 * narrowed by the count's width, so typed text never runs under it.
 */
final class InventorySearchBox extends EditBox {
    private static final int COUNT_COLOR = 0xFFA0A0A0;
    private static final int NO_MATCH_COLOR = 0xFFE07070;

    private final Font font;
    private String countText = "";
    private int countColor = COUNT_COLOR;

    InventorySearchBox(Font font, Component narration) {
        super(font, 0, 0, SearchFieldLayout.MAX_WIDTH, SearchFieldLayout.HEIGHT, narration);
        this.font = font;
        setMaxLength(50);
        setHint(Component.translatable(InventorySearchTexts.HINT));
    }

    /** Shows {@code count} matches, or nothing when no query is active. */
    void setCount(int count, boolean active) {
        countText = active ? Integer.toString(count) : "";
        countColor = count == 0 ? NO_MATCH_COLOR : COUNT_COLOR;
        setMessage(active ? Component.translatable(InventorySearchTexts.NARRATION_COUNT, count)
                : Component.translatable(InventorySearchTexts.NARRATION));
    }

    @Override
    public int getInnerWidth() {
        // EditBox's constructor calls this before the fields below are set.
        int count = countText == null || countText.isEmpty() ? 0 : font.width(countText) + 3;
        return Math.max(8, super.getInnerWidth() - count);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
        if (isVisible() && !countText.isEmpty()) {
            int x = getX() + getWidth() - 4 - font.width(countText);
            int y = getY() + (getHeight() - 8) / 2;
            graphics.text(font, countText, x, y, countColor);
        }
    }
}
