package com.vxplayer.player

import com.vxplayer.music.*
import org.junit.Assert.*
import org.junit.Test

class MusicErrorPolicyTest {
    @Test fun skipsBadTracksWithoutRepeatingOrLoopingAnUnplayableQueue() {
        val ids=listOf("bad","missing","good")
        assertEquals(2,nextMusicAfterError(ids,0,setOf("bad","missing")))
        assertNull(nextMusicAfterError(ids,2,ids.toSet(),wrap=true))
        assertNull(nextMusicAfterError(emptyList(),0,emptySet()))
        assertNull(nextMusicAfterError(listOf("bad"),0,setOf("bad"),wrap=true))
    }
    @Test fun respectsShuffleOrderAndRepeatAllBoundary() {
        val ids=listOf("first","second","last")
        assertEquals(0,nextMusicAfterError(ids,2,setOf("last"),listOf(2,0,1)))
        assertNull(nextMusicAfterError(ids,2,setOf("last")))
        assertEquals(0,nextMusicAfterError(ids,2,setOf("last"),wrap=true))
        assertEquals(1,nextMusicAfterError(ids,0,setOf("first"),listOf(-1,0,0,1,999)))
    }
    @Test fun foldersSeparateVolumesAndUseReadableNames() {
        val song=Song("uri","Voice",volume="external",relativePath="WhatsApp/Media/Voice Notes/")
        assertEquals("Voice Notes",song.folderLabel())
        assertNotEquals(song.folderKey(),song.copy(volume="internal").folderKey())
        assertEquals("Internal audio",song.copy(volume="internal",relativePath="").folderLabel())
    }
}
