package com.vxplayer.player
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.*
import org.junit.Test
class ActivityResultCompatibilityTest {
    @Test fun fragmentActivityDoesNotContainTheLegacySixteenBitRequestCodeValidator() {
        val type=Class.forName("androidx.fragment.app.FragmentActivity",false,FragmentActivity::class.java.classLoader)
        assertFalse("Modern Activity Result launchers require Fragment 1.3+",type.declaredMethods.any {it.name=="checkForValidRequestCode"})
    }
}
