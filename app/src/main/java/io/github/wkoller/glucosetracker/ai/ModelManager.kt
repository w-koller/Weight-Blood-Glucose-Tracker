package io.github.wkoller.glucosetracker.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.download.DownloadSpec
import io.github.wkoller.glucosetracker.download.DownloadState
import io.github.wkoller.glucosetracker.download.VerifiedDownload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private fun DownloadState.toModelState(): ModelState = when (this) {
    DownloadState.NotDownloaded -> ModelState.NotDownloaded
    is DownloadState.Downloading -> ModelState.Downloading(downloadedBytes, totalBytes)
    DownloadState.WaitingForWifi -> ModelState.WaitingForWifi
    DownloadState.Verifying -> ModelState.Verifying
    DownloadState.Complete -> ModelState.Ready
    is DownloadState.Failed -> ModelState.Failed(
        when (reason) {
            DownloadState.Reason.NOT_ENOUGH_SPACE -> ModelState.Reason.NOT_ENOUGH_SPACE
            DownloadState.Reason.DOWNLOAD_FAILED -> ModelState.Reason.DOWNLOAD_FAILED
            DownloadState.Reason.CHECK_FAILED -> ModelState.Reason.CHECK_FAILED
        },
    )
}

sealed interface ModelState {
    data object NotDownloaded : ModelState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ModelState
    data object WaitingForWifi : ModelState
    data object Verifying : ModelState
    data object Ready : ModelState
    data class Failed(val reason: Reason) : ModelState

    enum class Reason { NOT_ENOUGH_SPACE, DOWNLOAD_FAILED, CHECK_FAILED }
}

/**
 * Looks after the on-device language model: downloading it with Android's download manager (Wi-Fi
 * only, resumable), checking the file against its published SHA-256, and loading it into LiteRT-LM.
 * The model is loaded only while the assistant is in use and is released after two idle minutes.
 */
class ModelManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engineLock = Mutex()
    private var engine: Engine? = null
    private var idleJob: Job? = null

    private val download = VerifiedDownload(
        context,
        DownloadSpec(
            key = "model",
            url = MODEL_URL,
            directory = MODEL_DIR,
            fileName = MODEL_FILE,
            bytes = MODEL_BYTES,
            sha256 = MODEL_SHA256,
            title = context.getString(R.string.assistant_download_title),
            description = context.getString(R.string.assistant_download_description),
            spaceMargin = SPACE_MARGIN,
        ),
    )
    val modelFile: File get() = download.file

    /** The download's progress, in the terms the assistant screens use. */
    val state: StateFlow<ModelState> = download.state
        .map { it.toModelState() }
        .stateIn(scope, SharingStarted.Eagerly, ModelState.NotDownloaded)

    init {
        scope.launch { download.refresh() }
    }

    suspend fun refresh() = download.refresh()

    fun hasRoomForModel(): Boolean = download.hasRoom()

    fun startDownload(allowMobileData: Boolean = false) = download.start(allowMobileData)

    fun downloadWithMobileData() = download.restartWithMobileData()

    fun watchDownload() = download.watch()

    fun cancelDownload() = download.cancel()

    suspend fun delete() {
        unload()
        download.cancel()
    }

    /** Runs [block] with a loaded engine, loading the model first if needed. */
    suspend fun <T> withEngine(block: suspend (Engine) -> T): T = engineLock.withLock {
        idleJob?.cancel()
        val loaded = engine ?: load().also { engine = it }
        try {
            block(loaded)
        } finally {
            idleJob = scope.launch {
                delay(IDLE_UNLOAD_MS)
                unload()
            }
        }
    }

    val isLoaded: Boolean get() = engine != null

    private suspend fun load(): Engine = withContext(Dispatchers.Default) {
        check(download.state.value == DownloadState.Complete) { "The model is not ready" }
        // The GPU is faster and uses less memory; not every phone supports it, so the CPU is the fallback.
        val backends = listOf<Backend>(Backend.GPU(), Backend.CPU())
        var lastError: Throwable? = null
        for (backend in backends) {
            try {
                val candidate = Engine(EngineConfig(modelFile.path, backend, null, null, MAX_TOKENS, null, context.cacheDir.path))
                candidate.initialize()
                return@withContext candidate
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw IllegalStateException("The model could not be loaded", lastError)
    }

    fun unload() {
        scope.launch {
            engineLock.withLock {
                engine?.close()
                engine = null
            }
        }
    }

    companion object {
        const val MODEL_NAME = "Gemma 4"
        const val SIZE_TEXT = "2.6 GB"
        const val MODEL_DIR = "models"
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
        const val MODEL_BYTES = 2_588_147_712L
        const val MODEL_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"

        // Pinned to one revision so the file always matches the hash above.
        const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it.litertlm"

        private const val SPACE_MARGIN = 400L * 1024 * 1024
        private const val MAX_TOKENS = 4096
        private const val IDLE_UNLOAD_MS = 2 * 60 * 1000L
    }
}
