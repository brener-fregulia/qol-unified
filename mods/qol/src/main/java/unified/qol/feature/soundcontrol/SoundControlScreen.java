package unified.qol.feature.soundcontrol;

import unified.qol.platform.ModNames;
import unified.qol.search.SearchQuery;
import unified.qol.ui.DraftScreen;
import unified.qol.ui.SettingsDraft;
import unified.qol.ui.UiKeys;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/**
 * The sound list (sound-control-v1.md §6): Recent, Rules and All sounds tabs, search, filters, and per-sound volume,
 * mute and reset. Edits change a working copy and are audible at once through the live preview (§16), which writes
 * nothing. Done writes the copy to the draft and saves and applies it, whether opened from vanilla's Music &amp; Sound
 * options ({@code standalone}, Q-SC4) or from the auto-applying QoL settings workspace (settings-ux-v1.md §18).
 * Cancel and Escape drop the copy and restore the previous audible state.
 */
final class SoundControlScreen extends Screen implements DraftScreen {
    enum Tab {
        RECENT, RULES, ALL
    }

    private static final int M = 8;
    private static final int ROW_HEIGHT = 24;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int GREY = 0xFFA0A0A0;
    private static final int MUTED_COLOR = 0xFFFFC060;
    private static final int ERROR = 0xFFFF7070;

    /** Name, namespace and mod of one known event; the category comes from the recent history. */
    private record Base(String id, String name, String namespace, String modName) {
    }

    private final Screen parent;
    private final SettingsDraft draft;
    private final boolean standalone;
    private final Map<String, SoundRule> working;
    private final Map<String, Base> catalog = new HashMap<>();
    /** Prepared search candidates by sound id; see {@link #candidate(Base)}. The language cannot change while open. */
    private final Map<String, SoundSearch.Candidate> preparedCandidates = new HashMap<>();
    private final Map<String, String> categoryLabels = new HashMap<>();
    private final Set<String> installed = new HashSet<>();
    private Map<String, RecentSounds.Entry<Identifier, SoundSource>> recentById = Map.of();
    private List<String> recentOrder = List.of();

    private Tab tab = Tab.RECENT;
    private String query = "";
    private String categoryFilter;
    private String namespaceFilter;
    private Component info = Component.empty();
    private Component status = Component.empty();
    private boolean statusError;
    private int ticks;

    private SoundList list;
    private EditBox search;
    private boolean focusSearch;
    private Button emptyAction;
    private final Button[] tabButtons = new Button[3];
    private int listTop;
    private int listBottom;

    SoundControlScreen(Screen parent, SettingsDraft draft, boolean standalone) {
        super(Component.translatable(SoundControlTexts.TITLE));
        this.parent = parent;
        this.draft = draft;
        this.standalone = standalone;
        this.working = new TreeMap<>(SoundRules.read(
                draft.choice(SoundControlSettings.ID).settings().get(SoundControlSettings.RULES)));
        if (!draft.editable()) {
            setStatus(Component.translatable(UiKeys.MESSAGE_READ_ONLY), true);
        }
        SoundControlHooks.preview(draft, working);
    }

    @Override
    public SettingsDraft draft() {
        return draft;
    }

    // ---- Layout ----

