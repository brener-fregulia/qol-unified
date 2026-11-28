package unified.qol.feature.keyboardmap;

import com.mojang.blaze3d.platform.InputConstants;
import unified.qol.core.SettingsSnapshot;
import unified.qol.feature.keyboardmap.BindingFilter.Visibility;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The keyboard map screen, Concept A of keyboard-map-ux-v1.md. The keyboard and mouse dominate; one panel on the right
 * shows either the action list (Explore) or a card for the selected input or unbound action, with Change, Clear and
 * Reset on each action. The catalog is built on open and after each change, never per frame. Mappings change only
 * through {@link RemapSession}; selecting never writes.
 */
public final class KeyboardMapScreen extends Screen {
    // Approved palette (keyboard-map-ux-v1.md §8.1).
    private static final int PANEL = 0xB4101010;
    private static final int FILL_FREE = 0xCC2A2A2A;
    private static final int FILL_FREE_QUIET = 0x662A2A2A;
    private static final int LINE_FREE = 0xFF3C3C3C;
    private static final int FILL_ONE = 0xD92F5D3A;
    private static final int LINE_ONE = 0xFF3F7A4C;
    private static final int FILL_SHARED = 0xD96B2E2E;
    private static final int LINE_SHARED = 0xFF8A3B3B;
    private static final int SELECTED = 0xFF4FC3F7;
    private static final int HOVER = 0x99FFFFFF;
    private static final int MATCH = 0xFFF0E6A0;
    private static final int BODY = 0xCC1E1E1E;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFF9A9A9A;
    private static final int TEXT_HEADER = 0xFFE8C66A;
    private static final int TEXT_SHARED = 0xFFE59A9A;
    private static final int TEXT_ONE = 0xFFA9D8B4;

    private static final int HEADER_ROW = 13;
    private static final int LIST_ROW = 21;
    private static final int CARD_HEADER = 24;
    private static final int CARD_ROW = 46;
    private static final int TAG_LINES = 3;

    private final Screen parent;
    private final Consumer<String> layoutWriter;
    private final MinecraftBindings source;
    private final RemapSession session;
    private final Selection selection = new Selection();

    private BindingCatalog catalog;
    private String layoutId;
    private String query = "";
    private BindingFilter filter;
    private String category;
    private Visibility visibility;
    private boolean moreKeys;
    private boolean actionsTab;
    private boolean filtersOpen;
    private int listScroll;
    private int cardScroll;

    private KeyboardMapGeometry geometry;
    private KeyboardLayout layout;
    private List<KeyCap> drawnKeys = List.of();
    private int originX;
    private int originY;
    private int drawingBottom;
    private int mouseX0 = -1;
    private int mouseY0;
    private final List<ListRow> rows = new ArrayList<>();
    private final Map<String, Map<String, Button>> rowButtons = new LinkedHashMap<>();
    private InputId hovered;

    private EditBox search;
    private Button filters;

    private record ListRow(Binding binding, String header) {
    }

    /**
     * @param initialLayout layout to show first, or {@code null} for the current setting
     * @param layoutWriter persists a layout chosen in the toolbar (Core configuration, or the settings draft)
     */
    public KeyboardMapScreen(Screen parent, String initialLayout, Consumer<String> layoutWriter) {
        super(Component.translatable(KeyboardMapTexts.TITLE));
        this.parent = parent;
        this.layoutWriter = Objects.requireNonNull(layoutWriter, "layoutWriter");
        this.source = new MinecraftBindings(Minecraft.getInstance().options);
        this.session = new RemapSession(() -> catalog, source);
        SettingsSnapshot settings = settings();
        this.layoutId = initialLayout != null ? initialLayout : settings.getEnum(KeyboardMapSettings.LAYOUT);
        this.visibility = Visibility.byId(settings.getEnum(KeyboardMapSettings.DEFAULT_FILTER));
        this.catalog = source.snapshot();
    }

    private static SettingsSnapshot settings() {
        SettingsSnapshot active = KeyboardMapFeature.activeSettings();
        return active != null ? active : SettingsSnapshot.defaults(KeyboardMapSettings.DESCRIPTOR);
    }

    // ---- Widgets ----

