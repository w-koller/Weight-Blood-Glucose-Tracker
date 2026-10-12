package io.github.wkoller.glucosetracker.tts

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.IOException

/** Unpacks a .tar.bz2 archive, refusing any entry that would land outside the destination. */
object ArchiveExtractor {

    fun extractTarBz2(archive: File, destination: File) {
        destination.mkdirs()
        val root = destination.canonicalFile
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered(1 shl 16))).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val target = File(root, entry.name).canonicalFile
                if (target != root && !target.path.startsWith(root.path + File.separator)) {
                    throw IOException("Archive entry outside the destination: ${entry.name}")
                }
                when {
                    entry.isDirectory -> target.mkdirs()
                    entry.isFile -> {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { tar.copyTo(it) }
                    }
                    // Links and other special entries are not needed by the voice model, so they are skipped.
                    else -> Unit
                }
            }
        }
    }
}
