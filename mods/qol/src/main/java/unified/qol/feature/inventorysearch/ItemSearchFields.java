package unified.qol.feature.inventorysearch;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import unified.qol.search.SearchableText;

/**
 * What Inventory Search matches on an item stack (inventory-search-v1.md §4), all client-visible: the display name
 * (a custom name when renamed), the item's own name in the current language, its id with namespace
 * ({@code minecraft:diamond_sword}), and the names of its enchantments and stored enchantments (enchanted books) when
 * the item shows them in its tooltip. No tooltip is built and nothing inside a container item is read.
 */
public final class ItemSearchFields {
    private ItemSearchFields() {
    }

    /** A new, empty index for one screen's slots. */
    public static SlotSearchIndex<ItemStack> newIndex() {
        return new SlotSearchIndex<>(ItemStack::isSameItemSameComponents, stack -> stack.copyWithCount(1),
                ItemSearchFields::prepare);
    }

    static SearchableText prepare(ItemStack stack) {
        var fields = new ArrayList<String>(6);
        String display = stack.getHoverName().getString();
        String name = stack.getItemName().getString();
        fields.add(display);
        if (!name.equals(display)) {
            fields.add(name);
        }
        fields.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        TooltipDisplay tooltip = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        addEnchantments(fields, stack, tooltip, DataComponents.ENCHANTMENTS);
        addEnchantments(fields, stack, tooltip, DataComponents.STORED_ENCHANTMENTS);
        return SearchableText.of(fields);
    }

    private static void addEnchantments(List<String> fields, ItemStack stack, TooltipDisplay tooltip,
            DataComponentType<ItemEnchantments> type) {
        ItemEnchantments enchantments = stack.get(type);
        if (enchantments == null || enchantments.isEmpty() || !tooltip.shows(type)) {
            return;
        }
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            fields.add(enchantment.value().description().getString());
        }
    }
}