    @Override
    protected void init() {
        catalog = source.snapshot();
        layout = KeyboardLayouts.byId(layoutId);
        geometry = KeyboardMapGeometry.plan(width, height, layout);
        rowButtons.clear();
        int m = geometry.margin();
        int y = geometry.toolbarY();

        // Toolbar, one row: search, Filters, layout | Vanilla Key Binds (secondary), Done.
        // Buttons are sized from their text; short labels are used when the full ones would leave search too narrow.
        Component doneText = Component.translatable(KeyboardMapTexts.DONE);
        Component filtersText = Component.translatable(KeyboardMapTexts.FILTERS);
        Component bindsText = Component.translatable(KeyboardMapTexts.KEY_BINDS);
        boolean compact = width - 2 * m - 16 - buttonWidth(doneText) - buttonWidth(filtersText) - buttonWidth(bindsText)
                - buttonWidth(Component.translatable(layoutKey(KeyboardLayouts.ABNT2))) < 120;
        if (compact) {
            bindsText = Component.translatable(KeyboardMapTexts.KEY_BINDS_SHORT);
        }
        int doneWidth = buttonWidth(doneText);
        int bindsWidth = buttonWidth(bindsText);
        int filtersWidth = buttonWidth(filtersText) + 8;
        int layoutWidth = compact ? buttonWidth(Component.literal("US ANSI"))
                : buttonWidth(Component.translatable(layoutKey(KeyboardLayouts.ABNT2)));
        int searchWidth = Math.clamp(width - 2 * m - doneWidth - bindsWidth - filtersWidth - layoutWidth - 16, 60, 220);
        int x = m;
        search = new EditBox(font, x, y, searchWidth, 20, Component.translatable(KeyboardMapTexts.SEARCH_HINT));
        search.setHint(Component.translatable(KeyboardMapTexts.SEARCH_HINT).withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(64);
        search.setValue(query);
        search.setResponder(this::onSearch);
        addRenderableWidget(search);
        x += searchWidth + 4;
        boolean filtering = category != null || visibility != Visibility.ALL;
        filters = addRenderableWidget(Button.builder(Component.translatable(KeyboardMapTexts.FILTERS)
                        .append(filtering ? " •" : ""), b -> {
                    filtersOpen = !filtersOpen;
                    refreshUi();
                }).bounds(x, y, filtersWidth, 20).build());
        x += filtersWidth + 4;
        CycleButton<String> layoutButton = CycleButton.<String>builder(
                        v -> compact ? Component.literal(KeyboardLayouts.ABNT2.equals(v) ? "ABNT2" : "US ANSI")
                                : Component.translatable(layoutKey(v)), layoutId)
                .withValues(KeyboardLayouts.US_ANSI, KeyboardLayouts.ABNT2).displayOnlyValue()
                .create(x, y, layoutWidth, 20, Component.translatable(KeyboardMapTexts.LAYOUT), (b, v) -> {
                    layoutId = v;
                    layoutWriter.accept(v);
                    refreshUi();
                });
        layoutButton.setTooltip(Tooltip.create(Component.translatable(KeyboardMapTexts.LAYOUT)));
        addRenderableWidget(layoutButton);
        Button done = Button.builder(doneText, b -> onClose())
                .bounds(width - m - doneWidth, y, doneWidth, 20).build();
        Button binds = Button.builder(bindsText, b ->
                        minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options)))
                .bounds(width - m - doneWidth - 4 - bindsWidth, y, bindsWidth, 20).build();
        addRenderableWidget(binds);
        addRenderableWidget(done);

        // Filters popup under its button: category and visibility.
        if (filtersOpen) {
            int px = filters.getX();
            int popupWidth = Math.min(160, width - px - m);
            var categories = new ArrayList<String>();
            categories.add("");
            categories.addAll(catalog.categories());
            if (category != null && !categories.contains(category)) {
                category = null;
            }
            addRenderableWidget(CycleButton.<String>builder(c -> c.isEmpty()
                                    ? Component.translatable(KeyboardMapTexts.ALL_CATEGORIES) : Component.literal(c),
                            category == null ? "" : category)
                    .withValues(categories)
                    .create(px + 4, y + 26, popupWidth - 8, 20, Component.translatable(KeyboardMapTexts.CATEGORY),
                            (b, v) -> {
                                category = v.isEmpty() ? null : v;
                                listScroll = 0;
                                rebuildRows();
                            }));
            addRenderableWidget(CycleButton.<Visibility>builder(v -> Component.translatable(filterKey(v.id())),
                            visibility)
                    .withValues(Visibility.values())
                    .create(px + 4, y + 50, popupWidth - 8, 20, Component.translatable(KeyboardMapTexts.SHOW),
                            (b, v) -> {
                                visibility = v;
                                listScroll = 0;
                                rebuildRows();
                            }));
        }

        // Bottom row: page and tab switches, then the status line.
        int by = geometry.bottomBarY();
        int bx = m;
        if (geometry.split() && controlsVisible()) {
            addRenderableWidget(Button.builder(Component.translatable(moreKeys
                            ? KeyboardMapTexts.MAIN_KEYS : KeyboardMapTexts.MORE_KEYS), b -> {
                        moreKeys = !moreKeys;
                        refreshUi();
                    }).bounds(bx, by, 80, 20).build());
            bx += 84;
        }
        if (geometry.tabbed()) {
            addRenderableWidget(Button.builder(Component.translatable(actionsTab
                            ? KeyboardMapTexts.SHOW_KEYBOARD : KeyboardMapTexts.SHOW_LIST), b -> {
                        actionsTab = !actionsTab;
                        refreshUi();
                    }).bounds(bx, by, 80, 20).build());
        }

