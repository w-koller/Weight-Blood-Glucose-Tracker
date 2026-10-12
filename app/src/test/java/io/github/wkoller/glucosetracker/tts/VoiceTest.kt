package io.github.wkoller.glucosetracker.tts

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class VoiceTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `voice choices survive being saved and read back`() {
        listOf(VoiceChoice.PhoneDefault, VoiceChoice.Phone("en-au-x-aub-local"), VoiceChoice.Natural(7)).forEach {
            assertEquals(it, VoiceChoice.decode(it.encode()))
        }
        assertEquals(VoiceChoice.PhoneDefault, VoiceChoice.decode("natural:not-a-number"))
        assertEquals(VoiceChoice.PhoneDefault, VoiceChoice.decode("something else"))
    }

    @Test
    fun `the audio callback has the exact method sherpa-onnx looks up`() {
        // sherpa-onnx calls GetMethodID(cls, "invoke", "([F)Ljava/lang/Integer;"). Without it the app closed.
        val method = AudioSink { true }.javaClass.getMethod("invoke", FloatArray::class.java)
        assertEquals(Int::class.javaObjectType, method.returnType)
        assertEquals(1, AudioSink { true }.invoke(FloatArray(1)))
        assertEquals(0, AudioSink { false }.invoke(FloatArray(1)))
    }

    private fun archive(vararg entries: Pair<String, String>): File {
        val file = folder.newFile("pack.tar.bz2")
        TarArchiveOutputStream(BZip2CompressorOutputStream(file.outputStream())).use { tar ->
            entries.forEach { (name, text) ->
                val bytes = text.toByteArray()
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = bytes.size.toLong() })
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
        return file
    }

    @Test
    fun `a voice pack unpacks into its folder`() {
        val destination = folder.newFolder("out")
        ArchiveExtractor.extractTarBz2(archive("pack/tokens.txt" to "a b c", "pack/espeak-ng-data/en_dict" to "dict"), destination)
        assertEquals("a b c", File(destination, "pack/tokens.txt").readText())
        assertTrue(File(destination, "pack/espeak-ng-data/en_dict").exists())
    }

    @Test
    fun `an entry that would land outside the folder is refused`() {
        val destination = folder.newFolder("out")
        assertThrows(IOException::class.java) {
            ArchiveExtractor.extractTarBz2(archive("../escaped.txt" to "no"), destination)
        }
        assertFalse(File(destination.parentFile, "escaped.txt").exists())
    }
}
