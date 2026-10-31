package com.vxplayer.player
import com.vxplayer.ui.*
import org.junit.Assert.*
import org.junit.Test

class QuickActionPolicyTest {
    @Test fun removedActionIsIgnoredWithoutLosingOtherSavedChoices() {
        val layout=parseQuickActions("Together,Mute,Speed","Together,Night")
        assertEquals(listOf(PlayerAction.Mute,PlayerAction.Speed),layout.order.take(2))
        assertEquals(setOf(PlayerAction.Night),layout.hidden)
        assertEquals(PlayerAction.entries.toSet(),layout.order.toSet())
    }
    @Test fun onlyAnInwardHorizontalDragFromTheRightEdgeOpensThePanel() {
        assertTrue(opensActionPanel(390f,400f,-40f,5f))
        assertFalse(opensActionPanel(300f,400f,-40f,5f))
        assertFalse(opensActionPanel(390f,400f,40f,5f))
        assertFalse(opensActionPanel(390f,400f,-10f,5f))
        assertFalse(opensActionPanel(390f,400f,-20f,40f))
    }
    @Test fun storedOrderAndVisibilitySurviveUnknownOrDuplicateItems() {
        val layout=parseQuickActions("Mute,unknown,Shuffle,Mute","Night,bad,Night")
        assertEquals(listOf(PlayerAction.Mute,PlayerAction.Shuffle),layout.order.take(2))
        assertEquals(PlayerAction.entries.toSet(),layout.order.toSet());assertEquals(PlayerAction.entries.size,layout.order.size)
        assertEquals(setOf(PlayerAction.Night),layout.hidden)
        assertEquals(QuickActionLayout(),parseQuickActions(null,null))
    }
    @Test fun reorderVisibilityAndBoundaryMovesAreStable() {
        val original=QuickActionLayout()
        assertEquals(original,original.move(PlayerAction.Night,-1))
        assertEquals(PlayerAction.Customise,original.move(PlayerAction.Customise,-1).order.first())
        assertTrue(original.visible(PlayerAction.Mute,false).hidden.contains(PlayerAction.Mute))
        assertTrue(original.visible(PlayerAction.Mute,false).visible(PlayerAction.Mute,true).hidden.isEmpty())
        assertEquals("1X",speedActionLabel(1f));assertEquals("1.25X",speedActionLabel(1.25f));assertEquals("1X",speedActionLabel(Float.NaN))
    }
}
