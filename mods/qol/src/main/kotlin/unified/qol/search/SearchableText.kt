package unified.qol.search

/**
 * The prepared, folded fields of one searchable entry (a binding's name, category and key; a sound's name and id).
 *
 * Immutable. The consumer that builds it owns it and keeps it with the data it describes, rebuilding it only when
 * that data or its translated labels change (a new snapshot, a language reload). There is no global cache. Field
 * order is the consumer's, so it can also test a single field with [field].
 */
class SearchableText private constructor(private val fields: List<String>) {
    /** The number of fields, including empty ones. */
    fun size(): Int = fields.size

    /** The folded text of field [index], in the order given to [of]; empty for a null or blank field. */
    fun field(index: Int): String = fields[index]

    /** Whether [foldedTerm] occurs inside any one field. A term never spans two fields. */
    fun contains(foldedTerm: String): Boolean = fields.any { it.contains(foldedTerm) }

    override fun equals(other: Any?): Boolean = other is SearchableText && other.fields == fields

    override fun hashCode(): Int = fields.hashCode()

    override fun toString(): String = "SearchableText$fields"

    companion object {
        @JvmField
        val EMPTY = SearchableText(emptyList())

        /** Folds each field once ([SearchText.fold]); a null field becomes empty and keeps its position. */
        @JvmStatic
        fun of(vararg fields: String?): SearchableText = SearchableText(fields.map(SearchText::fold))

        @JvmStatic
        fun of(fields: Collection<String?>): SearchableText = SearchableText(fields.map(SearchText::fold))
    }
}
