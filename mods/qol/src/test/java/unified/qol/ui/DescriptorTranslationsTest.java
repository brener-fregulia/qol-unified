package unified.qol.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import unified.qol.Features;
import unified.qol.core.ApplyMode;
import unified.qol.core.Feature;
import unified.qol.core.FeatureCategory;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.SettingDescriptor;

/**
 * Java calling the Kotlin {@link DescriptorTranslations} (which itself reads the Java descriptor API): every
 * production feature has all of its settings texts in both languages.
 */
class DescriptorTranslationsTest {
    private Set<String> keys(String lang) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/assets/qol_unified/lang/" + lang + ".json")) {
            return new Gson().fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class)
                    .keySet();
        }
    }

    @Test
    void kotlinListsTheKeysOfAJavaDescriptor() {
        var descriptor = new FeatureDescriptor("demo", FeatureCategory.AUDIO, true, ApplyMode.LIVE,
                List.of(new SettingDescriptor.EnumSetting("mode", "a", List.of("a", "b"), ApplyMode.LIVE)),
                List.of());
        assertEquals(List.of("qol_unified.feature.demo.name", "qol_unified.feature.demo.description",
                "qol_unified.feature.demo.setting.mode", "qol_unified.feature.demo.setting.mode.a",
                "qol_unified.feature.demo.setting.mode.b"), DescriptorTranslations.requiredKeys(descriptor));
        assertEquals(List.of("qol_unified.feature.demo.setting.mode.b"),
                DescriptorTranslations.missing(descriptor, Set.copyOf(
                        DescriptorTranslations.requiredKeys(descriptor).subList(0, 4))));
    }

    @Test
    void everyProductionFeatureIsTranslatedInBothLanguages() throws Exception {
        Set<String> en = keys("en_us");
        Set<String> pt = keys("pt_br");
        var features = Features.createRegistry().features();
        assertEquals(9, features.size());
        for (Feature feature : features) {
            FeatureDescriptor d = feature.descriptor();
            assertTrue(DescriptorTranslations.missing(d, en).isEmpty(), d.id() + " en_us");
            assertTrue(DescriptorTranslations.missing(d, pt).isEmpty(), d.id() + " pt_br");
        }
    }
}
