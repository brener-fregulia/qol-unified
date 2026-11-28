package unified.qol.feature.keyboardmap;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import unified.qol.search.SearchableText;

/** An immutable snapshot of all mappings, indexed by input (keymap-v1.md P-1). Built on open and after changes. */
public final class BindingCatalog {
    private final List<Binding> bindings;
    private final Map<InputId, List<Binding>> byInput = new LinkedHashMap<>();
    private final List<String> categories;
    /** Each binding's folded search fields (name, category, input label), prepared once per snapshot. */
    private final Map<Binding, SearchableText> searchTexts = new IdentityHashMap<>();

    public BindingCatalog(List<Binding> bindings) {
        this.bindings = List.copyOf(bindings);
        var categories = new LinkedHashSet<String>();
        for (Binding binding : this.bindings) {
            categories.add(binding.category());
            searchTexts.put(binding, prepare(binding));
            if (!binding.unbound()) {
                byInput.computeIfAbsent(binding.input(), i -> new ArrayList<>()).add(binding);
            }
        }
        byInput.replaceAll((input, list) -> List.copyOf(list));
        this.categories = List.copyOf(categories);
    }

    /**
     * The binding's prepared search fields: 0 name, 1 category, 2 input label. Bindings of this snapshot are prepared
     * when it is built; any other binding is prepared on the spot.
     */
    public SearchableText searchText(Binding binding) {
        SearchableText text = searchTexts.get(binding);
        return text != null ? text : prepare(binding);
    }

    private static SearchableText prepare(Binding binding) {
        return SearchableText.of(binding.name(), binding.category(), binding.inputLabel());
    }

    public List<Binding> bindings() {
        return bindings;
    }

    /** Category labels in first-seen order (vanilla's sorted order when built from Minecraft). */
    public List<String> categories() {
        return categories;
    }

    public Optional<Binding> binding(String id) {
        return bindings.stream().filter(b -> b.id().equals(id)).findFirst();
    }

    /** Mappings bound to {@code input}, in catalog order. */
    public List<Binding> on(InputId input) {
        return input == null ? List.of() : byInput.getOrDefault(input, List.of());
    }

    public InputState state(InputId input) {
        return InputState.of(on(input).size());
    }

    /** State of a binding's own input; {@link InputState#UNBOUND} when it is unbound. */
    public InputState state(Binding binding) {
        return binding.unbound() ? InputState.UNBOUND : state(binding.input());
    }

    /** Every bound input, keyboard and mouse. */
    public List<InputId> inputs() {
        return List.copyOf(byInput.keySet());
    }

    /** The other mappings already on {@code input}: what binding {@code id} to it would overlap with. */
    public List<Binding> overlapsIfBound(String id, InputId input) {
        return on(input).stream().filter(b -> !b.id().equals(id)).toList();
    }
}
