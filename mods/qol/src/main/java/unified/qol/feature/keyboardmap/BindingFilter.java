package unified.qol.feature.keyboardmap;

import java.util.Objects;
import unified.qol.search.SearchQuery;
import unified.qol.search.SearchableText;

/**
 * Search and filters, combined with AND (keymap-v1.md I-4 to I-7). The query matches the action name, the category
 * and the input label, ignoring case and accents; {@code key:<text>} matches only the input label. The query is a
 * phrase: the whole text must occur in one of those fields (unchanged since V1). Folding is the shared search core's
 * ({@link SearchQuery}). Immutable: the query is folded once, when the filter is made, never per match.
 */
public final class BindingFilter {
    public static final String KEY_PREFIX = "key:";

    /** The {@code default_filter} values. */
    public enum Visibility {
        ALL("all"),
        OVERLAPS("overlaps"),
        ASSIGNED("assigned"),
        UNBOUND("unbound");

        private final String id;

        Visibility(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Visibility byId(String id) {
            for (Visibility v : values()) {
                if (v.id.equals(id)) {
                    return v;
                }
            }
            return ALL;
        }
    }

    private final String query;
    private final String category;
    private final Visibility visibility;
    private final SearchQuery prepared;
    /** The text after {@code key:}, or null when the query is not a key filter. */
    private final SearchQuery keyQuery;

    public BindingFilter(String query, String category, Visibility visibility) {
        this.query = query == null ? "" : query;
        this.category = category;
        this.visibility = visibility == null ? Visibility.ALL : visibility;
        this.prepared = SearchQuery.of(this.query);
        this.keyQuery = prepared.afterPrefix(KEY_PREFIX);
    }

    public String query() {
        return query;
    }

    public String category() {
        return category;
    }

    public Visibility visibility() {
        return visibility;
    }

    /** Whether this filter was made from the same inputs, so a caller can keep it instead of making a new one. */
    public boolean sameAs(String query, String category, Visibility visibility) {
        return this.query.equals(query == null ? "" : query) && Objects.equals(this.category, category)
                && this.visibility == (visibility == null ? Visibility.ALL : visibility);
    }

    public static BindingFilter all() {
        return new BindingFilter("", null, Visibility.ALL);
    }

    public boolean isEmpty() {
        return query.isBlank() && category == null && visibility == Visibility.ALL;
    }

    public boolean matches(Binding binding, BindingCatalog catalog) {
        if (category != null && !category.equals(binding.category())) {
            return false;
        }
        boolean visible = switch (visibility) {
            case ALL -> true;
            case OVERLAPS -> catalog.state(binding) == InputState.OVERLAP;
            case ASSIGNED -> !binding.unbound();
            case UNBOUND -> binding.unbound();
        };
        if (!visible) {
            return false;
        }
        if (prepared.isEmpty()) {
            return true;
        }
        // Fields: 0 name, 1 category, 2 input label (BindingCatalog#searchText).
        SearchableText text = catalog.searchText(binding);
        if (keyQuery != null) {
            return !binding.unbound() && (keyQuery.isEmpty() || text.field(2).contains(keyQuery.text()));
        }
        return text.field(0).contains(prepared.text()) || text.field(1).contains(prepared.text())
                || (!binding.unbound() && text.field(2).contains(prepared.text()));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BindingFilter f && sameAs(f.query, f.category, f.visibility);
    }

    @Override
    public int hashCode() {
        return Objects.hash(query, category, visibility);
    }

    @Override
    public String toString() {
        return "BindingFilter[query=" + query + ", category=" + category + ", visibility=" + visibility + "]";
    }
}
