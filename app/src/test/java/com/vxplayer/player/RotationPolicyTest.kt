package com.vxplayer.player
import org.junit.Assert.*
import org.junit.Test
class RotationPolicyTest {
    @Test fun cycleContainsAllFourDirectionsAndAuto() {
        var mode=RotationMode.Auto
        val seen=mutableSetOf<RotationMode>()
        repeat(5){seen+=mode;mode=mode.next()}
        assertEquals(RotationMode.entries.toSet(),seen);assertEquals(RotationMode.Auto,mode)
        assertEquals(RotationMode.Auto,RotationMode.parse("corrupt"))
        assertEquals(RotationMode.ReversePortrait,RotationMode.parse("Reverse Portrait"))
    }
    @Test fun lockingPreservesReverseAndUnlockReturnsToFullSensor() {
        val controller=OrientationController(RotationMode.Auto,false,10)
        controller.toggleLock(currentOrientation(false,2));assertTrue(controller.locked);assertEquals(9,controller.requested)
        controller.toggleLock(0);assertFalse(controller.locked);assertEquals(10,controller.requested)
        assertEquals(8,currentOrientation(true,3));assertEquals(0,currentOrientation(true,1))
    }
}
