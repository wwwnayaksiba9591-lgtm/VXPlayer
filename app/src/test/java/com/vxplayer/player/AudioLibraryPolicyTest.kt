package com.vxplayer.player

import com.vxplayer.music.AudioLibraryPolicy
import org.junit.Assert.*
import org.junit.Test

class AudioLibraryPolicyTest {
    @Test fun includesVoiceNotesRecordingsAndAllRequestedFormats() {
        listOf("mp3","m4a","aac","flac","wav","ogg","opus","amr","wma").forEach {
            assertTrue(AudioLibraryPolicy.isAudio("application/octet-stream","WhatsApp Audio/voice.$it"))
        }
        assertTrue(AudioLibraryPolicy.isAudio("audio/custom","unknown"))
        assertFalse(AudioLibraryPolicy.isAudio("video/mp4","movie.mp4"))
        assertFalse(AudioLibraryPolicy.isAudio("application/pdf","note.pdf"))
    }
    @Test fun shortUnknownAndRecordingMetadataIsMappedWithoutHidingIt() {
        val song = AudioLibraryPolicy.map("content://media/internal/audio/media/1",null,"voice.opus","<unknown>","",750,
            volume="internal",relativePath="Recordings/")
        assertEquals("voice.opus",song.title);assertEquals(750L,song.duration);assertEquals("Unknown artist",song.artist)
        assertEquals("Recordings/",song.relativePath)
        assertEquals(0L,AudioLibraryPolicy.map("x","","ring.amr",null,null,-1,volume="external").duration)
    }
}
