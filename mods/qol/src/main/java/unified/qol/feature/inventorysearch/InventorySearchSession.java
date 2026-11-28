package unified.qol.feature.inventorysearch;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import unified.qol.search.SearchQuery;

/**
 * Inventory Search's state for one open container screen (inventory-search-v1.md §6): the field, the query, the slot
 * index and the current matches. It lives in the screen object itself ({@link InventorySearchHolder}), so it is
 * dropped with the screen and the query starts empty every time a container opens. Survives a resize (re-init).
 *
 * <p>It never clicks a slot, sends a packet or changes an item; it only draws over slots and reads keys while its own
 * field has focus.
 */
public final class InventorySearchSession {
    /** Non-matching occupied slots: about 55 % dark, so items stay recognizable. */
    static final int DIM = 0x8C101010;
    /** Matching slots: a 1 px frame inside the slot's 16 × 16 item area; slot borders stay untouched. */
    static final int FRAME = 0xFFFFD24A;
    private static final Identifier SEARCH_ICON = Identifier.withDefaultNamespace("icon/search");

    private final AbstractContainerScreen<?> screen;
    private final SearchFieldLayout.Kind kind;
    private final SlotSearchIndex<ItemStack> index = ItemSearchFields.newIndex();
    private InventorySearchBox field;
    private SpriteIconButton toggle;
    private String text = "";
    private SearchQuery query = SearchQuery.EMPTY;
    private SlotMatches matches;
    /** Opened from the collapsed icon; collapses again when focus leaves an empty field. */
    private boolean expanded;
    private boolean shown;
    /** What layout decided to show; the widgets' own {@code visible} is cleared only during the screen's widget pass. */
    private boolean fieldShown;
    private boolean toggleShown;

    InventorySearchSession(AbstractContainerScreen<?> screen, SearchFieldLayout.Kind kind) {
        this.screen = screen;
        this.kind = kind;
    }

    // ---- Widgets ----

    /** Called after every init (open and resize): the screen dropped its widgets, so they are made again. */
    void attach() {
        Minecraft mc = Screens.getMinecraft(screen);
        boolean wasFocused = field != null && field.isFocused();
        field = new InventorySearchBox(Screens.getFont(screen), Component.translatable(InventorySearchTexts.NARRATION));
        field.setHint(Component.translatable(InventorySearchTexts.HINT).withStyle(ChatFormatting.DARK_GRAY));
        field.setValue(text);
        field.setResponder(this::onText);
        toggle = SpriteIconButton.builder(Component.translatable(InventorySearchTexts.OPEN), b -> expandAndFocus(), true)
                .sprite(SEARCH_ICON, 10, 10).size(SearchFieldLayout.ICON_SIZE, SearchFieldLayout.ICON_SIZE)
                .withTootip().build();
        List<AbstractWidget> widgets = Screens.getWidgets(screen);
        widgets.add(field);
        widgets.add(toggle);
        refresh(mc);
        layout();
        if (wasFocused && shown) {
            focus();
        }
    }

    /** Places the field or the collapsed icon for the current container position (the recipe book moves it). */
    void layout() {
        shown = InventorySearchFeature.isActive();
        var holder = (InventorySearchHolder) screen;
        int left = holder.qolUnified$leftPos();
        int top = holder.qolUnified$topPos();
        int width = holder.qolUnified$imageWidth();
        int titleWidth = kind == SearchFieldLayout.Kind.CONTAINER ? Screens.getFont(screen).width(screen.getTitle()) : 0;
        var box = SearchFieldLayout.place(kind, left, top, width, holder.qolUnified$titleLabelX(), titleWidth);
        boolean collapsed = box.getPlacement() == SearchFieldLayout.Placement.COLLAPSED;
        if (collapsed && (expanded || !text.isEmpty())) {
            box = SearchFieldLayout.expanded(left, top, width);
            collapsed = false;
        }
        field.setRectangle(box.getWidth(), box.getHeight(), box.getX(), box.getY());
        toggle.setPosition(box.getX(), box.getY());
        fieldShown = shown && !collapsed;
        toggleShown = shown && collapsed;
        field.visible = fieldShown;
        toggle.visible = toggleShown;
        if (!fieldShown && field.isFocused()) {
            unfocus();
        }
    }

    // ---- Index ----

    /** Once per client tick: follows slot changes, the language and the feature switch. */
    void tick() {
        Minecraft mc = Screens.getMinecraft(screen);
        if (shown != InventorySearchFeature.isActive()) {
            layout();
        }
        if (shown) {
            refresh(mc);
            layout(); // the recipe book can move the container between ticks
        }
    }