        layoutDrawing();
        rebuildRows();
        if (panelVisible() && selection.mode() != Selection.Mode.EXPLORE) {
            addCardWidgets();
        }
    }

    private int buttonWidth(Component text) {
        return font.width(text) + 16;
    }

    private void onSearch(String text) {
        if (text.equals(query)) {
            return;
        }
        query = text;
        listScroll = 0;
        if (selection.mode() != Selection.Mode.EXPLORE) {
            // Typing means looking for something else: back to Explore, search kept (§0).
            selection.clear();
            refreshUi();
        } else {
            rebuildRows();
        }
    }

    /** Re-creates the widgets for a new state, keeping search focus while typing. */
    private void refreshUi() {
        boolean typing = search != null && search.isFocused();
        rebuildWidgets();
        if (typing) {
            setFocused(search);
            search.moveCursorToEnd(false);
        }
    }

    private static String filterKey(String id) {
        return "qol_unified.feature.keyboard_map.setting.default_filter." + id;
    }

    private static String layoutKey(String id) {
        return "qol_unified.feature.keyboard_map.setting.layout." + id;
    }

    private boolean controlsVisible() {
        return !geometry.tabbed() || !actionsTab;
    }

    private boolean panelVisible() {
        return !geometry.tabbed() || actionsTab;
    }

    // ---- Drawing layout ----

    private void layoutDrawing() {
        drawnKeys = layout.keys(geometry.visibleSections(moreKeys));
        int unit = geometry.unit();
        int keysWidth = KeyboardLayout.width(drawnKeys);
        int keysHeight = KeyboardLayout.height(drawnKeys);
        boolean mouse = geometry.mouseVisible(moreKeys);
        boolean beside = geometry.mouseBeside();
        int totalWidth = keysWidth;
        int totalHeight = keysHeight;
        if (mouse && beside) {
            totalWidth += KeyboardMapGeometry.MOUSE_GAP + MouseLayout.WIDTH;
            totalHeight = Math.max(keysHeight, MouseLayout.HEIGHT);
        } else if (mouse) {
            totalWidth = Math.max(keysWidth, MouseLayout.WIDTH);
            totalHeight += KeyboardMapGeometry.MOUSE_GAP + MouseLayout.HEIGHT;
        }
        originX = geometry.keyboardX() + (geometry.keyboardWidth() - totalWidth * unit) / 2;
        // Slightly above center, leaving room under the drawing for the hover label.
        int free = geometry.contentBottom() - geometry.contentTop() - 8 - totalHeight * unit;
        originY = geometry.contentTop() + 4 + Math.max(0, free / 3);
        drawingBottom = originY + totalHeight * unit;
        if (!mouse) {
            mouseX0 = -1;
        } else if (beside) {
            mouseX0 = originX + (keysWidth + KeyboardMapGeometry.MOUSE_GAP) * unit;
            mouseY0 = originY + Math.max(0, keysHeight - MouseLayout.HEIGHT) * unit;
        } else {
            mouseX0 = originX + (keysWidth - MouseLayout.WIDTH) * unit;
            mouseY0 = originY + (keysHeight + KeyboardMapGeometry.MOUSE_GAP) * unit;
        }
    }

    private void rebuildRows() {
        rows.clear();
        var filter = filter();
        String lastCategory = null;
        for (Binding binding : catalog.bindings()) {
            if (!filter.matches(binding, catalog)) {
                continue;
            }
            if (!binding.category().equals(lastCategory)) {
                rows.add(new ListRow(null, binding.category()));
                lastCategory = binding.category();
            }
            rows.add(new ListRow(binding, null));
        }
        listScroll = Math.clamp(listScroll, 0, maxListScroll());
    }

    /** The current filter; remade only when the query, category or visibility changed, so drawing never refolds. */
    private BindingFilter filter() {
        if (filter == null || !filter.sameAs(query, category, visibility)) {
            filter = new BindingFilter(query, category, visibility);
        }
        return filter;
    }

    // ---- Card ----

    private int panelTop() {
        return geometry.contentTop() + 2;
    }

    private int panelBottom() {
        return geometry.contentBottom() - 2;
    }

    private List<Binding> cardBindings() {
        return selection.highlightedBindings(catalog);
    }

    private int cardRowsFit() {
        return Math.max(1, (panelBottom() - panelTop() - CARD_HEADER - 24) / CARD_ROW);
    }

    private void addCardWidgets() {
        int x = geometry.panelX();
        int w = geometry.panelWidth();
        addRenderableWidget(Button.builder(Component.literal("✕"), b -> {
            selection.clear();
            refreshUi();
        }).bounds(x + w - 18, panelTop() + 2, 16, 16)
                .tooltip(Tooltip.create(Component.translatable(KeyboardMapTexts.BACK))).build());
        List<Binding> bindings = cardBindings();
        if (bindings.isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable(KeyboardMapTexts.FIND_UNBOUND), b -> {
                selection.clear();
                visibility = Visibility.UNBOUND;
                category = null;
                listScroll = 0;
                refreshUi();
            }).bounds(x + 4, panelTop() + CARD_HEADER + 26, w - 8, 20).build());
            return;
        }
        cardScroll = Math.clamp(cardScroll, 0, Math.max(0, bindings.size() - cardRowsFit()));
        int buttonWidth = (w - 16) / 3;
        for (int i = 0; i < Math.min(cardRowsFit(), bindings.size() - cardScroll); i++) {
            Binding binding = bindings.get(cardScroll + i);
            String id = binding.id();
            int by = panelTop() + CARD_HEADER + i * CARD_ROW + 22;
            var buttons = new LinkedHashMap<String, Button>();
            boolean capturing = session.state() == RemapSession.State.CAPTURING && id.equals(session.target());
            boolean confirming = session.state() == RemapSession.State.CONFIRMING && id.equals(session.target());
            boolean idle = session.state() == RemapSession.State.IDLE;
            if (confirming) {
                buttons.put(KeyboardMapTexts.CONFIRM, Button.builder(Component.translatable(KeyboardMapTexts.CONFIRM),
                        b -> {
                            session.confirm();
                            afterChange(id);
                        }).bounds(x + 4, by, (w - 12) / 2, 20).build());
                buttons.put(KeyboardMapTexts.CANCEL, Button.builder(Component.translatable(KeyboardMapTexts.CANCEL),
                        b -> {
                            session.cancel();
                            refreshUi();
                        }).bounds(x + 8 + (w - 12) / 2, by, (w - 12) / 2, 20).build());
            } else if (capturing) {
                buttons.put(KeyboardMapTexts.CANCEL, Button.builder(Component.translatable(KeyboardMapTexts.CANCEL),
                        b -> {
                            session.cancel();
                            refreshUi();
                        }).bounds(x + 4, by, w - 8, 20).build());
            } else {
                Button change = Button.builder(Component.translatable(KeyboardMapTexts.CHANGE), b -> {
                    session.begin(id);
                    refreshUi();
                }).bounds(x + 4, by, buttonWidth, 20).build();
                Button clear = Button.builder(Component.translatable(KeyboardMapTexts.CLEAR), b -> {
                    session.clear(id);
                    afterChange(id);
                }).bounds(x + 8 + buttonWidth, by, buttonWidth, 20).build();
                Button reset = Button.builder(Component.translatable(KeyboardMapTexts.RESET), b -> {
                    session.reset(id);
                    afterChange(id);
                }).bounds(x + 12 + 2 * buttonWidth, by, buttonWidth, 20).build();
                change.active = idle;
                clear.active = idle && !binding.unbound();
                reset.active = idle && !binding.isDefault();
                buttons.put(KeyboardMapTexts.CHANGE, change);
                buttons.put(KeyboardMapTexts.CLEAR, clear);
                buttons.put(KeyboardMapTexts.RESET, reset);
            }
            buttons.values().forEach(this::addRenderableWidget);
            rowButtons.put(id, buttons);
        }
    }

    /** After a write: rebuild the catalog and let the card follow the action (§0). */
    private void afterChange(String id) {
        catalog = source.snapshot();
        selection.follow(catalog, id);
        refreshUi();
    }

    // ---- Input ----

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (session.state() == RemapSession.State.CAPTURING) {
            String id = session.target();
            if (event.isEscape()) {
                session.cancel();
                refreshUi();
            } else {
                InputId input = MinecraftBindings.toInput(InputConstants.getKey(event));
                if (input != null && session.offer(input) == RemapSession.Outcome.APPLIED) {
                    afterChange(id);
                } else {
                    refreshUi();
                }
            }
            return true;
        }
        if (session.state() == RemapSession.State.CONFIRMING && event.isEscape()) {
            session.cancel();
            refreshUi();
            return true;
        }
        if (event.hasControlDownWithQuirk() && event.key() == InputConstants.KEY_F) {
            setFocused(search);
            return true;
        }
        if (event.isEscape()) {
            if (filtersOpen) {
                filtersOpen = false;
                refreshUi();
                return true;
            }
            if (search.isFocused() && !search.getValue().isEmpty()) {
                search.setValue("");
                return true;
            }
            if (selection.mode() != Selection.Mode.EXPLORE) {
                selection.clear();
                refreshUi();
                return true;
            }
        }
        if ((event.isUp() || event.isDown()) && !search.isFocused() && selection.mode() == Selection.Mode.EXPLORE) {
            listScroll = Math.clamp(listScroll + (event.isDown() ? LIST_ROW : -LIST_ROW), 0, maxListScroll());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (session.state() == RemapSession.State.CAPTURING) {
            String id = session.target();
            if (session.offer(InputId.mouse(event.button())) == RemapSession.Outcome.APPLIED) {
                afterChange(id);
            } else {
                refreshUi();
            }
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (filtersOpen) {
            filtersOpen = false;
            refreshUi();
            return true;
        }
        if (session.state() != RemapSession.State.IDLE || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (controlsVisible()) {
            InputId input = inputAt(event.x(), event.y());
            if (input != null) {
                selectInput(input);
                return true;
            }
        }
        if (panelVisible() && selection.mode() == Selection.Mode.EXPLORE) {
            Binding binding = rowAt(event.x(), event.y());
            if (binding != null) {
                selectBinding(binding.id());
                return true;
            }
        }
        return false;
    }

    /** Same as clicking a key or mouse region. */
    public void selectInput(InputId input) {
        selection.selectInput(input);
        cardScroll = 0;
        if (geometry.tabbed() && selection.mode() != Selection.Mode.EXPLORE) {
            actionsTab = true;
        }
        refreshUi();
    }

    /** Same as clicking a row in Explore. */
    public void selectBinding(String id) {
        selection.selectBinding(catalog, id);
        List<Binding> on = cardBindings();
        int index = 0;
        for (int i = 0; i < on.size(); i++) {
            if (on.get(i).id().equals(id)) {
                index = i;
            }
        }
        cardScroll = Math.max(0, index - cardRowsFit() + 1);
        if (geometry.tabbed()) {
            actionsTab = true;
        }
        refreshUi();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (panelVisible() && x >= geometry.panelX() && x < geometry.panelX() + geometry.panelWidth()) {
            if (selection.mode() == Selection.Mode.EXPLORE) {
                listScroll = Math.clamp(listScroll - (int) Math.signum(scrollY) * LIST_ROW * 2, 0, maxListScroll());
            } else {
                int max = Math.max(0, cardBindings().size() - cardRowsFit());
                int next = Math.clamp(cardScroll - (int) Math.signum(scrollY), 0, max);
                if (next != cardScroll) {
                    cardScroll = next;
                    refreshUi();
                }
            }
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        session.cancel();
        minecraft.gui.setScreen(parent);
    }

    // ---- Hit testing ----

    private int keyX(KeyCap key) {
        return originX + key.x() * geometry.unit();
    }

    private int keyY(KeyCap key) {
        return originY + key.y() * geometry.unit();
    }

    private InputId inputAt(double mx, double my) {
        int unit = geometry.unit();
        for (KeyCap key : drawnKeys) {
            int x = keyX(key);
            int y = keyY(key);
            if (mx >= x && mx < x + key.width() * unit && my >= y && my < y + key.height() * unit) {
                return InputId.key(key.scancode());
            }
        }
        if (mouseX0 >= 0) {
            return MouseLayout.regionAt((mx - mouseX0) / unit, (my - mouseY0) / unit)
                    .map(MouseLayout.Region::input).orElse(null);
        }
        return null;
    }

    private int maxListScroll() {
        int total = rows.stream().mapToInt(r -> r.header() != null ? HEADER_ROW : LIST_ROW).sum();
        return geometry == null ? 0 : Math.max(0, total - (panelBottom() - panelTop()));
    }

    private Binding rowAt(double mx, double my) {
        if (mx < geometry.panelX() || mx >= geometry.panelX() + geometry.panelWidth() || my < panelTop()
                || my >= panelBottom()) {
            return null;
        }
        int y = panelTop() - listScroll;
        for (ListRow row : rows) {
            int h = row.header() != null ? HEADER_ROW : LIST_ROW;
            if (my >= y && my < y + h) {
                return row.binding();
            }
            y += h;
        }
        return null;
    }

    // ---- Drawing ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        hovered = session.state() == RemapSession.State.IDLE && controlsVisible() && !filtersOpen
                ? inputAt(mouseX, mouseY) : null;
        if (controlsVisible()) {
            g.fill(geometry.keyboardX() - 2, geometry.contentTop() - 2,
                    geometry.keyboardX() + geometry.keyboardWidth() + 2, geometry.contentBottom() + 2, PANEL);
            drawKeyboard(g);
            drawMouse(g);
        }
        if (panelVisible()) {
            g.fill(geometry.panelX() - 2, geometry.contentTop() - 2, geometry.panelX() + geometry.panelWidth() + 2,
                    geometry.contentBottom() + 2, PANEL);
            if (selection.mode() == Selection.Mode.EXPLORE) {
                drawList(g, mouseX, mouseY);
            } else {
                drawCard(g);
            }
        }
        drawStatus(g);
        if (filtersOpen) {
            g.fill(filters.getX(), geometry.toolbarY() + 22, filters.getX() + Math.min(160,
                    width - filters.getX() - geometry.margin()), geometry.toolbarY() + 74, 0xF0101010);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (hovered != null) {
            drawHoverLabel(g, hovered, mouseX, mouseY);
        }
    }

    private void drawKeyboard(GuiGraphicsExtractor g) {
        int unit = geometry.unit();
        for (KeyCap key : drawnKeys) {
            int w = key.width() * unit;
            drawInput(g, InputId.key(key.scancode()), keyX(key), keyY(key), w, key.height() * unit, keyLabel(key, w));
        }
    }

    private void drawMouse(GuiGraphicsExtractor g) {
        if (mouseX0 < 0) {
            return;
        }
        int u = geometry.unit();
        int x = mouseX0 + MouseLayout.BODY_X * u;
        int y = mouseY0 + MouseLayout.BODY_Y * u;
        int w = MouseLayout.BODY_WIDTH * u;
        int h = MouseLayout.BODY_HEIGHT * u;
        int r = Math.max(2, u);
        // Rounded body from three rectangles; the outline follows the same steps.
        g.fill(x + r, y, x + w - r, y + h, BODY);
        g.fill(x, y + r, x + r, y + h - r, BODY);
        g.fill(x + w - r, y + r, x + w, y + h - r, BODY);
        g.horizontalLine(x + r, x + w - r - 1, y, LINE_FREE);
        g.horizontalLine(x + r, x + w - r - 1, y + h - 1, LINE_FREE);
        g.verticalLine(x, y + r, y + h - r - 1, LINE_FREE);
        g.verticalLine(x + w - 1, y + r, y + h - r - 1, LINE_FREE);
        for (MouseLayout.Region region : MouseLayout.REGIONS) {
            int rx = mouseX0 + region.x() * u;
            int ry = mouseY0 + region.y() * u;
            String label = region.button() == MouseLayout.BACK || region.button() == MouseLayout.FORWARD
                    ? String.valueOf(region.button()) : "";
            drawInput(g, region.input(), rx, ry, region.width() * u, region.height() * u, label);
        }
        var wheel = MouseLayout.region(MouseLayout.MIDDLE).orElseThrow();
        int wx = mouseX0 + wheel.x() * u + wheel.width() * u / 2;
        g.verticalLine(wx, mouseY0 + wheel.y() * u + 2, mouseY0 + (wheel.y() + wheel.height()) * u - 3, TEXT_DIM);
    }

    /** Fixed symbol, else the OS name if it fits, else the short name. */
    private String keyLabel(KeyCap key, int width) {
        if (key.fixedLabel() != null) {
            return key.fixedLabel();
        }
        String name = MinecraftBindings.label(InputId.key(key.scancode()));
        String shortName = KeyboardLayouts.shortLabel(key.scancode());
        return font.width(name) <= width - 4 || shortName == null ? name : shortName;
    }

    private void drawInput(GuiGraphicsExtractor g, InputId input, int x, int y, int w, int h, String label) {
        InputState state = catalog.state(input);
        boolean quietFree = !settings().getBoolean(KeyboardMapSettings.SHOW_UNBOUND_KEYS);
        int fill = switch (state) {
            case UNBOUND -> quietFree ? FILL_FREE_QUIET : FILL_FREE;
            case SINGLE -> FILL_ONE;
            case OVERLAP -> FILL_SHARED;
        };
        int line = switch (state) {
            case UNBOUND -> LINE_FREE;
            case SINGLE -> LINE_ONE;
            case OVERLAP -> LINE_SHARED;
        };
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.outline(x, y, w, h, line);
        if (input.equals(selection.highlightedInput(catalog))) {
            g.outline(x - 1, y - 1, w + 2, h + 2, SELECTED);
            g.outline(x, y, w, h, SELECTED);
        } else if (input.equals(hovered)) {
            g.outline(x, y, w, h, HOVER);
        } else if (matchesSearch(input)) {
            g.outline(x, y, w, h, MATCH);
        }
        int color = state == InputState.UNBOUND ? TEXT_DIM : TEXT;
        drawFitted(g, label, x, y, w, h, color);
        if (state == InputState.OVERLAP && w >= 10) {
            // Count badge so a shared key never depends on color alone.
            String count = String.valueOf(catalog.on(input).size());
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(x + w - 2 - font.width(count) * 0.5f, y + 2);
            pose.scale(0.5f, 0.5f);
            g.text(font, count, 0, 0, TEXT);
            pose.popMatrix();
        }
    }

    private void drawFitted(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, int color) {
        if (label.isEmpty()) {
            return;
        }
        int textWidth = font.width(label);
        if (textWidth <= w - 3) {
            g.centeredText(font, label, x + w / 2, y + (h - 7) / 2, color);
            return;
        }
        float scale = Math.max(0.5f, (w - 3) / (float) textWidth);
        String fitted = font.plainSubstrByWidth(label, (int) ((w - 3) / scale));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x + w / 2f, y + h / 2f);
        pose.scale(scale, scale);
        g.centeredText(font, fitted, 0, -4, color);
        pose.popMatrix();
    }

    private boolean matchesSearch(InputId input) {
        BindingFilter filter = filter();
        return !filter.isEmpty() && catalog.on(input).stream().anyMatch(b -> filter.matches(b, catalog));
    }

    private int stateColor(InputState state) {
        return switch (state) {
            case UNBOUND -> LINE_FREE;
            case SINGLE -> 0xFF4E9A62;
            case OVERLAP -> 0xFFB35454;
        };
    }

    /** The state square in front of a row: hollow when unbound. */
    private void drawStateMark(GuiGraphicsExtractor g, Binding binding, int x, int y) {
        InputState state = catalog.state(binding);
        g.fill(x, y, x + 6, y + 6, stateColor(state));
        if (state == InputState.UNBOUND) {
            g.fill(x + 1, y + 1, x + 5, y + 5, 0xFF101010);
        }
    }

    private String inputText(Binding binding) {
        if (binding.unbound()) {
            return Component.translatable(KeyboardMapTexts.UNBOUND).getString();
        }
        boolean drawn = binding.input().kind() == InputId.Kind.MOUSE || layout.contains(binding.input());
        int count = catalog.on(binding.input()).size();
        return binding.inputLabel() + (drawn ? "" : " *") + (count > 1 ? "  ×" + count : "");
    }

    private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = geometry.panelX();
        int w = geometry.panelWidth();
        int top = panelTop();
        int bottom = panelBottom();
        if (rows.isEmpty()) {
            g.text(font, Component.translatable(KeyboardMapTexts.NO_MATCHES), x + 4, top + 4, TEXT_DIM);
            return;
        }
        g.enableScissor(x, top, x + w, bottom);
        int y = top - listScroll;
        for (ListRow row : rows) {
            int h = row.header() != null ? HEADER_ROW : LIST_ROW;
            if (y + h > top && y < bottom) {
                if (row.header() != null) {
                    g.text(font, font.plainSubstrByWidth(row.header(), w - 6), x + 3, y + 3, TEXT_HEADER);
                } else {
                    Binding binding = row.binding();
                    boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h && mouseY < bottom;
                    if (over) {
                        g.fill(x, y, x + w, y + h, 0x26FFFFFF);
                        if (!binding.unbound() && !layout.contains(binding.input())
                                && binding.input().kind() == InputId.Kind.KEYBOARD) {
                            g.setTooltipForNextFrame(font, Component.translatable(KeyboardMapTexts.NOT_ON_LAYOUT),
                                    mouseX, mouseY);
                        }
                    }
                    drawStateMark(g, binding, x + 4, y + 3);
                    g.text(font, font.plainSubstrByWidth(binding.name(), w - 16), x + 14, y + 1, TEXT);
                    InputState state = catalog.state(binding);
                    int color = state == InputState.OVERLAP ? TEXT_SHARED
                            : state == InputState.SINGLE ? TEXT_ONE : TEXT_DIM;
                    g.text(font, font.plainSubstrByWidth(inputText(binding), w - 16), x + 14, y + 11, color);
                }
            }
            y += h;
        }
        g.disableScissor();
    }

    private void drawCard(GuiGraphicsExtractor g) {
        int x = geometry.panelX();
        int w = geometry.panelWidth();
        int top = panelTop();
        List<Binding> bindings = cardBindings();
        String title;
        String subtitle;
        if (selection.mode() == Selection.Mode.BINDING) {
            title = bindings.isEmpty() ? "" : bindings.getFirst().name();
            subtitle = Component.translatable(KeyboardMapTexts.UNBOUND).getString();
        } else {
            InputId input = selection.highlightedInput(catalog);
            title = MinecraftBindings.label(input);
            subtitle = Component.translatable(KeyboardMapTexts.ACTION_COUNT, bindings.size()).getString();
        }
        g.text(font, font.plainSubstrByWidth(title, w - 26), x + 4, top + 3, TEXT);
        g.text(font, font.plainSubstrByWidth(subtitle, w - 26), x + 4, top + 13, TEXT_DIM);
        g.horizontalLine(x + 2, x + w - 3, top + CARD_HEADER - 2, LINE_FREE);
        if (bindings.isEmpty()) {
            g.textWithWordWrap(font, Component.translatable(KeyboardMapTexts.EMPTY_INPUT), x + 4, top + CARD_HEADER + 4,
                    w - 8, TEXT_DIM, false);
            return;
        }
        int rowsShown = Math.min(cardRowsFit(), bindings.size() - cardScroll);
        for (int i = 0; i < rowsShown; i++) {
            Binding binding = bindings.get(cardScroll + i);
            int y = top + CARD_HEADER + i * CARD_ROW;
            if (binding.id().equals(selection.bindingId())) {
                g.fill(x, y - 1, x + 2, y + CARD_ROW - 4, SELECTED);
            }
            drawStateMark(g, binding, x + 5, y + 2);
            g.text(font, font.plainSubstrByWidth(binding.name(), w - 18), x + 15, y, TEXT);
            Component detail;
            int color = TEXT_DIM;
            if (session.state() == RemapSession.State.CAPTURING && binding.id().equals(session.target())) {
                detail = Component.translatable(KeyboardMapTexts.CAPTURING_SHORT);
                color = 0xFF7FD7F7;
            } else if (session.state() == RemapSession.State.CONFIRMING && binding.id().equals(session.target())) {
                detail = Component.translatable(KeyboardMapTexts.ALSO_USED_BY,
                        MinecraftBindings.label(session.pending()),
                        String.join(", ", session.pendingOverlaps().stream().map(Binding::name).toList()));
                color = TEXT_HEADER;
            } else {
                String def = binding.defaultInput() == null
                        ? Component.translatable(KeyboardMapTexts.UNBOUND).getString()
                        : MinecraftBindings.label(binding.defaultInput());
                detail = Component.translatable(KeyboardMapTexts.CARD_DETAIL, binding.category(), def);
            }
            g.text(font, font.plainSubstrByWidth(detail.getString(), w - 18), x + 15, y + 10, color);
        }
        if (cardScroll + rowsShown < bindings.size() || cardScroll > 0) {
            g.text(font, Component.translatable(KeyboardMapTexts.MORE_ACTIONS,
                            bindings.size() - rowsShown), x + 4, top + CARD_HEADER + rowsShown * CARD_ROW, TEXT_DIM);
        }
        if (bindings.size() > 1 && selection.mode() == Selection.Mode.INPUT) {
            int noteY = top + CARD_HEADER + rowsShown * CARD_ROW + 10;
            List<FormattedCharSequence> lines = font.split(Component.translatable(
                    KeyboardMapTexts.OVERLAP_EXPLANATION, bindings.size()), w - 8);
            for (int i = 0; i < lines.size() && noteY + i * 10 < panelBottom() - 8; i++) {
                g.text(font, lines.get(i), x + 4, noteY + i * 10, TEXT_DIM);
            }
        }
    }

    private void drawStatus(GuiGraphicsExtractor g) {
        Component status = switch (session.state()) {
            case CAPTURING -> Component.translatable(KeyboardMapTexts.CAPTURING,
                    catalog.binding(session.target()).map(Binding::name).orElse("")).withStyle(ChatFormatting.AQUA);
            case CONFIRMING -> Component.translatable(KeyboardMapTexts.CONFIRM_HINT).withStyle(ChatFormatting.GOLD);
            case IDLE -> Component.translatable(selection.mode() == Selection.Mode.EXPLORE
                    ? KeyboardMapTexts.LEGEND : KeyboardMapTexts.CARD_HINT);
        };
        int x = geometry.margin();
        for (var child : children()) {
            if (child instanceof Button b && b.getY() == geometry.bottomBarY()) {
                x = Math.max(x, b.getX() + b.getWidth() + 6);
            }
        }
        String text = font.plainSubstrByWidth(status.getString(), width - x - geometry.margin());
        g.text(font, Component.literal(text).withStyle(status.getStyle()), x, geometry.bottomBarY() + 6, TEXT_DIM);
    }

    /**
     * One compact label for the hovered input (§6.3), in the free space under the drawing with one short connector in
     * that space only, so it never covers a key; otherwise a vanilla tooltip.
     */
    private void drawHoverLabel(GuiGraphicsExtractor g, InputId input, int mouseX, int mouseY) {
        List<Binding> on = catalog.on(input);
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(MinecraftBindings.label(input)).withStyle(ChatFormatting.WHITE));
        boolean labels = settings().getBoolean(KeyboardMapSettings.CONTEXTUAL_LABELS);
        if (labels) {
            on.stream().limit(TAG_LINES).forEach(b -> lines.add(Component.literal(b.name())
                    .withStyle(ChatFormatting.GRAY)));
            if (on.size() > TAG_LINES) {
                lines.add(Component.translatable(KeyboardMapTexts.MORE_ACTIONS, on.size() - TAG_LINES)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            if (on.isEmpty()) {
                lines.add(Component.translatable(KeyboardMapTexts.FREE_INPUT).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (input.equals(InputId.mouse(MouseLayout.MIDDLE))) {
            lines.add(Component.translatable(KeyboardMapTexts.WHEEL_NOTE).withStyle(ChatFormatting.DARK_GRAY));
        }
        int maxWidth = Math.min(220, geometry.keyboardWidth() - 8);
        var wrapped = new ArrayList<FormattedCharSequence>();
        for (Component line : lines) {
            wrapped.addAll(font.split(line, maxWidth - 8));
        }
        int boxWidth = Math.min(maxWidth, wrapped.stream().mapToInt(font::width).max().orElse(0) + 8);
        int boxHeight = wrapped.size() * 10 + 4;
        int[] anchor = anchor(input);
        int boxY = drawingBottom + 8;
        boolean fits = labels && anchor != null && boxY + boxHeight <= geometry.contentBottom() - 2;
        if (!fits) {
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        int boxX = Math.clamp(anchor[0] - boxWidth / 2, geometry.keyboardX(),
                geometry.keyboardX() + geometry.keyboardWidth() - boxWidth);
        g.nextStratum();
        g.verticalLine(anchor[0], drawingBottom + 1, boxY, SELECTED);
        g.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xE6101010);
        g.outline(boxX, boxY, boxWidth, boxHeight, on.size() > 1 ? LINE_SHARED : on.size() == 1 ? LINE_ONE : LINE_FREE);
        for (int i = 0; i < wrapped.size(); i++) {
            g.text(font, wrapped.get(i), boxX + 4, boxY + 3 + i * 10, TEXT);
        }
    }

    /** Center x, top y, right x and bottom y of a drawn input, or {@code null}. */
    private int[] anchor(InputId input) {
        int u = geometry.unit();
        if (input.kind() == InputId.Kind.KEYBOARD) {
            for (KeyCap key : drawnKeys) {
                if (key.scancode() == input.code()) {
                    int x = keyX(key);
                    int y = keyY(key);
                    return new int[] {x + key.width() * u / 2, y, x + key.width() * u, y + key.height() * u};
                }
            }
            return null;
        }
        if (mouseX0 < 0) {
            return null;
        }
        return MouseLayout.region(input.code()).map(r -> new int[] {mouseX0 + r.x() * u + r.width() * u / 2,
                mouseY0 + r.y() * u, mouseX0 + (r.x() + r.width()) * u, mouseY0 + (r.y() + r.height()) * u})
                .orElse(null);
    }

    // ---- Read access for development game tests ----

    public BindingCatalog catalog() {
        return catalog;
    }

    public RemapSession session() {
        return session;
    }

    public Selection selection() {
        return selection;
    }

    public KeyboardMapGeometry geometry() {
        return geometry;
    }

    public List<String> visibleRowIds() {
        return rows.stream().map(ListRow::binding).filter(Objects::nonNull).map(Binding::id).toList();
    }

    public List<String> cardBindingIds() {
        return selection.mode() == Selection.Mode.EXPLORE ? List.of()
                : cardBindings().stream().map(Binding::id).toList();
    }

    public Set<KeyCap.Section> drawnSections() {
        return geometry.visibleSections(moreKeys);
    }

    /** A card button ({@link KeyboardMapTexts#CHANGE}, CLEAR, RESET, CONFIRM, CANCEL) of one action, or null. */
    public Button rowButton(String bindingId, String key) {
        return rowButtons.getOrDefault(bindingId, Map.of()).get(key);
    }

    /** Every card button currently shown, for checking that Explore has none. */
    public Set<String> shownRowButtonKeys() {
        var keys = new LinkedHashSet<String>();
        rowButtons.values().forEach(m -> keys.addAll(m.keySet()));
        return keys;
    }

    /** Screen position of a drawn key's or mouse region's center, or {@code null}. */
    public int[] inputCenter(InputId input) {
        int[] a = anchor(input);
        return a == null ? null : new int[] {a[0], (a[1] + a[3]) / 2};
    }
}
