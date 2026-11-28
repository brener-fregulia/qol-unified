package unified.qol.dev;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.mixin.client.gametest.input.KeyboardHandlerAccessor;
import net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unified.qol.QolUnifiedClient;
import unified.qol.core.CoreRuntime;
import unified.qol.core.FeatureState;
import unified.qol.feature.inventorysearch.InventorySearchHolder;
import unified.qol.feature.inventorysearch.InventorySearchSession;
import unified.qol.feature.inventorysearch.ItemSearchFields;
import unified.qol.feature.inventorysearch.SlotMatches;
import unified.qol.mixin.ContainerScreenSlotsAccessor;
import unified.qol.search.SearchQuery;

/**
 * Development-only client game test for Inventory Search (inventory-search-v1.md §11), in a disposable singleplayer
 * world inside {@code build/run-gametest-inventorysearch}. Keys are sent through the keyboard handler (with the Ctrl
 * modifier where needed), clicks and drags through the mouse handler, so Fabric's screen events and vanilla's own
 * handling run exactly as for a player. After every inventory interaction the client's menu is compared with the
 * server's.
 */
final class InventorySearchGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified_dev");
    private static final String ID = "inventory_search";
    private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    private static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    private static final String BLOCK = "0 100 2";
    private static final String BLOCK2 = "1 100 2";

    private final ClientGameTestContext ctx;
    private TestServerContext server;
    private final Map<Integer, double[]> centers = new HashMap<>();

    InventorySearchGameTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
    }

    void run() {
        on(core -> check(core.lifecycle().state(ID) == FeatureState.ACTIVE, "enabled by default"));
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty peaceful");
            server.runCommand("gamerule randomTickSpeed 0");
            server.runCommand("fill -3 99 -3 3 99 5 stone");
            server.runCommand("fill -3 100 -3 3 103 5 air");
            server.runCommand("tp @a 0.5 100 0.5 0 35");
            guiScale(2);
            playerInventory();
            keysWhileFocused();
            escapeAndReset();
            recipeBook();
            singleChest();
            doubleChestAndPerformance();
            otherContainers();
            mouseTweaksWithActiveQuery();
            disabled();
            narrowScales();
            portuguese();
        }
    }

    // ---- Scenarios ----

    /** Player inventory: field above the container, Ctrl+F, name / id / enchantment matches, frames and dimming. */
    private void playerInventory() {
        stockPlayer();
        openInventory();
        InventorySearchSession s = session();
        check(s != null && visible(), "field on the player inventory");
        check(fieldY() < topPos(), "above the container (its title row holds the player model)");
        check(!matches().active(), "empty query: vanilla look");
        shot("01-inventory-empty");
        ctrlF();
        check(focused(), "Ctrl+F focuses the field");
        type("diamond");
        SlotMatches m = matches();
        check(m.count() == 1 && m.isMatch(9), "diamond finds the sword in slot 9, count " + m.count());
        check(m.isDimmed(36), "stone (hotbar) dimmed");
        check(!m.isDimmed(10 + 5), "an empty slot is not dimmed");
        shot("02-inventory-diamond");
        clearField();
        type("minecraft:diamond_sword");
        check(matches().count() == 1 && matches().isMatch(9), "id with namespace");
        clearField();
        type("MENDING");
        check(matches().count() == 1 && matches().isMatch(10), "stored enchantment on the book");
        clearField();
        type("sharpness");
        check(matches().isMatch(9), "enchantment on the sword");
        clearField();
        type("stone dirt");
        check(matches().count() == 0, "words are AND: no item is both");
        clearField();
    }

    /** E, Q, F, 1–9 and Backspace while the field has focus; a click on a slot gives the keyboard back. */
    private void keysWhileFocused() {
        int total = total();
        moveTo(36); // hover the stone so Q, F and 1 would act on it
        ctrlF();
        type("x");
        typeKey(InputConstants.KEY_E, 'e');
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof InventoryScreen), "E types, the screen stays");
        typeKey(InputConstants.KEY_Q, 'q');
        typeKey(InputConstants.KEY_F, 'f');
        typeKey(InputConstants.KEY_1, '1');
        typeKey(InputConstants.KEY_1 + 4, '5');
        check(text().equals("xeqf15"), "every key typed text, was " + text());
        check(count(36) == 10, "Q dropped nothing");
        check(count(45) == 0, "F swapped nothing into the offhand");
        check(count(37) == 3, "1 and 5 swapped nothing with the hotbar");
        synced(total);
        key(InputConstants.KEY_BACKSPACE, 0);
        key(InputConstants.KEY_BACKSPACE, 0);
        check(text().equals("xeqf"), "Backspace edits, was " + text());
        clearField();
        type("stone");
        // A click on a slot unfocuses the field and is a normal vanilla click.
        click(36, LEFT);
        check(!focused(), "clicking a slot gives the keyboard back to the inventory");
        check(carried() == 10, "the click picked the stone up as usual");
        click(36, LEFT);
        check(count(36) == 10 && carried() == 0, "and put it back");
        check(text().equals("stone") && matches().count() == 1, "the query stays");
        // Unfocused: vanilla keys work again (1 swaps the hovered stack with hotbar slot 1).
        moveTo(9);
        key(InputConstants.KEY_1, 0);
        check(item(36).getItem().toString().contains("diamond_sword"), "unfocused 1 swaps as vanilla");
        key(InputConstants.KEY_1, 0);
        check(item(9).getItem().toString().contains("diamond_sword"), "swapped back");
        synced(total);
    }

    /** Escape unfocuses first, then closes; the query is gone when the screen opens again. */
    private void escapeAndReset() {
        ctrlF();
        key(InputConstants.KEY_ESCAPE, 0);
        check(!focused() && ctx.computeOnClient(mc -> mc.gui.screen() instanceof InventoryScreen),
                "first Escape only unfocuses");
        key(InputConstants.KEY_ESCAPE, 0);
        check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "second Escape closes the inventory");
        openInventory();
        check(text().isEmpty() && !matches().active(), "the query resets when the screen closes");
        // E with the field not focused closes the inventory as in vanilla.
        key(InputConstants.KEY_E, 0);
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "unfocused E closes the inventory");
    }

    /** The recipe book moves the container; the field follows it and never covers the book. */
    private void recipeBook() {
        openInventory();
        int leftBefore = leftPos();
        int[] button = ctx.computeOnClient(mc -> {
            for (var child : mc.gui.screen().children()) {
                if (child instanceof ImageButton b) {
                    return new int[] {b.getX() + b.getWidth() / 2, b.getY() + b.getHeight() / 2};
                }
            }
            throw new AssertionError("recipe book button");
        });
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(button[0] * scale, button[1] * scale);
        ctx.waitTicks(1);
        ctx.getInput().pressMouse(LEFT); // a real click: Fabric's mouse events and the recipe book both see it
        ctx.waitTicks(3);
        check(leftPos() != leftBefore, "the recipe book moved the container");
        check(fieldX() >= leftPos(), "the field moved with it, right of the book: " + fieldX() + " ≥ " + leftPos());
        ctrlF();
        type("stone");
        shot("03-inventory-recipe-book");
        close();
    }

    /** Single chest: the field in the title row, focused with the mouse. */
    private void singleChest() {
        clear();
        openBlock("chest", "{Items:[{Slot:0b,id:\"stone\",count:10},{Slot:1b,id:\"dirt\",count:5},"
                + "{Slot:4b,id:\"diamond_sword\",count:1},{Slot:13b,id:\"stone\",count:3}]}");
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen), "chest screen");
        titleRow("single chest");
        clickField();
        check(focused(), "a click on the field focuses it");
        type("stone");
        check(matches().count() == 2 && matches().isMatch(0) && matches().isMatch(13), "two stone stacks");
        check(matches().isDimmed(1) && matches().isDimmed(4), "dirt and sword dimmed");
        moveTo(0);
        shot("04-chest-stone-tooltip");
        close();
    }

    /** Double chest (54 + 36 slots, all different items): placement, and indexing and matching times in game. */
    private void doubleChestAndPerformance() {
        clear();
        List<String> ids = List.of("stone", "dirt", "oak_log", "diamond", "iron_ingot", "gold_ingot", "coal",
                "redstone", "lapis_lazuli", "emerald", "bread", "apple", "torch", "oak_planks", "cobblestone",
                "sand", "gravel", "glass", "white_wool", "string", "bone", "arrow", "bow", "diamond_sword",
                "iron_pickaxe", "shield", "book", "paper", "sugar_cane", "wheat");
        StringBuilder items = new StringBuilder("{Items:[");
        for (int i = 0; i < 27; i++) {
            items.append("{Slot:").append(i).append("b,id:\"").append(ids.get(i % ids.size())).append("\",count:1},");
        }
        items.setLength(items.length() - 1);
        items.append("]}");
        StringBuilder items2 = new StringBuilder("{Items:[");
        for (int i = 0; i < 27; i++) {
            items2.append("{Slot:").append(i).append("b,id:\"").append(ids.get((i + 3) % ids.size()))
                    .append("\",count:1},");
        }
        items2.setLength(items2.length() - 1);
        items2.append("]}");
        for (int i = 0; i < 36; i++) {
            server.runCommand((i < 9 ? "item replace entity @a hotbar." + i : "item replace entity @a inventory."
                    + (i - 9)) + " with " + ids.get((i + 7) % ids.size()));
        }
        server.runCommand("setblock " + BLOCK2 + " chest[facing=north,type=right]" + items2);
        openBlock("chest[facing=north,type=left]", items.toString());
        int rows = ctx.computeOnClient(mc -> mc.player.containerMenu.slots.size());
        if (rows != 90) {
            close();
            server.runCommand("setblock " + BLOCK2 + " chest[facing=north,type=left]" + items2);
            openBlock("chest[facing=north,type=right]", items.toString());
            rows = ctx.computeOnClient(mc -> mc.player.containerMenu.slots.size());
        }
        check(rows == 90, "double chest: 54 + 36 slots, was " + rows);
        titleRow("double chest");
        ctrlF();
        type("diamond");
        check(matches().count() >= 2, "diamond and diamond sword in several places: " + matches().count());
        shot("05-double-chest-diamond");

        // Indexing a cold index (every slot prepared) and matching changed queries, on the render thread.
        long[] times = ctx.computeOnClient(mc -> {
            List<Slot> slots = mc.player.containerMenu.slots;
            String lang = mc.getLanguageManager().getSelected();
            long[] index = new long[30];
            for (int r = 0; r < index.length; r++) {
                var cold = ItemSearchFields.newIndex();
                long t0 = System.nanoTime();
                cold.refresh(slots.size(), lang, i -> {
                    ItemStack stack = slots.get(i).getItem();
                    return stack.isEmpty() ? null : stack;
                });
                index[r] = System.nanoTime() - t0;
            }
            var warm = ItemSearchFields.newIndex();
            warm.refresh(slots.size(), lang, i -> slots.get(i).getItem().isEmpty() ? null : slots.get(i).getItem());
            long t1 = System.nanoTime();
            int unchanged = 300;
            for (int r = 0; r < unchanged; r++) {
                warm.refresh(slots.size(), lang, i -> slots.get(i).getItem().isEmpty() ? null : slots.get(i).getItem());
            }
            long tick = (System.nanoTime() - t1) / unchanged;
            String[] queries = {"stone", "diamond sword", "minecraft:oak", "ingot", "x"};
            long t2 = System.nanoTime();
            int runs = 1000;
            for (int r = 0; r < runs; r++) {
                warm.matches(SearchQuery.of(queries[r % queries.length] + (r % 2 == 0 ? "" : " ")));
            }
            long match = (System.nanoTime() - t2) / runs;
            Arrays.sort(index);
            return new long[] {index[index.length / 2], index[0], tick, match, slots.size()};
        });
        LOGGER.info("[inventorysearch] performance, {} slots: cold index median {} µs (best {} µs); unchanged "
                + "per-tick refresh {} µs; query (fold + match) {} µs", times[4], times[0] / 1000.0,
                times[1] / 1000.0, times[2] / 1000.0, times[3] / 1000.0);
        check(times[0] < 5_000_000, "indexing 90 slots under 5 ms");
        check(times[2] < 500_000, "an unchanged refresh under 0.5 ms");
        close();
    }

    /** Barrel, Ender Chest, Shulker Box and hopper: each gets the field in its title row and searches. */
    private void otherContainers() {
        record Case(String block, String nbt, Class<?> screen, String query, int expected) {
        }
        clear();
        server.runCommand("item replace entity @a enderchest.0 with diamond 2");
        server.runCommand("item replace entity @a enderchest.5 with stone 2");
        var cases = List.of(
                new Case("barrel", "{Items:[{Slot:0b,id:\"stone\",count:4},{Slot:8b,id:\"dirt\",count:2}]}",
                        ContainerScreen.class, "dirt", 1),
                new Case("ender_chest", "", ContainerScreen.class, "diamond", 1),
                new Case("shulker_box", "{Items:[{Slot:0b,id:\"stone\",count:4},{Slot:26b,id:\"stone\",count:2}]}",
                        ShulkerBoxScreen.class, "stone", 2),
                new Case("hopper", "{Items:[{Slot:0b,id:\"stone\",count:4},{Slot:4b,id:\"apple\",count:2}]}",
                        HopperScreen.class, "apple", 1));
        for (Case c : cases) {
            clear();
            openBlock(c.block(), c.nbt());
            check(ctx.computeOnClient(mc -> c.screen().isInstance(mc.gui.screen())), c.block() + " screen");
            titleRow(c.block());
            ctrlF();
            type(c.query());
            check(matches().count() == c.expected(), c.block() + ": " + c.query() + " count " + matches().count());
            shot("06-" + c.block());
            close();
        }
    }

    /**
     * Inventory Mouse Tweaks and vanilla gestures with an active query (the field unfocused, as after a click):
     * right-drag, left-drag collect, Shift + drag, the wheel and vanilla's Shift + double-click all behave as without
     * a query, and the server agrees.
     */
    private void mouseTweaksWithActiveQuery() {
        // Right-drag: one item per visited slot.
        clear();
        server.runCommand("item replace entity @a hotbar.0 with stone 10");
        openBlock("chest", "{Items:[{Slot:20b,id:\"dirt\",count:1}]}");
        searchUnfocused("stone");
        int total = total();
        click(54, LEFT);
        hold(0, RIGHT);
        moveTo(1);
        moveTo(2);
        release(RIGHT);
        check(count(0) == 1 && count(1) == 1 && count(2) == 1 && carried() == 7, "right-drag: one per slot");
        check(matches().count() >= 3, "the matches follow the new stacks: " + matches().count());
        click(54, LEFT);
        synced(total);
        // Left-drag collect.
        click(0, LEFT);
        hold(1, LEFT);
        moveTo(2);
        release(LEFT);
        check(carried() == 3 && count(1) == 0 && count(2) == 0, "left-drag collects, cursor " + carried());
        click(54, LEFT);
        synced(total);
        // Shift + drag: quick-moves each visited chest stack.
        close();
        openBlock("chest", "{Items:[{Slot:0b,id:\"stone\",count:10},{Slot:1b,id:\"dirt\",count:5}]}");
        searchUnfocused("dirt");
        total = total();
        shiftHold(0);
        moveTo(1);
        shiftRelease();
        check(count(0) == 0 && count(1) == 0, "Shift + drag moved both stacks");
        synced(total);
        // Wheel: one item per notch into the chest.
        scrollOver(54, -2);
        check(countOf(0, 27, "stone") + countOf(0, 27, "dirt") == 2, "the wheel moved two items into the chest");
        synced(total);
        close();
        // Vanilla Shift + double-click with an item on the cursor moves every matching stack.
        clear();
        server.runCommand("item replace entity @a hotbar.8 with stone 1");
        openBlock("chest", "{Items:[{Slot:0b,id:\"stone\",count:10},{Slot:1b,id:\"stone\",count:20},"
                + "{Slot:2b,id:\"dirt\",count:7}]}");
        searchUnfocused("stone");
        total = total();
        click(62, LEFT);
        shiftDoubleClickSlot(1);
        check(count(0) == 0 && count(1) == 0 && count(2) == 7 && carried() == 1,
                "Shift + double-click moved all stone, dirt stayed");
        click(62, LEFT);
        synced(total);
        shot("07-mouse-tweaks-with-query");
        close();
    }

    /** Off: no field, no marks, vanilla keys; switched while a chest is open; on again without a restart. */
    private void disabled() {
        clear();
        server.runCommand("item replace entity @a hotbar.0 with stone 3");
        openBlock("chest", "{Items:[{Slot:0b,id:\"stone\",count:1}]}");
        ctrlF();
        type("stone");
        check(matches().active(), "searching before the switch");
        setEnabled(false);
        ctx.waitTicks(2);
        check(!visible() && !focused(), "switched off while open: the field disappears");
        moveTo(54);
        key(InputConstants.KEY_F, InputConstants.MOD_CONTROL);
        check(ctx.computeOnClient(mc -> mc.player.getOffhandItem().getCount()) == 3,
                "off: Ctrl+F reaches vanilla, whose offhand swap ignores Ctrl");
        shot("08-disabled");
        close();
        openBlock("chest", "{Items:[{Slot:0b,id:\"stone\",count:1}]}");
        check(!visible() && !iconVisible(), "off: no field on a new screen");
        key(InputConstants.KEY_E, 0);
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "off: E closes as vanilla");
        setEnabled(true);
        openBlock("chest", "{Items:[{Slot:0b,id:\"stone\",count:1}]}");
        check(visible(), "on again without a restart");
        ctrlF();
        type("stone");
        check(matches().count() >= 1, "and it searches");
        close();
    }

    /**
     * Narrow layouts. Minecraft caps the GUI scale so the scaled screen is at least 320×240, where a double chest has
     * 9 px above it: a renamed double chest whose long title fills the row gets the search icon. A window smaller than
     * that (400×180 at scale 1) leaves the inventory no room above, so it collapses too.
     */
    private void narrowScales() {
        ctx.getInput().resizeWindow(854, 480);
        guiScale(4); // capped to 2: 427×240
        check(ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight()) == 240, "smallest auto height");
        clear();
        server.runCommand("setblock " + BLOCK2 + " chest[facing=north,type=right]");
        openBlock("chest[facing=north,type=left]", "{CustomName:\"Renamed storage chest for every spare block\","
                + "Items:[{Slot:0b,id:\"stone\",count:1},{Slot:20b,id:\"diamond\",count:1}]}");
        check(ctx.computeOnClient(mc -> mc.player.containerMenu.slots.size()) == 90, "double chest");
        check(iconVisible() && !visible(), "long title and 9 px above: the search icon instead of a field");
        shot("09-double-chest-long-title-icon");
        ctrlF();
        check(visible() && focused(), "Ctrl+F opens the collapsed field");
        check(fieldY() >= topPos() && fieldY() + 12 <= topPos() + 17, "the expanded field stays above the slots");
        type("diamond");
        check(matches().count() == 1, "and searches");
        key(InputConstants.KEY_ESCAPE, 0);
        check(visible() && !focused(), "a non-empty query keeps the field open after Escape");
        shot("10-double-chest-long-title-expanded");
        close();
        server.runCommand("setblock " + BLOCK2 + " air");

        ctx.getInput().resizeWindow(400, 180);
        guiScale(0);
        stockPlayer();
        openInventory();
        check(iconVisible() && !visible(), "inventory in a 400×180 window: the icon");
        shot("11-inventory-tiny-window-icon");
        ctrlF();
        type("dirt");
        check(matches().count() == 1 && matches().isMatch(37), "dirt found");
        shot("12-inventory-tiny-window-expanded");
        close();
        ctx.getInput().resizeWindow(1920, 1080);
        guiScale(3);
        doubleChestShot();
        guiScale(2);
    }

    private void doubleChestShot() {
        clear();
        server.runCommand("setblock " + BLOCK2 + " chest[facing=north,type=right]{Items:[{Slot:3b,id:\"diamond\"}]}");
        openBlock("chest[facing=north,type=left]", "{Items:[{Slot:0b,id:\"stone\",count:1}]}");
        check(ctx.computeOnClient(mc -> mc.player.containerMenu.slots.size()) == 90, "double chest");
        titleRow("double chest at 1920×1080 scale 3");
        ctrlF();
        type("diamond");
        shot("13-double-chest-1080p-scale3");
        close();
        server.runCommand("setblock " + BLOCK2 + " air");
    }

    /** pt_br: localized names and enchantments, accent-insensitive (`espada diamante`, `afiacao`). */
    private void portuguese() {
        language("pt_br");
        stockPlayer();
        openInventory();
        ctrlF();
        type("espada diamante");
        check(matches().count() == 1 && matches().isMatch(9), "espada diamante finds the Espada de Diamante");
        clearField();
        type("afiacao");
        check(matches().count() == 1 && matches().isMatch(9), "afiacao finds Afiação on the sword");
        clearField();
        type("remendo");
        check(matches().isMatch(10), "remendo finds the book's Remendo");
        shot("14-inventory-pt-br");
        close();
        language("en_us");
    }

    // ---- Search helpers ----

    private InventorySearchSession session() {
        return ctx.computeOnClient(mc -> mc.gui.screen() instanceof InventorySearchHolder h
                ? h.qolUnified$searchSession() : null);
    }

    private SlotMatches matches() {
        return ctx.computeOnClient(mc -> ((InventorySearchHolder) mc.gui.screen()).qolUnified$searchSession()
                .matches());
    }

    private boolean visible() {
        return ctx.computeOnClient(mc -> session().fieldVisible());
    }

    private boolean iconVisible() {
        return ctx.computeOnClient(mc -> session().iconVisible());
    }

    private boolean focused() {
        return ctx.computeOnClient(mc -> session().focused());
    }

    private String text() {
        return ctx.computeOnClient(mc -> session().text());
    }

    private int fieldX() {
        return ctx.computeOnClient(mc -> session().fieldWidget().getX());
    }

    private int fieldY() {
        return ctx.computeOnClient(mc -> session().fieldWidget().getY());
    }

    private int leftPos() {
        return ctx.computeOnClient(mc -> ((InventorySearchHolder) mc.gui.screen()).qolUnified$leftPos());
    }

    private int topPos() {
        return ctx.computeOnClient(mc -> ((InventorySearchHolder) mc.gui.screen()).qolUnified$topPos());
    }

    /** The field sits in the title row: below the container top, above the first slot row, after the title. */
    private void titleRow(String what) {
        int top = topPos();
        int y = fieldY();
        check(visible(), what + ": field shown");
        check(y >= top && y + 12 <= top + 17, what + ": in the title row, y " + y + " top " + top);
        int titleEnd = ctx.computeOnClient(mc -> {
            var screen = (AbstractContainerScreen<?>) mc.gui.screen();
            return ((InventorySearchHolder) screen).qolUnified$leftPos()
                    + ((InventorySearchHolder) screen).qolUnified$titleLabelX() + mc.font.width(screen.getTitle());
        });
        check(fieldX() > titleEnd, what + ": after the title, x " + fieldX() + " title end " + titleEnd);
    }

    private void searchUnfocused(String query) {
        ctrlF();
        type(query);
        key(InputConstants.KEY_RETURN, 0);
        check(!focused() && matches().active(), "query active, field unfocused");
    }

    private void ctrlF() {
        key(InputConstants.KEY_F, InputConstants.MOD_CONTROL);
    }

    private void clickField() {
        AbstractWidget field = ctx.computeOnClient(mc -> session().fieldWidget());
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        int[] at = ctx.computeOnClient(mc -> new int[] {field.getX() + 10, field.getY() + 6});
        ctx.getInput().setCursorPos(at[0] * scale, at[1] * scale);
        ctx.waitTicks(1);
        ctx.getInput().pressMouse(LEFT);
        ctx.waitTicks(1);
    }

    private void type(String text) {
        ctx.getInput().typeChars(text);
        ctx.waitTicks(1);
    }

    private void clearField() {
        int n = text().length();
        for (int i = 0; i < n; i++) {
            key(InputConstants.KEY_BACKSPACE, 0);
        }
        check(text().isEmpty(), "field cleared");
    }

    /** A real keystroke: the key press, then its character, as the window delivers them. */
    private void typeKey(int key, char c) {
        key(key, 0);
        ctx.getInput().typeChar(c);
        ctx.waitTicks(1);
    }

    /** A plain key press and release, through Fabric's test input (real scancode and keycode). */
    private void key(int key, int modifiers) {
        if (modifiers == 0) {
            ctx.getInput().pressKey(key);
            ctx.waitTicks(1);
            return;
        }
        // Fabric's test input sends no modifiers, so a Ctrl chord goes through the keyboard handler directly.
        int keycode = key == InputConstants.KEY_F ? 'f' : 0;
        ctx.runOnClient(mc -> {
            var keyboard = (KeyboardHandlerAccessor) mc.keyboardHandler;
            long window = mc.getWindow().handle();
            keyboard.invokeKeyPress(window, InputConstants.PRESS, new KeyEvent(key, keycode, modifiers));
            keyboard.invokeKeyPress(window, InputConstants.RELEASE, new KeyEvent(key, keycode, modifiers));
        });
        ctx.waitTicks(1);
    }

    private void guiScale(int scale) {
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(scale);
            mc.resizeGui();
        });
        ctx.waitTicks(3);
    }

    private void language(String code) {
        ctx.runOnClient(mc -> {
            mc.options.languageCode = code;
            mc.getLanguageManager().setSelected(code);
        });
        java.util.concurrent.CompletableFuture<Void> reload = ctx.computeOnClient(mc -> mc.reloadResourcePacks());
        ctx.waitFor(mc -> reload.isDone() && mc.gui.overlay() == null, 1200);
        ctx.waitTicks(5);
        check(ctx.computeOnClient(mc -> mc.getLanguageManager().getSelected()).equals(code), "language " + code);
    }

    private void stockPlayer() {
        close();
        server.runCommand("clear @a");
        server.runCommand("item replace entity @a hotbar.0 with stone 10");
        server.runCommand("item replace entity @a hotbar.1 with dirt 3");
        server.runCommand("item replace entity @a inventory.0 with diamond_sword[enchantments={\"minecraft:sharpness\":5}]");
        server.runCommand("item replace entity @a inventory.1 with enchanted_book[stored_enchantments={\"minecraft:mending\":1}]");
        ctx.waitTicks(2);
    }

    // ---- Inventory helpers (same mechanics as the Mouse Tweaks game test) ----

    private void clear() {
        close();
        server.runCommand("clear @a");
        server.runCommand("setblock " + BLOCK + " air");
        server.runCommand("setblock " + BLOCK2 + " air");
        ctx.waitTicks(2);
    }

    private void close() {
        ctx.runOnClient(mc -> {
            if (mc.gui.screen() != null) {
                mc.gui.screen().onClose();
            }
        });
        ctx.waitTicks(2);
    }

    private void openInventory() {
        close();
        ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        ctx.waitForScreen(InventoryScreen.class);
        ctx.waitTicks(2);
        locateSlots();
    }

    private void openBlock(String block, String nbt) {
        server.runCommand("setblock " + BLOCK + " " + block + nbt);
        server.runCommand("tp @a 0.5 100 0.5 0 35");
        ctx.waitTicks(3);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitFor(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>);
        ctx.waitTicks(3);
        locateSlots();
    }

    private void locateSlots() {
        centers.clear();
        ctx.runOnClient(mc -> {
            var screen = (AbstractContainerScreen<?>) mc.gui.screen();
            var accessor = (ContainerScreenSlotsAccessor) screen;
            Map<Integer, int[]> bounds = new HashMap<>();
            for (int x = 0; x < screen.width; x++) {
                for (int y = 0; y < screen.height; y++) {
                    Slot slot = accessor.qolUnified$slotAt(x + 0.5, y + 0.5);
                    if (slot != null) {
                        int px = x;
                        int py = y;
                        int[] b = bounds.computeIfAbsent(slot.index, i -> new int[] {px, py, px, py});
                        b[0] = Math.min(b[0], x);
                        b[1] = Math.min(b[1], y);
                        b[2] = Math.max(b[2], x);
                        b[3] = Math.max(b[3], y);
                    }
                }
            }
            bounds.forEach((i, b) -> centers.put(i, new double[] {(b[0] + b[2]) / 2.0 + 0.5,
                    (b[1] + b[3]) / 2.0 + 0.5}));
        });
    }

    private void moveTo(int slot) {
        double[] c = centers.get(slot);
        check(c != null, "slot " + slot + " on screen");
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(c[0] * scale, c[1] * scale);
        ctx.waitTicks(1);
    }

    private void hold(int slot, int button) {
        moveTo(slot);
        ctx.getInput().holdMouse(button);
        ctx.waitTicks(1);
        double[] c = centers.get(slot);
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos((c[0] + 1) * scale, c[1] * scale);
        ctx.waitTicks(1);
    }

    private void release(int button) {
        ctx.getInput().releaseMouse(button);
        ctx.waitTicks(2);
    }

    private void click(int slot, int button) {
        hold(slot, button);
        release(button);
    }

    private void shiftHold(int slot) {
        moveTo(slot);
        shiftButton(InputConstants.PRESS);
        double[] c = centers.get(slot);
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos((c[0] + 1) * scale, c[1] * scale);
        ctx.waitTicks(1);
    }

    private void shiftRelease() {
        shiftButton(InputConstants.RELEASE);
        ctx.waitTicks(2);
    }

    private void shiftDoubleClickSlot(int slot) {
        moveTo(slot);
        shiftButton(InputConstants.PRESS);
        shiftButton(InputConstants.RELEASE);
        shiftButton(InputConstants.PRESS);
        shiftButton(InputConstants.RELEASE);
        ctx.waitTicks(3);
    }

    private void shiftButton(int action) {
        ctx.runOnClient(mc -> ((MouseHandlerAccessor) mc.mouseHandler).invokeOnButton(mc.getWindow().handle(),
                new MouseButtonInfo(LEFT, InputConstants.MOD_SHIFT), action));
        ctx.waitTicks(1);
    }

    private void scrollOver(int slot, double amount) {
        moveTo(slot);
        ctx.getInput().scroll(amount);
        ctx.waitTicks(2);
    }

    private AbstractContainerMenu menu(Minecraft mc) {
        return mc.player.containerMenu;
    }

    private int count(int slot) {
        return ctx.computeOnClient(mc -> menu(mc).getSlot(slot).getItem().getCount());
    }

    private ItemStack item(int slot) {
        return ctx.computeOnClient(mc -> menu(mc).getSlot(slot).getItem().copy());
    }

    private int carried() {
        return ctx.computeOnClient(mc -> menu(mc).getCarried().getCount());
    }

    private int countOf(int from, int to, String id) {
        return ctx.computeOnClient(mc -> menu(mc).slots.subList(from, to).stream()
                .filter(s -> s.getItem().getItem().toString().contains(id)).mapToInt(s -> s.getItem().getCount())
                .sum());
    }

    private int total() {
        return ctx.computeOnClient(mc -> menu(mc).slots.stream().mapToInt(s -> s.getItem().getCount()).sum()
                + menu(mc).getCarried().getCount());
    }

    private void synced(int expectedTotal) {
        ctx.waitTicks(3);
        check(total() == expectedTotal, "item total " + expectedTotal + " → " + total());
        String client = ctx.computeOnClient(mc -> describe(menu(mc)));
        String serverSide = server.computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            return describe(p.containerMenu);
        });
        check(client.equals(serverSide), "client and server agree:\nclient " + client + "\nserver " + serverSide);
    }

    private static String describe(AbstractContainerMenu menu) {
        var out = new ArrayList<String>();
        for (Slot slot : menu.slots) {
            if (!slot.getItem().isEmpty()) {
                out.add(slot.index + "=" + slot.getItem().getCount() + " " + slot.getItem().getItem());
            }
        }
        return String.join("; ", out) + " cursor=" + menu.getCarried().getCount();
    }

    private void setEnabled(boolean enabled) {
        on(core -> core.saveAndApply(Map.of(ID, core.choices().get(ID).withEnabled(enabled))));
        ctx.waitTicks(1);
    }

    private void on(Consumer<CoreRuntime> action) {
        ctx.runOnClient(mc -> action.accept(QolUnifiedClient.core()));
    }

    private void shot(String name) {
        ctx.takeScreenshot("inventorysearch-" + name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
