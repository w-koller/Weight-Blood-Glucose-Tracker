package io.github.wkoller.glucosetracker.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import io.github.wkoller.glucosetracker.CrashReporter
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.download.DownloadSpec
import io.github.wkoller.glucosetracker.download.DownloadState
import io.github.wkoller.glucosetracker.download.VerifiedDownload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Receives each piece of audio from sherpa-onnx. Its native code looks up a method named invoke that
 * takes a float[] and returns an Integer. A Kotlin 2 lambda does not have that exact method, so the
 * lookup failed and the app closed. A named class does have it. Returning false stops the speech.
 */
class AudioSink(private val write: (FloatArray) -> Boolean) : (FloatArray) -> Int {
    override fun invoke(samples: FloatArray): Int = if (write(samples)) 1 else 0
}

sealed interface NaturalVoiceState {
    data object NotInstalled : NaturalVoiceState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : NaturalVoiceState
    data object WaitingForWifi : NaturalVoiceState
    data object Installing : NaturalVoiceState
    data object Ready : NaturalVoiceState
    data class Failed(val reason: Reason) : NaturalVoiceState

    enum class Reason { NOT_ENOUGH_SPACE, DOWNLOAD_FAILED, CHECK_FAILED, INSTALL_FAILED }
}

/** A voice in the natural voice model. [id] is its speaker number in the model. */
data class NaturalSpeaker(val id: Int, val name: String, val british: Boolean)

/**
 * Optional natural voices: the Kokoro-82M model (Apache 2.0), run on the phone by sherpa-onnx. The voice
 * pack is downloaded on Wi-Fi, checked, unpacked into the app's own storage and then deleted. The model
 * is loaded only while it is speaking and released after two idle minutes.
 */
