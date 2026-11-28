package unified.qol.search

/**
 * A search query in its folded form, made once each time the player's text changes.
 *
 * [text] is the whole query folded by [SearchText.fold] (so surrounding and repeated whitespace is gone); [terms] are
 * its space-separated words. Two match rules exist, both plain substring tests (no regular expressions, no query
 * language):
 * - [matchesAll]: every term occurs in some field (AND of words, any order). The default for new searches.
 * - [matchesPhrase]: the whole text occurs inside one field.
 *
 * An empty query matches everything with either rule; consumers decide what an empty query shows. Matching is pure, so
 * results depend only on the query and the prepared fields; ordering stays with the consumer. Immutable.
 */
class SearchQuery private constructor(
    @get:JvmName("text") val text: String,
    @get:JvmName("terms") val terms: List<String>,
) {
    fun isEmpty(): Boolean = text.isEmpty()

    fun matchesAll(fields: SearchableText): Boolean = terms.all(fields::contains)

    fun matchesPhrase(fields: SearchableText): Boolean = fields.contains(text)

    /**
     * The rest of the query after [prefix] (compared folded, so `KEY:` equals `key:`), as its own query; null when the
     * query does not start with it. Used for consumer-defined filters such as Keyboard Map's `key:`.
     */
    fun afterPrefix(prefix: String): SearchQuery? {
        val folded = SearchText.fold(prefix)
        return if (folded.isNotEmpty() && text.startsWith(folded)) of(text.substring(folded.length)) else null
    }

    override fun equals(other: Any?): Boolean = other is SearchQuery && other.text == text

    override fun hashCode(): Int = text.hashCode()

    override fun toString(): String = "SearchQuery[$text]"

    companion object {
        @JvmField
        val EMPTY = SearchQuery("", emptyList())

        @JvmStatic
        fun of(raw: String?): SearchQuery {
            val text = SearchText.fold(raw)
            return if (text.isEmpty()) EMPTY else SearchQuery(text, text.split(' '))
        }
    }
}
