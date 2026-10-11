package com.github.andreyasadchy.xtra.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDragListMoveTest {
    @Test fun `a drag that skips rows keeps the rows in between in order`() {
        val list = mutableListOf("A", "B", "C", "D")
        val moves = mutableListOf<Pair<Int, Int>>()
        moveDragListItem(list, 0, 3) { from, to -> moves.add(from to to) }
        assertEquals(listOf("B", "C", "D", "A"), list)
        assertEquals(listOf(0 to 1, 1 to 2, 2 to 3), moves)
        moveDragListItem(list, 3, 1) { from, to -> moves.add(from to to) }
        assertEquals(listOf("B", "A", "C", "D"), list)
    }

    @Test fun `unbound positions are ignored`() {
        val list = mutableListOf("A", "B")
        moveDragListItem(list, -1, 1) { _, _ -> error("not moved") }
        moveDragListItem(list, 1, 1) { _, _ -> error("not moved") }
        assertEquals(listOf("A", "B"), list)
    }
}
