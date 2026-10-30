package com.vxplayer.player
import org.junit.Assert.*
import org.junit.Test

class SeekRequestsTest {
    @Test fun rapidRequestsYieldOneFinalSeek() {
        val requests = SeekRequests()
        requests.request(1000,100000,0); requests.request(5000,100000,30); requests.request(8000,100000,60)
        assertNull(requests.take(100)); assertEquals(8000L,requests.take(150)); assertNull(requests.take(200))
    }
    @Test fun cancellationAndNewerRequestReplacePendingWork() {
        val requests = SeekRequests()
        requests.request(1000,100000,0); requests.cancel(); assertNull(requests.take(300))
        requests.request(2000,100000,400); assertEquals(2000L,requests.take(500))
    }
    @Test fun clampsKnownAndUnknownDurationsAndSupportsRelativeAccumulation() {
        val requests = SeekRequests()
        requests.request(-1,-9223372036854775807L,0); assertEquals(0L,requests.take(100))
        requests.request(8000,5000,200); assertEquals(5000L,requests.take(300))
        requests.request(8000,0,400); requests.request(seekWithOffset(requests.pending!!,1000,0),0,450)
        assertEquals(9000L,requests.take(600))
    }
}