    private void refresh(Minecraft mc) {
        if (!InventorySearchFeature.isActive()) {
            return;
        }
        List<Slot> slots = screen.getMenu().slots;
        index.refresh(slots.size(), mc.getLanguageManager().getSelected(), i -> {
            ItemStack stack = slots.get(i).getItem();
            return stack.isEmpty() ? null : stack;
        });
        updateMatches();
    }

    private void onText(String value) {
        text = value;
        SearchQuery next = SearchQuery.of(value);
        if (!next.equals(query)) {
            query = next;
            refresh(Screens.getMinecraft(screen));
        }
    }

    private void updateMatches() {
        matches = index.matches(query);
        field.setCount(matches.count(), matches.active());
    }

    // ---- Input ----

    /** Fabric's allowKeyPress: false consumes the key, so vanilla never sees it. */
    boolean allowKey(KeyEvent event) {
        if (!shown) {
            return true;
        }
        if (field.isFocused()) {
            int key = event.key();
            if (event.isEscape() || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                unfocus();
                return false;
            }
            field.keyPressed(event); // editing keys; printable text arrives through charTyped
            return false; // E, Q, F, 1–9 and every other hotkey type text instead of acting
        }
        if (event.key() == InputConstants.KEY_F && event.hasControlDownWithQuirk() && !InventorySearchHooks.recipeViewerPresent()) {
            expandAndFocus();
            return false; // never reaches vanilla's offhand swap (KeyMapping.matches ignores modifiers)
        }
        return true;
    }

    /** Fabric's beforeMouseClick: a click anywhere but the field gives the keyboard back to the inventory. */
    void beforeClick(MouseButtonEvent event) {
        if (field.isFocused() && !field.isMouseOver(event.x(), event.y())) {
            unfocus();
        }
    }

    private void expandAndFocus() {
        expanded = true;
        layout();
        focus();
    }

    private void focus() {
        if (!field.visible) {
            return;
        }
        screen.setFocused(field);
        field.setFocused(true);
    }

    private void unfocus() {
        field.setFocused(false);
        if (screen.getFocused() == field) {
            screen.setFocused(null);
        }
        if (text.isEmpty() && expanded) {
            expanded = false;
            layout();
        }
    }

    // ---- Drawing ----

    /**
     * Before the screen draws: vanilla draws container labels after its widgets, so a field over the title row (the
     * expanded icon) would be written over. The two widgets skip the screen's widget pass and are drawn by
     * {@link #drawWidgets} after the labels and slots instead. They are visible again for input right after.
     */
    void beforeScreenDraw() {
        field.visible = false;
        toggle.visible = false;
    }

    /** From ContainerScreenSearchMixin at the end of extractContents: after labels and slots, before tooltips. */
    public void drawWidgets(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        field.visible = fieldShown;
        toggle.visible = toggleShown;
        field.extractRenderState(graphics, mouseX, mouseY, a);
        toggle.extractRenderState(graphics, mouseX, mouseY, a);
    }

    /** From ContainerScreenSearchMixin, inside the container's translated pose, after vanilla drew the slots. */
    public void drawSlots(GuiGraphicsExtractor graphics, List<Slot> slots) {
        SlotMatches current = matches;
        if (!shown || current == null || !current.active()) {
            return;
        }
        for (int i = 0, n = slots.size(); i < n; i++) {
            Slot slot = slots.get(i);
            if (!slot.isActive()) {
                continue;
            }
            int x = slot.x;
            int y = slot.y;
            if (current.isDimmed(i)) {
                graphics.fill(x, y, x + 16, y + 16, DIM);
            } else if (current.isMatch(i)) {
                graphics.fill(x, y, x + 16, y + 1, FRAME);
                graphics.fill(x, y + 15, x + 16, y + 16, FRAME);
                graphics.fill(x, y + 1, x + 1, y + 15, FRAME);
                graphics.fill(x + 15, y + 1, x + 16, y + 15, FRAME);
            }
        }
    }

    // ---- State for tests ----

    public String text() {
        return text;
    }

    public boolean focused() {
        return field.isFocused();
    }

    public boolean fieldVisible() {
        return field.visible;
    }

    public boolean iconVisible() {
        return toggle.visible;
    }

    public AbstractWidget fieldWidget() {
        return field;
    }

    /** The current matches; null before the first refresh or while the feature is off. */
    public SlotMatches matches() {
        return matches;
    }

    public SlotSearchIndex<ItemStack> index() {
        return index;
    }
}
