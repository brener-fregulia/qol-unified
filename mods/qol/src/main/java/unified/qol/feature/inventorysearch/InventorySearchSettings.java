package unified.qol.feature.inventorysearch;

import unified.qol.core.ApplyMode;
import unified.qol.core.FeatureCategory;
import unified.qol.core.FeatureDescriptor;
import java.util.List;

/** Inventory Search descriptor (inventory-search-v1.md §7): one LIVE switch, no other settings. */
public final class InventorySearchSettings {
    public static final String ID = "inventory_search";

    /**
     * Fabric mod ids of recipe viewers that bind Ctrl+F to their own search (JEI, REI, EMI). While one is installed,
     * Ctrl+F is left to it; the field is still reachable with the mouse.
     */
    public static final List<String> RECIPE_VIEWERS = List.of("jei", "roughlyenoughitems", "emi");

    public static final FeatureDescriptor DESCRIPTOR = new FeatureDescriptor(ID, FeatureCategory.INVENTORY, true,
            ApplyMode.LIVE, List.of(), List.of());

    private InventorySearchSettings() {
    }
}
