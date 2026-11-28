package unified.qol.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unified.qol.feature.inventorysearch.InventorySearchHolder;
import unified.qol.feature.inventorysearch.InventorySearchSession;

/**
 * Inventory Search Mixin IS-1 (inventory-search-v1.md §6): the screen carries its own search session, and after
 * vanilla draws the slots (still under the hover highlight's front, the carried item and the tooltip) the session dims
 * non-matching slots and frames matching ones. The search field and icon are drawn at the end of extractContents, after
 * the container labels (which vanilla draws after widgets), so a field over the title row is never written over.
 * Drawing only: no input, no slot or item is touched.
 */
@Mixin(AbstractContainerScreen.class)
abstract class ContainerScreenSearchMixin implements InventorySearchHolder {
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Shadow
    @Final
    protected int imageWidth;
    @Shadow
    protected int titleLabelX;
    @Shadow
    @Final
    protected AbstractContainerMenu menu;

    @Unique
    private InventorySearchSession qolUnified$searchSession;

    @Inject(method = "extractSlots(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", at = @At("TAIL"))
    private void qolUnified$drawSearch(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        InventorySearchSession session = qolUnified$searchSession;
        if (session != null) {
            session.drawSlots(graphics, menu.slots);
        }
    }

    @Inject(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
    private void qolUnified$drawSearchWidgets(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a,
            CallbackInfo ci) {
        InventorySearchSession session = qolUnified$searchSession;
        if (session != null) {
            session.drawWidgets(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    public InventorySearchSession qolUnified$searchSession() {
        return qolUnified$searchSession;
    }

    @Override
    public void qolUnified$setSearchSession(InventorySearchSession session) {
        qolUnified$searchSession = session;
    }

    @Override
    public int qolUnified$leftPos() {
        return leftPos;
    }

    @Override
    public int qolUnified$topPos() {
        return topPos;
    }

    @Override
    public int qolUnified$imageWidth() {
        return imageWidth;
    }

    @Override
    public int qolUnified$titleLabelX() {
        return titleLabelX;
    }
}
