package unified.qol.feature.inventorysearch;

import unified.qol.core.Feature;
import unified.qol.core.FeatureContext;
import unified.qol.core.FeatureDescriptor;
import java.util.Objects;

/**
 * Inventory Search (inventory-search-v1.md). The screen hooks and the slot Mixin are installed once and act only while
 * this feature is active, so switching it is LIVE: an open screen hides or shows its field on the next tick, and
 * newly opened screens follow the switch.
 */
public final class InventorySearchFeature implements Feature {
    private static volatile boolean active;

    @Override
    public FeatureDescriptor descriptor() {
        return InventorySearchSettings.DESCRIPTOR;
    }

    @Override
    public void enable(FeatureContext context) {
        Objects.requireNonNull(context, "context");
        active = true;
    }

    @Override
    public void disable() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }
}
