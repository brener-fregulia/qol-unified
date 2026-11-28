package unified.qol.feature.inventorysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unified.qol.core.ApplyMode;
import unified.qol.core.FeatureCategory;

class InventorySearchSettingsTest {
    @AfterEach
    void deactivate() {
        new InventorySearchFeature().disable();
    }

    @Test
    void descriptorMatchesTheApprovedScope() {
        var d = InventorySearchSettings.DESCRIPTOR;
        assertEquals("inventory_search", d.id());
        assertEquals(FeatureCategory.INVENTORY, d.category());
        assertTrue(d.defaultEnabled());
        assertEquals(ApplyMode.LIVE, d.toggleMode());
        assertEquals(List.of(), d.settings(), "one switch, no other settings");
        assertEquals(List.of(), d.conflicts());
        assertEquals(List.of("jei", "roughlyenoughitems", "emi"), InventorySearchSettings.RECIPE_VIEWERS);
    }

    @Test
    void switchIsLive() {
        assertFalse(InventorySearchFeature.isActive());
        var feature = new InventorySearchFeature();
        feature.enable(() -> null);
        assertTrue(InventorySearchFeature.isActive());
        feature.disable();
        assertFalse(InventorySearchFeature.isActive());
    }
}
