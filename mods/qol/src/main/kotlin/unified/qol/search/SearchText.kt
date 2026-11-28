package unified.qol.search

import java.text.Normalizer
import java.util.Locale

/**
 * The text normalization every Unified: QoL search shares (docs/discovery/unified-search-architecture.md §6.1).
 *
 * [fold] turns text into its search form: Unicode NFD, combining marks removed (so "Música" becomes "musica" and a
 * pre-composed "é" equals "e" + U+0301), runs of whitespace collapsed to one space and trimmed, then lower case with
 * `Locale.ROOT`. It is the same rule the Unified Settings workspace and Keyboard Map used, plus whitespace collapsing.
 * Compatibility forms (NFKD: ligatures, full-width letters, superscripts) are deliberately not folded, so "²" stays
 * distinct from "2". The text comes from the consumer in the active game language; nothing is translated here.
 *
 * Pure and thread-safe; it allocates, so callers fold once when their data changes, never per frame.
 */
object SearchText {
    @JvmStatic
    fun fold(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val decomposed = if (isAscii(text)) text else Normalizer.normalize(text, Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        var pendingSpace = false
        var i = 0
        while (i < decomposed.length) {
            val cp = decomposed.codePointAt(i)
            i += Character.charCount(cp)
            when {
                isMark(cp) -> Unit
                Character.isWhitespace(cp) || Character.isSpaceChar(cp) -> if (out.isNotEmpty()) pendingSpace = true
                else -> {
                    if (pendingSpace) {
                        out.append(' ')
                        pendingSpace = false
                    }
                    out.appendCodePoint(cp)
                }
            }
        }
        return out.toString().lowercase(Locale.ROOT)
    }

    private fun isAscii(text: String): Boolean = text.all { it.code < 0x80 }

    /** `\p{M}`: the combining marks NFD splits off letters. */
    private fun isMark(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
        Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
        else -> false
    }
}
