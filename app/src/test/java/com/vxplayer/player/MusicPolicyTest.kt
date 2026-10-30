package com.vxplayer.player
import com.vxplayer.music.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MusicPolicyTest {
    @Test fun queueInsertRemoveReorderAndEmptyAreSafe() {
        val queue=listOf("a","b","c")
        assertEquals(listOf("a","x","b","c"),insertMusic(queue,0,"x",true))
        assertEquals(listOf("a","b","c","x"),insertMusic(queue,0,"x",false))
        assertEquals(listOf("a","c"),removeMusic(queue,1))
        assertEquals(listOf("b","c","a"),reorderMusic(queue,0,2))
        assertEquals(queue,reorderMusic(queue,-1,3));assertTrue(removeMusic(emptyList<String>(),0).isEmpty())
        assertEquals(listOf("x"),insertMusic(emptyList(),-1,"x",true))
    }
    @Test fun playlistsDoNotDuplicateAndShuffleVisitsEveryTrack() {
        assertEquals(listOf("a","b"),addPlaylistSong(listOf("a","b"),"a"))
        val shuffled=shuffledMusic(listOf("a","b","c","d"),"c",Random(42))
        assertEquals("c",shuffled.first());assertEquals(setOf("a","b","c","d"),shuffled.toSet());assertEquals(4,shuffled.size)
        assertEquals(listOf("a"),shuffledMusic(listOf("a"),"a"));assertTrue(shuffledMusic(emptyList<String>(),null).isEmpty())
        assertEquals(2,nextMusicRepeat(0));assertEquals(1,nextMusicRepeat(2));assertEquals(0,nextMusicRepeat(1))
    }
    @Test fun lrcHandlesMultipleTagsOffsetsFractionsAndMalformedLines() {
        val lines=parseLrc("[offset:-200]\n[00:00.10]Start\n[00:01.5][00:03.050]Repeat\n[00:80.00]bad\n[garbage]ignored\n[00:04.00]\n[00:05]End")
        assertEquals(listOf(0L,1300L,2850L,4800L),lines.map {it.timeMs});assertEquals("Repeat",lines[2].text)
        assertEquals(1,activeLyric(lines,2000));assertEquals(-1,activeLyric(emptyList(),0))
    }
    @Test fun sortIsStableAndAlbumTracksAreOrdered() {
        val songs=listOf(Song("b","Zulu",album="A",track=2,added=3),Song("a","Alpha",album="A",track=1,added=1))
        assertEquals("a",sortSongs(songs,MusicSort.Title).first().uri)
        assertEquals("a",sortSongs(songs,MusicSort.Album).first().uri)
        assertEquals("b",sortSongs(songs,MusicSort.RecentlyAdded).first().uri)
    }
}
