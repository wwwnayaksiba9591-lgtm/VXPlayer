package com.vxplayer.player
import com.vxplayer.library.*
import org.junit.Assert.assertEquals
import org.junit.Test
class LibrarySortTest {
    data class Item(val name: String, val date: Long, val size: Long, val duration: Long)
    private val items = listOf(Item("Z", 1, 30, 2), Item("a", 3, 10, 4), Item("B", 2, 20, 3))
    private fun sorted(key: LibrarySort, descending: Boolean = false) = orderLibrary(items, key, descending, { it.name }, { it.date }, { it.size }, { it.duration }).map { it.name }
    @Test fun sortsNamesWithoutCaseAndCanReverse() {
        assertEquals(listOf("a", "B", "Z"), sorted(LibrarySort.Name))
        assertEquals(listOf("Z", "B", "a"), sorted(LibrarySort.Name, true))
    }
    @Test fun sortsNumericMetadata() {
        assertEquals(listOf("Z", "B", "a"), sorted(LibrarySort.Date))
        assertEquals(listOf("a", "B", "Z"), sorted(LibrarySort.Size))
        assertEquals(listOf("Z", "B", "a"), sorted(LibrarySort.Duration))
    }
    @Test fun newestFirstUsesDescendingDateAndKeepsUnknownDatesLast() {
        assertEquals(listOf("a", "B", "Z"), sorted(LibrarySort.Date, true))
        val withUnknown=items+Item("Unknown",0,0,0)
        assertEquals(listOf("a", "B", "Z", "Unknown"),orderLibrary(withUnknown,LibrarySort.Date,true,
            {it.name},{it.date},{it.size},{it.duration}).map {it.name})
    }
}
