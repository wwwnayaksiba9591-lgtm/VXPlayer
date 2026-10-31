package com.vxplayer.player
import com.vxplayer.cast.*
import org.junit.Assert.*
import org.junit.Test
class HttpRangeTest {
    @Test fun wholePrefixSuffixAndOpenEndedRanges() {
        assertEquals(HttpRange(0,99),parseHttpRange(null,100))
        assertEquals(HttpRange(20,30),parseHttpRange("bytes=20-30",100))
        assertEquals(HttpRange(90,99),parseHttpRange("bytes=-10",100))
        assertEquals(HttpRange(20,99),parseHttpRange("bytes=20-",100))
        assertEquals(HttpRange(0,99),parseHttpRange("bytes=-200",100))
    }
    @Test fun invalidRangesNeverEscapeTheFile() {
        listOf("bytes=100-","bytes=20-10","bytes=-0","bytes=-","bytes=0-1,4-5","bytes=999999999999999999999-", "items=0-1").forEach {assertNull(parseHttpRange(it,100))}
        assertNull(parseHttpRange(null,0))
    }
}
