package unified.qol;

import unified.qol.core.Feature;
import unified.qol.core.FeatureRegistry;
import unified.qol.feature.chatheads.ChatHeadsFeature;
import unified.qol.feature.foodhud.FoodHudFeature;
import unified.qol.feature.inventorysearch.InventorySearchFeature;
import unified.qol.feature.keyboardmap.KeyboardMapFeature;
import unified.qol.feature.mousetweaks.MouseTweaksFeature;
import unified.qol.feature.shulkerpreview.ShulkerPreviewFeature;
import unified.qol.feature.soundcontrol.SoundControlFeature;
import unified.qol.feature.targetinfo.TargetInfoFeature;
import unified.qol.feature.zoom.ZoomFeature;
import java.util.List;

/** The production feature list. Features are registered here explicitly (M0 §3.5). */
public final class Features {
    private Features() {
    }

    public static FeatureRegistry createRegistry() {
        return createRegistry(List.of());
    }

    /** Production features followed by development-only sample features (empty outside development). */
    public static FeatureRegistry createRegistry(List<Feature> developmentFeatures) {
        FeatureRegistry.Builder builder = FeatureRegistry.builder();
        builder.register(new FoodHudFeature());
        builder.register(new ChatHeadsFeature());
        builder.register(new KeyboardMapFeature());
        builder.register(new TargetInfoFeature());
        builder.register(new SoundControlFeature());
        builder.register(new ShulkerPreviewFeature());
        builder.register(new ZoomFeature());
        builder.register(new MouseTweaksFeature());
        builder.register(new InventorySearchFeature());
        developmentFeatures.forEach(builder::register);
        return builder.build();
    }
}
