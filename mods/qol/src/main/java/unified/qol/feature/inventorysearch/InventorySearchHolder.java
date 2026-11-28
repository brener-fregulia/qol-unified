package unified.qol.feature.inventorysearch;

/**
 * Implemented on every {@code AbstractContainerScreen} by {@code ContainerScreenSearchMixin}: the screen carries its
 * own Inventory Search session, so the state is scoped to that screen object and needs no global map.
 */
public interface InventorySearchHolder {
    InventorySearchSession qolUnified$searchSession();

    void qolUnified$setSearchSession(InventorySearchSession session);

    int qolUnified$leftPos();

    int qolUnified$topPos();

    int qolUnified$imageWidth();

    int qolUnified$titleLabelX();
}
