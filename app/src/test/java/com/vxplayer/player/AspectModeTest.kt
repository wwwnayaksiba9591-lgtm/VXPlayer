package com.vxplayer.player
import org.junit.Assert.*
import org.junit.Test
class AspectModeTest {
    @Test fun allModesCycleAndLegacyOriginalSurvives() {
        var mode=AspectMode.Fit;val seen=mutableSetOf<AspectMode>()
        repeat(6){seen+=mode;mode=mode.next()}
        assertEquals(AspectMode.entries.toSet(),seen);assertEquals(AspectMode.Fit,mode)
        assertEquals(AspectMode.Original,AspectMode.parse(null,3));assertEquals(AspectMode.Fit,AspectMode.parse("bad",99))
        assertEquals(16f/9,AspectMode.Wide.ratio,0f)
    }
}
