package com.vxplayer.player
import org.junit.Assert.*
import org.junit.Test
class TenSecondSeekTest {
    @Test fun tenSecondSkipsClampAndHandleUnknownLength() {
        assertEquals(15_000L,seekWithOffset(5000,10_000,100_000))
        assertEquals(0L,seekWithOffset(5000,-10_000,100_000))
        assertEquals(100_000L,seekWithOffset(95_000,10_000,100_000))
        assertEquals(15_000L,seekWithOffset(5000,10_000,-9223372036854775807L))
        assertEquals(Long.MAX_VALUE,seekWithOffset(Long.MAX_VALUE-10,10_000,0))
    }
}
