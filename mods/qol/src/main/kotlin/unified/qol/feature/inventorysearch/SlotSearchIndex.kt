package unified.qol.feature.inventorysearch

import java.util.BitSet
import java.util.function.IntFunction
import unified.qol.search.SearchQuery
import unified.qol.search.SearchableText

/**
 * The prepared search text of one open screen's slots (inventory-search-v1.md §6). Owned by that screen's
 * [InventorySearchSession]; there is no global cache.
 *
 * [refresh] runs at most once per client tick (and when the screen opens). A slot is prepared again only when its entry
 * is no longer [same] as the stored snapshot (another item or other components; a count change is not a change), or
 * when the `context` changes (the game language): then every slot is prepared again. Nothing is prepared per frame.
 * [matches] is cached until the query or the index changes, so drawing only reads a [SlotMatches].
 *
 * Free of Minecraft types: `T` is an item stack in game and a plain value in unit tests.
 */
class SlotSearchIndex<T : Any>(
    private val same: (stored: T, current: T) -> Boolean,
    private val snapshot: (T) -> T,
    private val prepare: (T) -> SearchableText,
) {
    private var context: Any? = null
    private val stored = ArrayList<T?>()
    private val texts = ArrayList<SearchableText?>()
    private var lastMatches: SlotMatches? = null

    /** Incremented whenever a slot's prepared text changes, appears or disappears. */
    var version: Int = 0
        private set

    /** How many slots have been prepared so far (for tests and measurements). */
    var preparedCount: Long = 0
        private set

    /** The number of slots currently indexed. */
    fun size(): Int = texts.size

    /**
     * Brings the index up to date with [size] slots; [entry] gives slot `i`'s current value, or null when it is empty.
     * Returns whether anything changed.
     */
    fun refresh(size: Int, context: Any, entry: IntFunction<T?>): Boolean {
        var changed = false
        if (context != this.context) {
            this.context = context
            stored.clear()
            texts.clear()
            changed = true
        }
        while (texts.size > size) {
            stored.removeAt(stored.size - 1)
            texts.removeAt(texts.size - 1)
            changed = true
        }
        for (i in 0 until size) {
            val current = entry.apply(i)
            if (i >= texts.size) {
                stored.add(null)
                texts.add(null)
            }
            val old = stored[i]
            if (current == null) {
                if (old != null || texts[i] != null) {
                    stored[i] = null
                    texts[i] = null
                    changed = true
                }
            } else if (old == null || !same(old, current)) {
                stored[i] = snapshot(current)
                texts[i] = prepare(current)
                preparedCount++
                changed = true
            }
        }
        if (changed) {
            version++
        }
        return changed
    }

    /** The prepared text of slot [index], or null when it is empty or not indexed. */
    fun text(index: Int): SearchableText? = texts.getOrNull(index)

    /** The slots matching [query]; recomputed only when the query or the index changed. */
    fun matches(query: SearchQuery): SlotMatches {
        val last = lastMatches
        if (last != null && last.version == version && last.query == query) {
            return last
        }
        val occupied = BitSet(texts.size)
        val matched = BitSet(texts.size)
        for (i in texts.indices) {
            val text = texts[i] ?: continue
            occupied.set(i)
            if (!query.isEmpty() && query.matchesAll(text)) {
                matched.set(i)
            }
        }
        return SlotMatches(query, version, occupied, matched).also { lastMatches = it }
    }
}

/**
 * The result of one query over one index version. Read-only after construction; drawing reads it every frame without
 * allocating. With an empty query nothing is marked, so the screen looks exactly like vanilla.
 */
class SlotMatches internal constructor(
    @get:JvmName("query") val query: SearchQuery,
    internal val version: Int,
    private val occupied: BitSet,
    private val matched: BitSet,
) {
    /** The number of occupied slots that match. */
    @get:JvmName("count")
    val count: Int = matched.cardinality()

    /** Whether a query is active, so slots are marked at all. */
    fun active(): Boolean = !query.isEmpty()

    fun isMatch(index: Int): Boolean = matched.get(index)

    /** An occupied slot that does not match while a query is active: drawn dimmed. Empty slots are never dimmed. */
    fun isDimmed(index: Int): Boolean = active() && occupied.get(index) && !matched.get(index)
}
