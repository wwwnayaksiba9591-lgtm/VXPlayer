package com.vxplayer.player

import com.vxplayer.privatefiles.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrivateFilePolicyTest {
    @get:Rule val temporary = TemporaryFolder()
    private val id = "12345678-1234-1234-1234-123456789abc"
    @Test fun pathsStayInsidePrivateAndRejectTraversal() {
        val root = temporary.newFolder("private")
        val layout = PrivateFileLayout(root)
        assertEquals(File(root, "$id.media").canonicalFile, layout.file(id).canonicalFile)
        for (invalid in listOf("../$id", "../../video", "", "C:\\video", "$id/else")) {
            assertThrows(IllegalArgumentException::class.java) { layout.file(invalid) }
        }
        assertThrows(IllegalArgumentException::class.java) { layout.file(id, "/../../other") }
    }
    @Test fun verificationRejectsTruncationAndEmptyCopies() {
        verifyPrivateCopy(10, 10, 10)
        verifyPrivateCopy(-1, 10, 10)
        for (counts in listOf(Triple(10L, 9L, 9L), Triple(10L, 10L, 8L), Triple(0L, 0L, 0L))) {
            assertThrows(IllegalArgumentException::class.java) { verifyPrivateCopy(counts.first, counts.second, counts.third) }
        }
    }
    @Test fun recoveryPromotesOnlyJournaledAndCompleteCopies() {
        val layout = PrivateFileLayout(temporary.newFolder("recovery"))
        val entry = PrivateEntry(id, "Video.mp4", "content://media/external/video/media/1", 3)
        val part = layout.file(id, ".part").apply { writeBytes(byteArrayOf(1, 2)) }
        assertNull(layout.recoverVerifiedCopy(entry))
        assertTrue(part.exists())
        val verified = entry.copy(state = "awaitingDelete")
        assertThrows(IllegalArgumentException::class.java) { layout.recoverVerifiedCopy(verified) }
        assertTrue(part.exists())
        part.writeBytes(byteArrayOf(1, 2, 3))
        val recovered = layout.recoverVerifiedCopy(verified)!!
        assertArrayEquals(byteArrayOf(1, 2, 3), recovered.readBytes())
        assertFalse(part.exists())
        assertEquals(recovered, layout.recoverVerifiedCopy(verified))
    }
    @Test fun pendingAndroidDeletionProtectsTheVerifiedCopy() {
        requirePrivateCopyRemovable("private")
        requirePrivateCopyRemovable("awaitingDelete")
        assertThrows(IllegalStateException::class.java) { requirePrivateCopyRemovable("deletingOriginal") }
    }
    @Test fun copyHandlesLargeFilesAndCancellationWithoutChangingSource() {
        val original = ByteArray(2 * 1024 * 1024 + 7) { (it % 255).toByte() }
        val output = ByteArrayOutputStream()
        val count = copyPrivateBytes(ByteArrayInputStream(original), output, original.size.toLong(), {}, {})
        assertEquals(original.size.toLong(), count)
        assertArrayEquals(original, output.toByteArray())
        assertThrows(IOException::class.java) {
            copyPrivateBytes(object : java.io.InputStream() {
                override fun read(): Int = throw IOException("Source disconnected")
            }, ByteArrayOutputStream(), -1L, {}, {})
        }
    }
    @Test fun copyRejectsWrongExpectedSizeAndHonorsCancellation() {
        assertThrows(IllegalArgumentException::class.java) {
            copyPrivateBytes(ByteArrayInputStream(byteArrayOf(1, 2)), ByteArrayOutputStream(), 3L, {}, {})
        }
        var checks = 0
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            copyPrivateBytes(ByteArrayInputStream(ByteArray(600_000)), ByteArrayOutputStream(), -1L, {}, {
                if (++checks == 2) throw java.util.concurrent.CancellationException()
            })
        }
    }
    @Test fun locksAfterThirtySecondsButNotAfterShortAbsence() {
        val policy = PrivateLockPolicy()
        assertFalse(policy.unlocked)
        policy.unlock(); policy.background(1000); policy.foreground(30_999)
        assertTrue(policy.unlocked)
        policy.background(40_000); policy.foreground(70_000)
        assertFalse(policy.unlocked)
        policy.unlock(); policy.lock(); assertFalse(policy.unlocked)
        assertFalse(PrivateLockPolicy().unlocked)
    }
}
