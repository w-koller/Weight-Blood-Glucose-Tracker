package io.github.wkoller.glucosetracker.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import io.github.wkoller.glucosetracker.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

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
    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("model", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engineLock = Mutex()
    private var engine: Engine? = null
    private var idleJob: Job? = null
    private var pollJob: Job? = null

    private val directory: File get() = context.getExternalFilesDir(MODEL_DIR) ?: File(context.filesDir, MODEL_DIR)
    val modelFile: File get() = File(directory, MODEL_FILE)

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    init {
        scope.launch { refresh() }
    }

    /** Reads the state from disk and the download manager. Safe to call often. */
    suspend fun refresh() {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        when {
            prefs.getBoolean(KEY_VERIFIED, false) && modelFile.length() == MODEL_BYTES -> _state.value = ModelState.Ready
            id >= 0 -> pollDownload(id)
            modelFile.exists() && modelFile.length() == MODEL_BYTES -> verify()
            else -> if (_state.value !is ModelState.Failed) _state.value = ModelState.NotDownloaded
        }
    }

    fun hasRoomForModel(): Boolean {
        directory.mkdirs()
        return StatFs(directory.path).availableBytes > MODEL_BYTES + SPACE_MARGIN
    }

    fun startDownload() {
        if (!hasRoomForModel()) {
            _state.value = ModelState.Failed(ModelState.Reason.NOT_ENOUGH_SPACE)
            return
        }
        directory.mkdirs()
        modelFile.delete()
        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle(context.getString(io.github.wkoller.glucosetracker.R.string.assistant_download_title))
            .setDescription(context.getString(io.github.wkoller.glucosetracker.R.string.assistant_download_description))
            .setDestinationInExternalFilesDir(context, MODEL_DIR, MODEL_FILE)
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val id = try {
            downloads.enqueue(request)
        } catch (error: RuntimeException) {
            // For example a SecurityException if the download manager refuses the request. Report it instead of crashing.
            CrashReporter.record(context, error)
            _state.value = ModelState.Failed(ModelState.Reason.DOWNLOAD_FAILED)
            return
        }
        prefs.edit().putLong(KEY_DOWNLOAD_ID, id).putBoolean(KEY_VERIFIED, false).apply()
        _state.value = ModelState.Downloading(0, MODEL_BYTES)
        watchDownload()
    }

    /** Polls the download while a screen is showing its progress. */
    fun watchDownload() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                refresh()
                val current = _state.value
                if (current !is ModelState.Downloading && current !is ModelState.WaitingForWifi) break
                delay(1_000)
            }
        }
    }

    fun cancelDownload() {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id >= 0) downloads.remove(id)
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        modelFile.delete()
        _state.value = ModelState.NotDownloaded
    }

    suspend fun delete() {
        unload()
        cancelDownload()
        prefs.edit().putBoolean(KEY_VERIFIED, false).apply()
        modelFile.delete()
        _state.value = ModelState.NotDownloaded
    }

    private suspend fun pollDownload(id: Long) {
        val cursor = downloads.query(DownloadManager.Query().setFilterById(id))
        cursor.use {
            if (!it.moveToFirst()) {
                prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                _state.value = ModelState.NotDownloaded
                return
            }
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { t -> t > 0 } ?: MODEL_BYTES
            val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                    verify()
                }
                DownloadManager.STATUS_FAILED -> {
                    prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                    downloads.remove(id)
                    _state.value = ModelState.Failed(
                        if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) ModelState.Reason.NOT_ENOUGH_SPACE else ModelState.Reason.DOWNLOAD_FAILED,
                    )
                }
                DownloadManager.STATUS_PAUSED -> _state.value =
                    if (reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI || reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK) ModelState.WaitingForWifi
                    else ModelState.Downloading(done, total)
                else -> _state.value = ModelState.Downloading(done, total)
            }
        }
    }

    /** Checks the downloaded file against the published hash, so a damaged file is never loaded. */
    private suspend fun verify() {
        _state.value = ModelState.Verifying
        val ok = withContext(Dispatchers.IO) {
            if (modelFile.length() != MODEL_BYTES) return@withContext false
            val digest = MessageDigest.getInstance("SHA-256")
            modelFile.inputStream().buffered(1 shl 20).use { input ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) } == MODEL_SHA256
        }
        prefs.edit().putBoolean(KEY_VERIFIED, ok).apply()
        if (ok) {
            _state.value = ModelState.Ready
        } else {
            modelFile.delete()
            _state.value = ModelState.Failed(ModelState.Reason.CHECK_FAILED)
        }
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
        check(_state.value == ModelState.Ready) { "The model is not ready" }
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
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_VERIFIED = "verified"
    }
}
