package unified.qol.dev;

import unified.qol.DevelopmentFeatures;
import unified.qol.core.ApplyMode;
import unified.qol.core.Feature;
import unified.qol.core.FeatureCategory;
import unified.qol.core.SettingDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

/** Development-only sample features covering the M0 scenarios. */
public final class DevFixtures implements DevelopmentFeatures {
    static final String SCENARIO_PROPERTY = "qol_unified.dev.scenario";
    private static final Map<String, SampleFeature> FEATURES = new LinkedHashMap<>();

    @Override
    public List<Feature> features() {
        seedScenario();
        FEATURES.clear();
        add(SampleFeature.builder("dev_live_toggle", FeatureCategory.HUD_INFORMATION)
                .setting(new SettingDescriptor.BooleanSetting("show_details", false, ApplyMode.LIVE)));
        add(SampleFeature.builder("dev_restart_toggle", FeatureCategory.HUD_INFORMATION).restartRequired());
        add(SampleFeature.builder("dev_all_settings", FeatureCategory.INVENTORY)
                .setting(new SettingDescriptor.BooleanSetting("highlight", false, ApplyMode.LIVE))
                .setting(new SettingDescriptor.IntegerSetting("slot_limit", 16, 1, 64, ApplyMode.RESTART_REQUIRED))
                .setting(new SettingDescriptor.EnumSetting("layout", "compact",
                        List.of("compact", "detailed", "hidden"), ApplyMode.LIVE)));
        // Fabric API is always installed in development, so this conflict is always detected.
        add(SampleFeature.builder("dev_conflict", FeatureCategory.VISUALS).conflictsWith("fabric-api"));
        add(SampleFeature.builder("dev_enable_fails", FeatureCategory.CONTROLS).failEnable(Integer.MAX_VALUE));
        add(SampleFeature.builder("dev_disable_fails", FeatureCategory.CONTROLS).failDisable(2));
        for (String suffix : List.of("a", "b", "c", "d", "e", "f", "g", "h")) {
            add(SampleFeature.builder("dev_filler_" + suffix, FeatureCategory.HUD_INFORMATION));
        }
        add(SampleFeature.builder("dev_filler_chat", FeatureCategory.CHAT_SOCIAL));
        add(SampleFeature.builder("dev_filler_network", FeatureCategory.PERFORMANCE_NETWORK));
        add(SampleFeature.builder("dev_filler_map", FeatureCategory.MAPS_NAVIGATION));
        return new ArrayList<>(FEATURES.values());
    }

    static SampleFeature feature(String id) {
        return FEATURES.get(id);
    }

    private static void add(SampleFeature.Builder builder) {
        SampleFeature feature = builder.build();
        FEATURES.put(feature.descriptor().id(), feature);
    }

    /**
     * Prepares the configuration for a game-test scenario. Runs only when the scenario property is set,
     * and only inside a disposable {@code run-gametest*} game directory.
     */
    private static void seedScenario() {
        String scenario = System.getProperty(SCENARIO_PROPERTY);
        if (scenario == null) {
            return;
        }
        Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        if (!gameDir.getFileName().toString().startsWith("run-gametest")) {
            throw new IllegalStateException("Refusing to seed configuration outside a run-gametest directory");
        }
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("qol_unified");
        try {
            switch (scenario) {
                case "main" -> {
                    deleteTree(dir);
                    write(dir, """
                            {"schemaVersion":1,"futureField":{"nested":[1,{"keep":true}]},
                             "features":{"removed_feature":{"enabled":true,"settings":{"x":1}},
                             "dev_live_toggle":{"settings":{"unknown_setting":"kept"}}}}""");
                }
                case "foodhud", "appleskin", "chatheads", "chatheads_reference", "keyboardmap", "targetinfo",
                        "targetinfo_jade", "soundcontrol", "shulkerpreview", "settings", "zoom", "mousetweaks",
                        "inventorysearch" -> deleteTree(dir);
                case "restart", "soundcontrolrestart", "settingsrestart" -> {
                    // Reuse the configuration written by the "main" (or "soundcontrol") scenario.
                }
                case "newer" -> {
                    deleteTree(dir);
                    write(dir, "{\"schemaVersion\":99,\"features\":{\"dev_live_toggle\":{\"enabled\":false}}}");
                }
                case "corrupt" -> {
                    deleteTree(dir);
                    write(dir, "{ \"schemaVersion\": 1, broken");
                }
                default -> throw new IllegalArgumentException("Unknown scenario " + scenario);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path dir, String json) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("config.json"), json);
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