class NaturalVoices(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engineLock = Mutex()
    private var engine: OfflineTts? = null
    private var track: AudioTrack? = null
    private var speakJob: Job? = null
    private var idleJob: Job? = null

    /** Moves on with Stop and with each new request, so speech that has been replaced ends at its next chance. */
    private val requests = AtomicLong()

    private val installDir: File get() = File(context.filesDir, "$VOICES_DIR/$MODEL_NAME")
    private val marker: File get() = File(installDir, ".installed")

    private val download = VerifiedDownload(
        context,
        DownloadSpec(
            key = "natural_voices",
            url = ARCHIVE_URL,
            directory = VOICES_DIR,
            fileName = "$MODEL_NAME.tar.bz2",
            bytes = ARCHIVE_BYTES,
            sha256 = ARCHIVE_SHA256,
            title = context.getString(R.string.voice_download_title),
            description = context.getString(R.string.voice_download_description),
            spaceMargin = UNPACKED_BYTES,
        ),
    )

    private enum class Install { NONE, INSTALLING, INSTALLED, FAILED }

    private val install = MutableStateFlow(if (marker.exists()) Install.INSTALLED else Install.NONE)

    private val _preparing = MutableStateFlow(false)

    /** True while the model is loading or the first words are being made. */
    val preparing: StateFlow<Boolean> = _preparing.asStateFlow()

    val state: StateFlow<NaturalVoiceState> = combine(download.state, install) { d, i ->
        when (i) {
            Install.INSTALLED -> NaturalVoiceState.Ready
            Install.INSTALLING -> NaturalVoiceState.Installing
            Install.FAILED -> NaturalVoiceState.Failed(NaturalVoiceState.Reason.INSTALL_FAILED)
            Install.NONE -> when (d) {
                DownloadState.NotDownloaded -> NaturalVoiceState.NotInstalled
                is DownloadState.Downloading -> NaturalVoiceState.Downloading(d.downloadedBytes, d.totalBytes)
                DownloadState.WaitingForWifi -> NaturalVoiceState.WaitingForWifi
                DownloadState.Verifying, DownloadState.Complete -> NaturalVoiceState.Installing
                is DownloadState.Failed -> NaturalVoiceState.Failed(
                    when (d.reason) {
                        DownloadState.Reason.NOT_ENOUGH_SPACE -> NaturalVoiceState.Reason.NOT_ENOUGH_SPACE
                        DownloadState.Reason.DOWNLOAD_FAILED -> NaturalVoiceState.Reason.DOWNLOAD_FAILED
                        DownloadState.Reason.CHECK_FAILED -> NaturalVoiceState.Reason.CHECK_FAILED
                    },
                )
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, if (marker.exists()) NaturalVoiceState.Ready else NaturalVoiceState.NotInstalled)

    val isReady: Boolean get() = marker.exists()

    init {
        scope.launch {
            if (!marker.exists()) download.refresh()
            // Unpack as soon as a checked download is on the phone.
            download.state.collect { if (it == DownloadState.Complete && install.value == Install.NONE) unpack() }
        }
    }

    fun start(allowMobileData: Boolean = false) {
        install.value = Install.NONE
        download.start(allowMobileData)
    }

    fun downloadWithMobileData() = download.restartWithMobileData()

    fun watch() = download.watch()

    fun hasRoom(): Boolean = download.hasRoom()

    fun cancel() = download.cancel()

    suspend fun delete() {
        stop()
        engineLock.withLock {
            engine?.release()
            engine = null
        }
        download.cancel()
        installDir.deleteRecursively()
        install.value = Install.NONE
    }

    private fun unpack() {
        install.value = Install.INSTALLING
        scope.launch {
            val staging = File(context.filesDir, "$VOICES_DIR/.staging")
            try {
                staging.deleteRecursively()
                ArchiveExtractor.extractTarBz2(download.file, staging)
                val unpacked = File(staging, MODEL_NAME)
                check(REQUIRED_FILES.all { File(unpacked, it).exists() }) { "The voice pack is missing files" }
                installDir.deleteRecursively()
                installDir.parentFile?.mkdirs()
                check(unpacked.renameTo(installDir)) { "The voice pack could not be moved into place" }
                marker.createNewFile()
                download.cancel()
                install.value = Install.INSTALLED
            } catch (error: Throwable) {
                CrashReporter.record(context, error)
                install.value = Install.FAILED
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    /**
     * Speaks [text] with [speaker]. [speed] is 1.0 for a normal pace. Stops anything already being said.
     * [onFinished] is called when the speech has been read or stopped. If the natural voice fails for any
     * reason, [onFailure] is called instead, so the text can be read another way.
     */
    fun speak(text: String, speaker: Int, speed: Float, onFinished: () -> Unit = {}, onFailure: () -> Unit = {}) {
        stop()
        if (!isReady) {
            onFailure()
            return
        }
        val request = requests.incrementAndGet()
        fun active() = requests.get() == request
        speakJob = scope.launch {
            _preparing.value = true
            try {
                engineLock.withLock {
                    idleJob?.cancel()
                    val tts = engine ?: load().also { engine = it }
                    // Speech that was stopped or replaced while it waited is not started.
                    if (active()) {
                        val output = audioTrack(tts.sampleRate())
                        // Audio left by speech that was stopped part-way is cleared, so it is not heard first.
                        output.flush()
                        output.play()
                        tts.generateWithCallback(
                            text,
                            speaker,
                            speed,
                            AudioSink { samples ->
                                _preparing.value = false
                                if (active()) output.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                                active()
                            },
                        )
                        // Stopping a streaming track lets the audio already written finish playing. Speech that
                        // was stopped stays paused, in case Stop came just as it started.
                        if (active()) output.stop() else output.pause()
                    }
                    idleJob = scope.launch {
                        delay(IDLE_RELEASE_MS)
                        release()
                    }
                }
                onFinished()
            } catch (error: Throwable) {
                // Native code can fail with an Error rather than an Exception, so everything is caught here.
                if (error is kotlinx.coroutines.CancellationException) throw error
                CrashReporter.record(context, error)
                onFailure()
            } finally {
                _preparing.value = false
            }
        }
    }

    fun stop() {
        requests.incrementAndGet()
        track?.let {
            runCatching {
                it.pause()
                it.flush()
            }
        }
    }

    private fun load(): OfflineTts {
        val dir = installDir.path
        val kokoro = OfflineTtsKokoroModelConfig().apply {
            model = "$dir/model.int8.onnx"
            voices = "$dir/voices.bin"
            tokens = "$dir/tokens.txt"
            dataDir = "$dir/espeak-ng-data"
        }
        val model = OfflineTtsModelConfig().apply {
            this.kokoro = kokoro
            numThreads = THREADS
            provider = "cpu"
        }
        return OfflineTts(null, OfflineTtsConfig().apply { this.model = model })
    }

    private fun audioTrack(sampleRate: Int): AudioTrack {
        track?.takeIf { it.sampleRate == sampleRate }?.let { return it }
        track?.release()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val buffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(buffer * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { track = it }
    }

    private suspend fun release() = engineLock.withLock {
        engine?.release()
        engine = null
        track?.release()
        track = null
    }

    companion object {
        const val MODEL_NAME = "kokoro-int8-en-v0_19"
        const val SIZE_TEXT = "98 MB"
        const val VOICES_DIR = "voices"
        const val ARCHIVE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-en-v0_19.tar.bz2"
        const val ARCHIVE_BYTES = 103_248_205L
        const val ARCHIVE_SHA256 = "c9f0dd393615805b0bab050c340834d5e684e732aec91c0e860cd30e982c08bd"
        private const val UNPACKED_BYTES = 200L * 1024 * 1024
        private const val THREADS = 4
        private const val IDLE_RELEASE_MS = 2 * 60 * 1000L
        private val REQUIRED_FILES = listOf("model.int8.onnx", "voices.bin", "tokens.txt", "espeak-ng-data")

        /** The model's named voices. Speaker 0 is a blend and is left out. */
        val speakers = listOf(
            NaturalSpeaker(1, "Bella", british = false),
            NaturalSpeaker(2, "Nicole", british = false),
            NaturalSpeaker(3, "Sarah", british = false),
            NaturalSpeaker(4, "Sky", british = false),
            NaturalSpeaker(5, "Adam", british = false),
            NaturalSpeaker(6, "Michael", british = false),
            NaturalSpeaker(7, "Emma", british = true),
            NaturalSpeaker(8, "Isabella", british = true),
            NaturalSpeaker(9, "George", british = true),
            NaturalSpeaker(10, "Lewis", british = true),
        )
    }
}
