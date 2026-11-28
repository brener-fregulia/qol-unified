package unified.qol.feature.soundcontrol;

import java.util.Comparator;
import unified.qol.search.SearchQuery;
import unified.qol.search.SearchableText;

/**
 * Search and filter rules for the sound list (sound-control-v1.md §5.4). Free of Minecraft types. Matching is the
 * shared search core's: every word must occur in one field, ignoring case and accents ({@link SearchQuery}).
 */
public final class SoundSearch {
    /** Queries shorter than this list nothing in the All sounds tab unless a filter is set (§6.2). */
    public static final int MIN_QUERY = 2;
    /** The All sounds tab shows at most this many rows; the rest are counted. */
    public static final int MAX_ROWS = 200;

    /**
     * One sound the list can show.
     *
     * @param id full event id
     * @param name friendly name in the current language
     * @param namespace id namespace
     * @param modName mod display name, or the namespace
     * @param category raw category name of the last play, or null when never heard
     * @param categoryLabel localized category name, or null when never heard
     * @param text the folded search fields, always derived from the other components when the candidate is made
     */
    public record Candidate(String id, String name, String namespace, String modName, String category,
            String categoryLabel, SearchableText text) {
        public Candidate {
            text = SearchableText.of(name, id, namespace, modName, category, categoryLabel);
        }

        public Candidate(String id, String name, String namespace, String modName, String category,
                String categoryLabel) {
            this(id, name, namespace, modName, category, categoryLabel, null);
        }
    }

    public static final Comparator<Candidate> BY_NAME = Comparator.comparing(Candidate::name,
            String.CASE_INSENSITIVE_ORDER).thenComparing(Candidate::id);

    private SoundSearch() {
    }

    /**
     * True when every word of {@code query} occurs in the name, id, namespace, mod name or category, and the
     * candidate passes the filters. A null filter accepts everything; a category filter rejects never-heard sounds.
     */
    public static boolean matches(Candidate candidate, SearchQuery query, String categoryFilter,
            String namespaceFilter) {
        if (categoryFilter != null && !categoryFilter.equals(candidate.category())) {
            return false;
        }
        if (namespaceFilter != null && !namespaceFilter.equals(candidate.namespace())) {
            return false;
        }
        return query.matchesAll(candidate.text());
    }

    /** {@link #matches(Candidate, SearchQuery, String, String)} for a raw query (folds it on each call). */
    public static boolean matches(Candidate candidate, String query, String categoryFilter, String namespaceFilter) {
        return matches(candidate, SearchQuery.of(query), categoryFilter, namespaceFilter);
    }

    /** Whether the All sounds tab should list anything for this query and these filters. */
    public static boolean listsAll(String query, String categoryFilter, String namespaceFilter) {
        return categoryFilter != null || namespaceFilter != null || query.strip().length() >= MIN_QUERY;
    }
}
