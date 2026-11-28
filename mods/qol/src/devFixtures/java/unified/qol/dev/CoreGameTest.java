package unified.qol.dev;

import com.mojang.blaze3d.platform.InputConstants;
import unified.qol.QolUnifiedClient;
import unified.qol.core.CoreRuntime;
import unified.qol.core.FailureRecord;
import unified.qol.core.FeatureState;
import unified.qol.core.config.ConfigurationService;
import unified.qol.core.config.LoadStatus;
import unified.qol.ui.UiKeys;
import unified.qol.ui.screen.QolWorkspace;
import unified.settings.ui.UnifiedSettingsScreen;
import unified.settings.ui.WorkspaceKeys;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Development-only client game test driving the real settings screen through the M0 scenarios. Runs in
 * disposable {@code build/run-gametest*} directories and saves screenshots there.
 */
public final class CoreGameTest implements FabricClientGameTest {
    private static final InputConstants.Key KEY_ESCAPE = InputConstants.getKey("key.keyboard.escape");

    private ClientGameTestContext ctx;
    private String scenario;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        scenario = System.getProperty(DevFixtures.SCENARIO_PROPERTY, "main");
        ctx.waitForScreen(TitleScreen.class);
        switch (scenario) {
            case "main" -> main();
            case "restart" -> restart();
            case "newer" -> newer();
            case "corrupt" -> corrupt();
            case "foodhud" -> foodHud();
            case "appleskin" -> new FoodHudGameTest(ctx).compareWithAppleSkin();
            case "chatheads" -> new ChatHeadsGameTest(ctx).run();
            case "chatheads_reference" -> new ChatHeadsGameTest(ctx).compareWithReference();
            case "keyboardmap" -> new KeyboardMapGameTest(ctx).run();
            case "targetinfo" -> new TargetInfoGameTest(ctx).run();
            case "settings" -> new SettingsWorkspaceGameTest(ctx).run();
            case "settingsrestart" -> new SettingsWorkspaceGameTest(ctx).afterRestart();
            case "shulkerpreview" -> new ShulkerPreviewGameTest(ctx).run();
            case "targetinfo_jade" -> new TargetInfoGameTest(ctx).compareWithJade();
            case "soundcontrol" -> new SoundControlGameTest(ctx).run();
            case "soundcontrolrestart" -> new SoundControlGameTest(ctx).restart();
            case "zoom" -> new ZoomGameTest(ctx).run();
            case "mousetweaks" -> new MouseTweaksGameTest(ctx).run();
            case "inventorysearch" -> new InventorySearchGameTest(ctx).run();
            default -> throw new AssertionError("Unknown scenario " + scenario);
        }
        ctx.setScreen(TitleScreen::new);
        ctx.waitForScreen(TitleScreen.class);
    }

    private void foodHud() {
        new FoodHudGameTest(ctx).run();
        ctx.waitForScreen(TitleScreen.class);
        // Food HUD is the first feature: the workspace opens on its panel with all seven settings inline.
        open();
        // Leaving a world leaves the cursor grabbed for a moment; let it settle before clicking.
        ctx.waitTicks(10);
        check(ctx.computeOnClient(mc -> ((UnifiedSettingsScreen) mc.gui.screen()).navigation().selected())
                .equals("food_hud"), "Food HUD selected first");
        shot("food-hud-settings");
        closeIfOpen();
    }

    private void main() {
        shot("01-title");
        on(core -> {
            check(core.configurationStatus() == LoadStatus.LOADED, "seeded config loads");
            check(core.lifecycle().state("dev_live_toggle") == FeatureState.ACTIVE, "live toggle active");
            check(core.lifecycle().state("dev_conflict") == FeatureState.INACTIVE, "conflict default disables");
            check(core.compatibility("dev_conflict").defaultSuppressed(), "conflict default suppressed");
            check(core.lifecycle().state("dev_enable_fails") == FeatureState.INACTIVE, "failed enable cleaned up");
            check(core.lifecycle().failure("dev_enable_fails").map(FailureRecord::operation)
                    .orElse(null) == FailureRecord.Operation.ENABLE, "enable failure recorded");
            check(core.lifecycle().state("dev_disable_fails") == FeatureState.ACTIVE, "disable sample active");
        });

        // Real key presses are covered by manual QA: the test harness's simulated keys do not reach screens
        // for every key in this Minecraft version.
        open();
        shot("02-settings-hud");
        ctx.getInput().setCursorPos(ctx.computeOnClient(mc -> mc.getWindow().getScreenWidth() * 0.6),
                ctx.computeOnClient(mc -> mc.getWindow().getScreenHeight() * 0.5));
        ctx.getInput().scroll(-10);
        ctx.waitTicks(2);
        shot("03-settings-hud-scrolled");
        click(UiKeys.category(unified.qol.core.FeatureCategory.CONTROLS));
        shot("04-controls-category");
        search("sample");
        shot("05-search-across-categories");

        // Navigation and search write nothing.
        byte[] before = configBytes();
        search("dev_live_toggle");
        shot("05b-search-live");
        check(java.util.Arrays.equals(before, configBytes()), "navigation and search do not save");

        // Auto-apply (settings-ux-v1.md §18): the switch saves and applies at once; Back only closes.
        click(UiKeys.SWITCH_ON);
        shot("05c-toggled");
        on(core -> check(core.lifecycle().state("dev_live_toggle") == FeatureState.INACTIVE, "live toggle applied"));
        check(config().contains("\"enabled\": false"), "live toggle saved");
        byte[] afterToggle = configBytes();
        click(WorkspaceKeys.BACK);
        ctx.waitForScreen(TitleScreen.class);
        check(java.util.Arrays.equals(afterToggle, configBytes()), "Back writes nothing more");
        on(core -> check(core.lifecycle().state("dev_live_toggle") == FeatureState.INACTIVE, "Back keeps it"));

        // Escape behaves like Back.
        open();
        search("dev_live_toggle");
        ctx.getInput().pressKey(KEY_ESCAPE);
        ctx.waitForScreen(TitleScreen.class);
        check(java.util.Arrays.equals(afterToggle, configBytes()), "Escape writes nothing");

        // RESTART_REQUIRED toggle is saved at once but not applied; the screen says so and stays usable.
        open();
        search("dev_restart_toggle");
        click(UiKeys.SWITCH_ON);
        shot("06-restart-required");
        on(core -> {
            check(core.lifecycle().state("dev_restart_toggle") == FeatureState.ACTIVE, "restart toggle not applied");
            check(core.lifecycle().pendingRestart("dev_restart_toggle"), "restart pending");
        });
        check(config().contains("dev_restart_toggle"), "restart toggle saved");

        // Conflict default overridden explicitly, without leaving the screen.
        search("dev_conflict");
        shot("07-conflict-default");
        click(UiKeys.SWITCH_OFF);
        on(core -> {
            check(core.lifecycle().state("dev_conflict") == FeatureState.ACTIVE, "explicit enable despite conflict");
            check(core.compatibility("dev_conflict").playerOverride(), "override flagged");
        });

        // Disable failure → UNKNOWN; no automatic retry; explicit Retry disable.
        search("dev_disable_fails");
        click(UiKeys.SWITCH_ON);
        shot("08-disable-failed-result");
        on(core -> check(core.lifecycle().state("dev_disable_fails") == FeatureState.UNKNOWN, "UNKNOWN after failure"));
        int calls = DevFixtures.feature("dev_disable_fails").calls().size();
        click(WorkspaceKeys.BACK);
        ctx.waitForScreen(TitleScreen.class);
        check(DevFixtures.feature("dev_disable_fails").calls().size() == calls, "no automatic retry");
        open();
        search("dev_disable_fails");
        check(locate(UiKeys.SWITCH_ON).isEmpty() && locate(UiKeys.SWITCH_OFF).isEmpty(),
                "no enable control while UNKNOWN");
        shot("09-unknown-row");
        click(UiKeys.RETRY_DISABLE);
        shot("10-retry-failed");
        on(core -> check(core.lifecycle().state("dev_disable_fails") == FeatureState.UNKNOWN, "retry 1 fails"));
        click(UiKeys.RETRY_DISABLE);
        shot("11-retry-succeeded");
        on(core -> check(core.lifecycle().state("dev_disable_fails") == FeatureState.INACTIVE, "retry 2 succeeds"));

        // Integer input: invalid text is never saved and leaves the saved value intact; Enter commits a valid one.
        search("dev_all_settings");
        shot("12-feature-settings");
        byte[] beforeInvalid = configBytes();
        setIntegerField("abc");
        shot("13-invalid-integer");
        check(java.util.Arrays.equals(beforeInvalid, configBytes()), "invalid text not saved");
        on(core -> check(core.choices().get("dev_all_settings").settings().getInt("slot_limit") == 16,
                "saved value intact"));
        // The error stays findable after navigating away: the feature is flagged in the sidebar.
        search("");
        check(ctx.computeOnClient(mc -> ((UnifiedSettingsScreen) mc.gui.screen()).navigation()
                .rows((module, id) -> id.equals("dev_all_settings")).stream().anyMatch(
                        unified.settings.ui.WorkspaceNavigation.Row::isFlagged)), "flag model");
        search("dev_all_settings");
        setIntegerField("48");
        click(UiKeys.enumValue("dev_all_settings", "layout", "compact"));
        click("options.off");
        shot("15-feature-settings-edited");
        shot("16-settings-saved-restart");
        click(WorkspaceKeys.BACK);
        ctx.waitForScreen(TitleScreen.class);
        on(core -> {
            var effective = core.lifecycle().effectiveSettings("dev_all_settings");
            var desired = core.lifecycle().desiredSettings("dev_all_settings");
            check(effective.getEnum("layout").equals("detailed"), "LIVE enum applied");
            check(effective.getBoolean("highlight"), "LIVE boolean applied");
            check(effective.getInt("slot_limit") == 16, "RESTART_REQUIRED keeps old effective value");
            check(desired.getInt("slot_limit") == 48, "RESTART_REQUIRED desired value saved");
        });

        // Persistence: a fresh load of the file sees the saved choices; unknown data is preserved.
        on(core -> {
            var reloaded = new ConfigurationService(configFile(), core.registry());
            reloaded.load();
            check(!reloaded.choices().get("dev_live_toggle").enabled(), "reload: live toggle off");
            check(reloaded.choices().get("dev_all_settings").settings().getInt("slot_limit") == 48, "reload: slot");
        });
        String text = config();
        check(text.contains("futureField") && text.contains("removed_feature") && text.contains("unknown_setting"),
                "unknown data preserved");
    }

    /** Second launch after "main": checks persistence across a real restart. */
    private void restart() {
        on(core -> {
            check(core.configurationStatus() == LoadStatus.LOADED, "config loads after restart");
            check(core.lifecycle().state("dev_live_toggle") == FeatureState.INACTIVE, "live toggle persisted");
            check(core.lifecycle().state("dev_restart_toggle") == FeatureState.INACTIVE, "restart toggle applied now");
            check(!core.lifecycle().pendingRestart("dev_restart_toggle"), "no longer pending");
            var effective = core.lifecycle().effectiveSettings("dev_all_settings");
            check(effective.getInt("slot_limit") == 48, "restart-required setting effective after restart");
            check(effective.getEnum("layout").equals("detailed"), "enum persisted");
            check(core.lifecycle().state("dev_conflict") == FeatureState.ACTIVE, "explicit override persisted");
            check(core.lifecycle().state("dev_disable_fails") == FeatureState.INACTIVE, "disabled choice persisted");
        });
        String text = config();
        check(text.contains("futureField") && text.contains("removed_feature"), "unknown data still preserved");
        open();
        shot("01-after-restart");
        click(WorkspaceKeys.BACK);
    }

    private void newer() {
        byte[] before = configBytes();
        on(core -> {
            check(core.configurationStatus() == LoadStatus.NEWER_VERSION, "newer schema detected");
            check(core.configurationReadOnly(), "read-only");
            check(core.lifecycle().state("dev_live_toggle") == FeatureState.INACTIVE, "understood value used");
        });
        open();
        shot("01-read-only");
        check(locate(UiKeys.SWITCH_ON).isEmpty() && locate(UiKeys.SWITCH_OFF).isEmpty(),
                "switches locked while read-only");
        click(WorkspaceKeys.BACK);
        check(java.util.Arrays.equals(before, configBytes()), "newer file never modified");
    }

    private void corrupt() {
        byte[] original = configBytes();
        on(core -> check(core.configurationStatus() == LoadStatus.RECOVERED, "corrupt file recovered"));
        Path backups = configFile().resolveSibling("backups");
        try (Stream<Path> files = Files.list(backups)) {
            List<Path> list = files.toList();
            check(list.size() == 1, "one backup");
            check(java.util.Arrays.equals(original, Files.readAllBytes(list.getFirst())), "backup is exact copy");
        } catch (IOException e) {
            throw new AssertionError("backups missing", e);
        }
        check(java.util.Arrays.equals(original, configBytes()), "original kept until save");
        open();
        search("dev_live_toggle");
        shot("01-recovered-defaults");
        click(UiKeys.SWITCH_ON);
        closeIfOpen();
        check(config().contains("\"schemaVersion\": 1") && config().contains("\"enabled\": false"),
                "file replaced with valid configuration on save");
    }

    // --- helpers ---

    /** Finds a visible widget whose message is the given translation key, anywhere in the screen. */
    private java.util.Optional<AbstractWidget> widget(Minecraft mc, String key) {
        var found = new ArrayList<AbstractWidget>();
        visit(mc.gui.screen(), w -> {
            if (w.visible && w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
                found.add(w);
            }
        });
        return found.stream().findFirst();
    }

    private java.util.Optional<double[]> locate(String key) {
        return ctx.computeOnClient(mc -> widget(mc, key).filter(AbstractWidget::isActive).map(w -> {
            int scale = mc.getWindow().getGuiScale();
            return new double[] {(w.getX() + w.getWidth() / 2.0) * scale, (w.getY() + w.getHeight() / 2.0) * scale};
        }));
    }

    private boolean isActive(String key) {
        return ctx.computeOnClient(mc -> widget(mc, key).map(AbstractWidget::isActive).orElse(false));
    }

    /** Clicks a widget with the simulated mouse, like a player would. */
    private void click(String key) {
        double[] position = locate(key).orElseThrow(() -> new AssertionError("No active widget " + key));
        ctx.getInput().setCursorPos(position[0], position[1]);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
    }

    private static void visit(GuiEventListener listener, java.util.function.Consumer<AbstractWidget> action) {
        if (listener instanceof AbstractWidget widget) {
            action.accept(widget);
        }
        if (listener instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                visit(child, action);
            }
        }
    }

    private void open() {
        ctx.setScreen(() -> QolWorkspace.open(Minecraft.getInstance().gui.screen(), null));
        ctx.waitForScreen(UnifiedSettingsScreen.class);
    }

    private void closeIfOpen() {
        if (currentScreen() instanceof UnifiedSettingsScreen) {
            click(WorkspaceKeys.BACK);
        }
        ctx.waitForScreen(TitleScreen.class);
    }

    private Screen currentScreen() {
        return ctx.computeOnClient(mc -> mc.gui.screen());
    }

    private void search(String query) {
        ctx.runOnClient(mc -> {
            Screen screen = mc.gui.screen();
            EditBox box = (EditBox) screen.children().stream().filter(EditBox.class::isInstance).findFirst()
                    .orElseThrow(() -> new AssertionError("no search field"));
            screen.setFocused(box);
            box.setValue("");
        });
        ctx.getInput().typeChars(query);
        ctx.waitTicks(2);
    }

    /** Types into the (only) integer field and presses Enter, which commits a valid value. */
    private void setIntegerField(String text) {
        ctx.runOnClient(mc -> {
            var boxes = new ArrayList<EditBox>();
            collect(mc.gui.screen(), boxes);
            // The search field is a direct child of the screen; setting fields live in the panel.
            boxes.removeIf(b -> mc.gui.screen().children().contains(b));
            check(boxes.size() == 1, "one integer field");
            boxes.getFirst().setValue(text);
            boxes.getFirst().keyPressed(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RETURN, 0, 0));
        });
        ctx.waitTicks(3);
    }

    private static void collect(GuiEventListener listener, List<EditBox> boxes) {
        if (listener instanceof EditBox box) {
            boxes.add(box);
        }
        if (listener instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                collect(child, boxes);
            }
        }
    }

    private void on(java.util.function.Consumer<CoreRuntime> assertion) {
        ctx.runOnClient(mc -> assertion.accept(QolUnifiedClient.core()));
    }

    private void shot(String name) {
        ctx.waitTicks(2);
        ctx.takeScreenshot(scenario + "-" + name);
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("qol_unified").resolve("config.json");
    }

    private static byte[] configBytes() {
        try {
            return Files.exists(configFile()) ? Files.readAllBytes(configFile()) : new byte[0];
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static String config() {
        return new String(configBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
    }
}
