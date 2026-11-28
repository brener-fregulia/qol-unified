package unified.qol.feature.inventorysearch;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;

/**
 * Attaches Inventory Search to supported screens (inventory-search-v1.md §4, §6). Fabric recreates a screen's events
 * on every init, so the listeners are registered again after a resize and never twice. Only the screen's own events
 * are used: no global keyboard or mouse hook, and nothing outside the supported container screens.
 */
public final class InventorySearchHooks {
    /** Vanilla's container width; modded subclasses with another layout get no field. */
    private static final int VANILLA_WIDTH = 176;
    private static boolean registered;
    private static Boolean recipeViewer;

    private InventorySearchHooks() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            SearchFieldLayout.Kind kind = kind(screen);
            if (kind == null) {
                return;
            }
            var container = (AbstractContainerScreen<?>) screen;
            var holder = (InventorySearchHolder) container;
            InventorySearchSession session = holder.qolUnified$searchSession();
            if (session == null) {
                session = new InventorySearchSession(container, kind);
                holder.qolUnified$setSearchSession(session);
            }
            InventorySearchSession s = session;
            s.attach();
            ScreenKeyboardEvents.allowKeyPress(screen).register((sc, event) -> s.allowKey(event));
            ScreenMouseEvents.beforeMouseClick(screen).register((sc, event) -> s.beforeClick(event));
            ScreenMouseEvents.afterMouseClick(screen).register((sc, event, consumed) -> {
                s.layout(); // the recipe book button moves the container
                return consumed;
            });
            ScreenEvents.afterTick(screen).register(sc -> s.tick());
            ScreenEvents.beforeExtract(screen).register((sc, graphics, mouseX, mouseY, a) -> s.beforeScreenDraw());
            ScreenEvents.remove(screen).register(sc -> holder.qolUnified$setSearchSession(null));
        });
    }

    /**
     * The supported screens: the player inventory, every 9×N chest menu (chests, barrels, Ender Chests, minecart and
     * boat chests), Shulker Boxes and hoppers. The creative inventory has its own search.
     */
    static SearchFieldLayout.Kind kind(Screen screen) {
        if (screen instanceof CreativeModeInventoryScreen || !(screen instanceof AbstractContainerScreen<?>)) {
            return null;
        }
        if (((InventorySearchHolder) screen).qolUnified$imageWidth() != VANILLA_WIDTH) {
            return null;
        }
        if (screen instanceof InventoryScreen) {
            return SearchFieldLayout.Kind.INVENTORY;
        }
        if (screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen || screen instanceof HopperScreen) {
            return SearchFieldLayout.Kind.CONTAINER;
        }
        return null;
    }

    /** JEI, REI or EMI installed: they bind Ctrl+F to their own search, so Inventory Search leaves it to them. */
    static boolean recipeViewerPresent() {
        if (recipeViewer == null) {
            recipeViewer = InventorySearchSettings.RECIPE_VIEWERS.stream()
                    .anyMatch(FabricLoader.getInstance()::isModLoaded);
        }
        return recipeViewer;
    }
}
