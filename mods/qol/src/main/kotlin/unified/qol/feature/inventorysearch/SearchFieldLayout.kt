package unified.qol.feature.inventorysearch

/**
 * Where the search control goes on a container screen (inventory-search-v1.md §5.1). Pure geometry in GUI pixels, so
 * every rule is unit-tested. Minecraft 26.3 facts it relies on (verified in the decompiled source): titles are drawn
 * at (titleLabelX, 6) with a 9 px font; the first slot row's border starts at y 17; slots span x 7–169 of a 176 px
 * container; `topPos = (height − imageHeight) / 2`.
 */
object SearchFieldLayout {
    const val HEIGHT = 12
    const val MAX_WIDTH = 88
    const val MIN_WIDTH = 50
    const val ICON_SIZE = 12
    /** The title row's top; the field ends at y 16, above the slot borders at y 17. */
    const val ROW_Y = 4
    const val GAP = 4
    /** The right inner margin of a 176 px container (slots end at x 169). */
    const val RIGHT_MARGIN = 7

    enum class Kind {
        /** Chests, barrels, Ender Chests, Shulker Boxes, hoppers: the title row first, then above the container. */
        CONTAINER,
        /** The player inventory: its title row holds the player model and armor, so above the container first. */
        INVENTORY,
    }

    enum class Placement { TITLE_ROW, ABOVE, COLLAPSED }

    /** A rectangle in screen GUI coordinates. A [Placement.COLLAPSED] box is the search icon button. */
    data class Box(val x: Int, val y: Int, val width: Int, val height: Int, val placement: Placement)

    /**
     * @param titleX the title's x inside the container (`titleLabelX`)
     * @param titleWidth the title's rendered width, 0 when there is none
     */
    @JvmStatic
    fun place(kind: Kind, left: Int, top: Int, imageWidth: Int, titleX: Int, titleWidth: Int): Box {
        val right = left + imageWidth - RIGHT_MARGIN
        val inRow = right - (left + titleX + titleWidth + GAP)
        val above = top >= HEIGHT + 2
        if (kind == Kind.CONTAINER && inRow >= MIN_WIDTH) {
            val width = minOf(MAX_WIDTH, inRow)
            return Box(right - width, top + ROW_Y, width, HEIGHT, Placement.TITLE_ROW)
        }
        if (above) {
            return Box(right - MAX_WIDTH, top - HEIGHT - 2, MAX_WIDTH, HEIGHT, Placement.ABOVE)
        }
        return Box(right - ICON_SIZE, top + ROW_Y, ICON_SIZE, ICON_SIZE, Placement.COLLAPSED)
    }

    /**
     * The field shown after the collapsed icon is opened: the title row's right part, as wide as allowed. It covers
     * the end of the title only while the player is searching, and never a slot.
     */
    @JvmStatic
    fun expanded(left: Int, top: Int, imageWidth: Int): Box {
        val right = left + imageWidth - RIGHT_MARGIN
        val width = minOf(MAX_WIDTH, imageWidth - 2 * RIGHT_MARGIN)
        return Box(right - width, top + ROW_Y, width, HEIGHT, Placement.TITLE_ROW)
    }
}