    @Override
    protected void init() {
        if (catalog.isEmpty()) {
            buildCatalog();
        }
        refreshRecent();
        int y = 22;
        boolean wide = width >= 420;
        int tabWidth = wide ? 84 : (width - 2 * M - 8) / 3;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            tabButtons[i] = addRenderableWidget(Button.builder(tabLabel(t), b -> selectTab(t))
                    .bounds(M + i * (tabWidth + 4), y, tabWidth, 20).build());
            tabButtons[i].active = t != tab;
        }
        int searchX = wide ? M + 3 * (tabWidth + 4) : M;
        if (!wide) {
            y += 24;
        }
        search = new EditBox(font, searchX, y, width - M - searchX, 20,
                Component.translatable(SoundControlTexts.SEARCH_HINT));
        search.setHint(Component.translatable(SoundControlTexts.SEARCH_HINT).withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(64);
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            rebuildRows();
        });
        addRenderableWidget(search);
        y += 24;

        int filterWidth = (width - 2 * M - 8) / 3;
        var categories = new ArrayList<String>();
        categories.add("");
        for (SoundSource source : SoundSource.values()) {
            if (source != SoundSource.MASTER) {
                categories.add(source.getName());
            }
        }
        addRenderableWidget(CycleButton.<String>builder(c -> c.isEmpty()
                        ? Component.translatable(SoundControlTexts.FILTER_ANY)
                        : Component.translatable("soundCategory." + c), categoryFilter == null ? "" : categoryFilter)
                .withValues(categories)
                .create(M, y, filterWidth, 20, Component.translatable(SoundControlTexts.FILTER_CATEGORY), (b, v) -> {
                    categoryFilter = v.isEmpty() ? null : v;
                    rebuildRows();
                }));
        var namespaces = new ArrayList<String>();
        namespaces.add("");
        var sorted = new TreeSet<String>((a, b) -> {
            int byName = String.CASE_INSENSITIVE_ORDER.compare(ModNames.displayName(a), ModNames.displayName(b));
            return byName != 0 ? byName : a.compareTo(b);
        });
        catalog.values().forEach(base -> sorted.add(base.namespace()));
        namespaces.addAll(sorted);
        if (namespaceFilter != null && !namespaces.contains(namespaceFilter)) {
            namespaceFilter = null;
        }
        addRenderableWidget(CycleButton.<String>builder(n -> n.isEmpty()
                        ? Component.translatable(SoundControlTexts.FILTER_ANY)
                        : Component.literal(ModNames.displayName(n)), namespaceFilter == null ? "" : namespaceFilter)
                .withValues(namespaces)
                .create(M + filterWidth + 4, y, filterWidth, 20, Component.translatable(SoundControlTexts.FILTER_MOD),
                        (b, v) -> {
                            namespaceFilter = v.isEmpty() ? null : v;
                            rebuildRows();
                        }));
        if (tab == Tab.RULES) {
            Button resetAll = addRenderableWidget(Button.builder(Component.translatable(SoundControlTexts.RESET_ALL),
                    b -> confirmResetAll()).bounds(M + 2 * (filterWidth + 4), y, filterWidth, 20).build());
            resetAll.active = draft.editable() && !working.isEmpty();
        }
        y += 24;

        listTop = y + font.lineHeight + 3;
        listBottom = Math.max(listTop + ROW_HEIGHT, height - 52);
        list = addRenderableWidget(new SoundList(minecraft, width, listBottom - listTop, listTop));
        int actionWidth = Math.min(200, width - 2 * M);
        emptyAction = addRenderableWidget(Button.builder(
                        Component.translatable(SoundControlTexts.EMPTY_RECENT_ACTION), b -> selectTab(Tab.ALL))
                .bounds((width - actionWidth) / 2, (listTop + listBottom) / 2 + 12, actionWidth, 20).build());

        int buttonWidth = Math.min(100, (width - 2 * M - 4) / 2);
        addRenderableWidget(Button.builder(Component.translatable(UiKeys.DONE), b -> done())
                .bounds(width / 2 - buttonWidth - 2, height - 24, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(UiKeys.CANCEL), b -> onClose())
                .bounds(width / 2 + 2, height - 24, buttonWidth, 20).build());
        setInitialFocus(search);
        rebuildRows();
    }

    private Component tabLabel(Tab t) {
        return switch (t) {
            case RECENT -> Component.translatable(SoundControlTexts.TAB_RECENT);
            case RULES -> Component.translatable(SoundControlTexts.TAB_RULES, working.size());
            case ALL -> Component.translatable(SoundControlTexts.TAB_ALL);
        };
    }

    private void selectTab(Tab t) {
        tab = t;
        rebuildWidgets();
        // Vanilla focuses the clicked (now removed) button after onPress returns; give focus back to the search box
        // on the next tick, so typing keeps searching.
        focusSearch = true;
    }

    // ---- Data ----

    private void buildCatalog() {
        SoundManager sounds = minecraft.getSoundManager();
        for (Identifier id : sounds.getAvailableSounds()) {
            installed.add(id.toString());
            catalog.put(id.toString(), base(id, sounds.getSoundEvent(id)));
        }
    }

    private static Base base(Identifier id, WeighedSoundEvents events) {
        Component subtitle = events == null ? null : events.getSubtitle();
        String name = subtitle != null && ComponentUtils.isTranslationResolvable(subtitle)
                ? subtitle.getString() : id.getPath();
        return new Base(id.toString(), name, id.getNamespace(), ModNames.displayName(id.getNamespace()));
    }

    /** Takes a fresh copy of the history; heard events and rules for ids not in the catalog get an entry. */
    private void refreshRecent() {
        var byId = new HashMap<String, RecentSounds.Entry<Identifier, SoundSource>>();
        var order = new ArrayList<String>();
        for (var entry : SoundControlHooks.recent()) {
            String id = entry.id().toString();
            byId.put(id, entry);
            order.add(id);
            catalog.computeIfAbsent(id, k -> base(entry.id(), null));
        }
        recentById = byId;
        recentOrder = order;
        for (String id : working.keySet()) {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed != null) {
                catalog.computeIfAbsent(id, k -> base(parsed, null));
            }
        }
    }

    /**
     * The sound's search candidate. Candidates and their folded text are kept for the screen's lifetime and remade only
     * when the sound's last-heard category changes, so typing filters prepared text instead of refolding every sound.
     */
    private SoundSearch.Candidate candidate(Base base) {
        var heard = recentById.get(base.id());
        String category = heard == null ? null : heard.category().getName();
        SoundSearch.Candidate cached = preparedCandidates.get(base.id());
        if (cached != null && Objects.equals(cached.category(), category)) {
            return cached;
        }
        String label = category == null ? null
                : categoryLabels.computeIfAbsent(category,
                        c -> Component.translatable("soundCategory." + c).getString());
        var made = new SoundSearch.Candidate(base.id(), base.name(), base.namespace(), base.modName(), category, label);
        preparedCandidates.put(base.id(), made);
        return made;
    }

    private void rebuildRows() {
        if (list == null) {
            return;
        }
        var candidates = new ArrayList<SoundSearch.Candidate>();
        SearchQuery searchQuery = SearchQuery.of(query);
        boolean filtering = !searchQuery.isEmpty() || categoryFilter != null || namespaceFilter != null;
        switch (tab) {
            case RECENT -> {
                for (String id : recentOrder) {
                    var c = candidate(catalog.get(id));
                    if (SoundSearch.matches(c, searchQuery, categoryFilter, namespaceFilter)) {
                        candidates.add(c);
                    }
                }
            }
            case RULES -> {
                for (String id : working.keySet()) {
                    Base base = catalog.get(id);
                    if (base != null) {
                        var c = candidate(base);
                        if (SoundSearch.matches(c, searchQuery, categoryFilter, namespaceFilter)) {
                            candidates.add(c);
                        }
                    }
                }
                candidates.sort(SoundSearch.BY_NAME);
            }
            case ALL -> {
                if (SoundSearch.listsAll(query, categoryFilter, namespaceFilter)) {
                    for (Base base : catalog.values()) {
                        var c = candidate(base);
                        if (SoundSearch.matches(c, searchQuery, categoryFilter, namespaceFilter)) {
                            candidates.add(c);
                        }
                    }
                    candidates.sort(SoundSearch.BY_NAME);
                }
            }
        }
        int total = candidates.size();
        List<SoundSearch.Candidate> shown = tab == Tab.ALL && total > SoundSearch.MAX_ROWS
                ? candidates.subList(0, SoundSearch.MAX_ROWS) : candidates;
        var rows = new ArrayList<SoundEntry>(shown.size());
        for (var c : shown) {
            rows.add(new SoundEntry(c));
        }
        list.replaceEntries(rows);
        list.setScrollAmount(0);

        if (tab == Tab.ALL && !SoundSearch.listsAll(query, categoryFilter, namespaceFilter)) {
            info = Component.translatable(SoundControlTexts.ALL_HINT, installed.size());
        } else if (total > shown.size()) {
            info = Component.translatable(SoundControlTexts.COUNT_LIMITED, shown.size(), total);
        } else {
            info = Component.translatable(SoundControlTexts.COUNT, total);
        }
        emptyAction.visible = tab == Tab.RECENT && recentOrder.isEmpty() && !filtering
                && SoundControlHooks.audioAvailable();
    }

    private Component emptyText() {
        boolean filtering = !query.isBlank() || categoryFilter != null || namespaceFilter != null;
        return switch (tab) {
            case RECENT -> !SoundControlHooks.audioAvailable() ? Component.translatable(SoundControlTexts.NO_AUDIO)
                    : filtering ? Component.translatable(SoundControlTexts.NO_MATCH, query.strip())
                    : Component.translatable(SoundControlTexts.EMPTY_RECENT);
            case RULES -> filtering ? Component.translatable(SoundControlTexts.NO_MATCH, query.strip())
                    : Component.translatable(SoundControlTexts.EMPTY_RULES);
            case ALL -> SoundSearch.listsAll(query, categoryFilter, namespaceFilter)
                    ? Component.translatable(SoundControlTexts.NO_MATCH, query.strip()) : Component.empty();
        };
    }

    private SoundRule rule(String id) {
        return working.getOrDefault(id, SoundRule.DEFAULT);
    }

    /** Applies an edit to the working copy; false when it would exceed the rule limit (Q-SC6). */
    private boolean setRule(String id, SoundRule rule) {
        if (rule.isDefault()) {
            working.remove(id);
        } else {
            if (!working.containsKey(id) && working.size() >= SoundControlSettings.MAX_RULES) {
                setStatus(Component.translatable(SoundControlTexts.LIMIT, SoundControlSettings.MAX_RULES), true);
                return false;
            }
            working.put(id, rule);
        }
        SoundControlHooks.previewRule(id, rule);
        tabButtons[Tab.RULES.ordinal()].setMessage(tabLabel(Tab.RULES));
        return true;
    }

    private void confirmResetAll() {
        minecraft.gui.setScreen(new ResetAllConfirm(yes -> {
            if (yes) {
                working.clear();
                SoundControlHooks.preview(draft, working);
            }
            minecraft.gui.setScreen(this);
        }, Component.translatable(SoundControlTexts.RESET_ALL_TITLE),
                Component.translatable(SoundControlTexts.RESET_ALL_MESSAGE, working.size()), draft));
    }

    /** The confirmation belongs to the same draft, so the preview continues while it is shown. */
    private static final class ResetAllConfirm extends ConfirmScreen implements DraftScreen {
        private final SettingsDraft draft;

        ResetAllConfirm(it.unimi.dsi.fastutil.booleans.BooleanConsumer callback, Component title, Component message,
                SettingsDraft draft) {
            super(callback, title, message);
            this.draft = draft;
        }

        @Override
        public SettingsDraft draft() {
            return draft;
        }
    }

    // ---- Done / Cancel ----

    private void done() {
        if (!draft.editable()) {
            onClose();
            return;
        }
        draft.setValue(SoundControlSettings.ID, SoundControlSettings.RULES, SoundRules.write(working));
        // Done saves and applies directly, also when opened from the settings workspace, which no longer has a
        // shared confirmation (settings-ux-v1.md §18): returning to it needs no second Done.
        SettingsDraft.CommitOutcome outcome = draft.commit();
        switch (outcome.kind()) {
            case NO_CHANGE, APPLIED -> {
                // Saved and applied: publish the committed rules now, so ending the preview changes nothing audible.
                SoundControlHooks.syncNow();
                SoundControlHooks.endPreview();
                minecraft.gui.setScreen(parent);
            }
            case INVALID -> setStatus(Component.translatable(UiKeys.MESSAGE_INVALID), true);
            case READ_ONLY -> setStatus(Component.translatable(UiKeys.MESSAGE_READ_ONLY), true);
            case SAVE_FAILED -> setStatus(Component.translatable(UiKeys.MESSAGE_SAVE_FAILED), true);
            case APPLIED_WITH_FAILURES -> setStatus(Component.translatable(UiKeys.MESSAGE_APPLY_FAILED,
                    Component.translatable(SoundControlSettings.DESCRIPTOR.nameKey())), true);
        }
    }

    /**
     * Cancel and Escape: the working copy is dropped; nothing reaches the draft or the file. The audible state returns
     * to the committed rules (standalone) or to the parent draft's value (opened from the QoL settings).
     */
    @Override
    public void onClose() {
        if (standalone) {
            SoundControlHooks.endPreview();
        } else {
            SoundControlHooks.previewDraft(draft);
        }
        minecraft.gui.setScreen(parent);
    }

    private void setStatus(Component text, boolean error) {
        status = text;
        statusError = error;
    }

    @Override
    public void tick() {
        // The history is copied once per second so "last heard" stays current; rows are not reordered under the
        // cursor. Switching tabs or searching shows newly heard sounds.
        if (++ticks % 20 == 0) {
            refreshRecent();
        }
        if (focusSearch) {
            focusSearch = false;
            setFocused(search);
        }
    }

    // ---- Rendering ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, title, width / 2, 8, TEXT);
        g.text(font, fit(info.getString(), width - 2 * M), M, listTop - font.lineHeight - 2, GREY);
        if (list.children().isEmpty()) {
            Component empty = emptyText();
            int y = (listTop + listBottom) / 2 - 10;
            for (var line : font.split(empty, Math.max(40, width - 4 * M))) {
                g.centeredText(font, line, width / 2, y, GREY);
                y += font.lineHeight + 1;
            }
        }
        Component footer = status.getString().isEmpty() ? Component.translatable(SoundControlTexts.NOTE) : status;
        int color = status.getString().isEmpty() ? GREY : statusError ? ERROR : TEXT;
        int y = height - 48;
        for (var line : font.split(FormattedText.of(footer.getString()), width - 2 * M).stream().limit(2).toList()) {
            g.centeredText(font, line, width / 2, y, color);
            y += font.lineHeight + 1;
        }
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private Component heardText(RecentSounds.Entry<Identifier, SoundSource> heard) {
        long seconds = Math.max(0, (System.nanoTime() / 1_000_000L - heard.lastHeardMillis()) / 1000);
        Component ago = seconds < 60 ? Component.translatable(SoundControlTexts.HEARD_SECONDS, seconds)
                : seconds < 3600 ? Component.translatable(SoundControlTexts.HEARD_MINUTES, seconds / 60)
                : Component.translatable(SoundControlTexts.HEARD_HOURS, seconds / 3600);
        String count = heard.count() > 999 ? "999+" : String.valueOf(heard.count());
        return Component.empty().append(ago).append(" · ")
                .append(Component.translatable(SoundControlTexts.HEARD_COUNT, count));
    }

    // ---- List ----

    private final class SoundList extends ContainerObjectSelectionList<SoundEntry> {
        SoundList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, ROW_HEIGHT);
        }

        @Override
        public int getRowWidth() {
            return Math.max(200, Math.min(560, getWidth() - 24));
        }
    }

    private final class VolumeSlider extends AbstractSliderButton {
        private final String id;

        VolumeSlider(String id, int width) {
            super(0, 0, width, 20, Component.empty(), rule(id).volume() / 100.0);
            this.id = id;
            updateMessage();
        }

        int volume() {
            return (int) Math.round(value * 100);
        }

        void show(int volume) {
            value = volume / 100.0;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(SoundControlTexts.VOLUME, volume()));
        }

        @Override
        protected void applyValue() {
            if (!setRule(id, rule(id).withVolume(volume()))) {
                show(rule(id).volume());
            }
        }

        // Arrow keys move by exactly 1%.
        @Override
        public boolean keyPressed(KeyEvent event) {
            if (event.isLeft() || event.isRight()) {
                int next = Math.clamp(volume() + (event.isLeft() ? -1 : 1), 0, 100);
                setValue(next / 100.0);
                return true;
            }
            return super.keyPressed(event);
        }
    }

    private final class SoundEntry extends ContainerObjectSelectionList.Entry<SoundEntry> {
        private final SoundSearch.Candidate sound;
        private final VolumeSlider slider;
        private final Button mute;
        private final Button reset;
        private final List<AbstractWidget> widgets;

        SoundEntry(SoundSearch.Candidate sound) {
            this.sound = sound;
            int rowWidth = list.getRowWidth();
            this.slider = new VolumeSlider(sound.id(), Math.clamp(rowWidth / 3, 70, 120));
            int muteWidth = Math.max(font.width(Component.translatable(SoundControlTexts.MUTE)),
                    font.width(Component.translatable(SoundControlTexts.UNMUTE))) + 10;
            this.mute = Button.builder(muteLabel(), b -> toggleMute()).size(Math.max(40, muteWidth), 20).build();
            Component resetLabel = Component.translatable(SoundControlTexts.RESET);
            this.reset = Button.builder(resetLabel, b -> resetRule())
                    .size(Math.max(20, font.width(resetLabel) + 10), 20).tooltip(Tooltip.create(Component.translatable(SoundControlTexts.RESET_TOOLTIP)))
                    .build();
            this.widgets = List.of(slider, mute, reset);
            boolean editable = draft.editable();
            widgets.forEach(w -> w.active = editable);
            updateState();
        }

        private Component muteLabel() {
            return Component.translatable(rule(sound.id()).muted() ? SoundControlTexts.UNMUTE : SoundControlTexts.MUTE);
        }

        private void toggleMute() {
            SoundRule current = rule(sound.id());
            setRule(sound.id(), current.withMuted(!current.muted()));
            updateState();
        }

        private void resetRule() {
            setRule(sound.id(), SoundRule.DEFAULT);
            slider.show(100);
            updateState();
        }

        private void updateState() {
            boolean muted = rule(sound.id()).muted();
            mute.setMessage(muteLabel());
            mute.setTooltip(Tooltip.create(Component.translatable(muted ? SoundControlTexts.UNMUTE_TOOLTIP
                    : SoundControlTexts.MUTE_TOOLTIP)));
            reset.active = draft.editable() && !rule(sound.id()).isDefault();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
                float partialTick) {
            int right = getContentRight();
            for (int i = widgets.size() - 1; i >= 0; i--) {
                AbstractWidget widget = widgets.get(i);
                right -= widget.getWidth();
                widget.setX(right);
                widget.setY(getContentY() + (getContentHeight() - widget.getHeight()) / 2);
                widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
                right -= 2;
            }
            int textWidth = Math.max(20, right - getContentX() - 4);
            Font f = font;
            int top = getContentY() + (getContentHeight() - 2 * f.lineHeight - 1) / 2;
            String name = sound.name();
            boolean muted = rule(sound.id()).muted();
            String badge = muted ? " " + Component.translatable(SoundControlTexts.MUTED).getString() : "";
            String fittedName = fit(name, textWidth - f.width(badge));
            graphics.text(f, fittedName, getContentX(), top, TEXT);
            if (muted) {
                graphics.text(f, badge, getContentX() + f.width(fittedName), top, MUTED_COLOR);
            }
            graphics.text(f, fit(detail(), textWidth), getContentX(), top + f.lineHeight + 1, GREY);
            if (hovered && mouseX < right) {
                graphics.setTooltipForNextFrame(f, Component.literal(name + "\n" + detail()), mouseX, mouseY);
            }
        }

        private String detail() {
            var parts = new ArrayList<String>();
            parts.add(sound.id());
            if (sound.categoryLabel() != null) {
                parts.add(sound.categoryLabel());
            }
            parts.add(sound.modName());
            if (!installed.contains(sound.id())) {
                parts.add(Component.translatable(SoundControlTexts.NOT_INSTALLED).getString());
            }
            var heard = recentById.get(sound.id());
            if (heard != null) {
                parts.add(heardText(heard).getString());
            }
            return String.join(" · ", parts);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return widgets;
        }
    }
}
