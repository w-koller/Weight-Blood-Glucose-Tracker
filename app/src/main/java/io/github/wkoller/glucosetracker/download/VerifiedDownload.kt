package io.github.wkoller.glucosetracker.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
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
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

sealed interface DownloadState {
    data object NotDownloaded : DownloadState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : DownloadState
    data object WaitingForWifi : DownloadState
    data object Verifying : DownloadState

    /** The file is on the phone and matches its published checksum. */
    data object Complete : DownloadState
    data class Failed(val reason: Reason) : DownloadState

    enum class Reason { NOT_ENOUGH_SPACE, DOWNLOAD_FAILED, CHECK_FAILED }
}

/** What to download, and how to check it. */
data class DownloadSpec(
    val key: String,
    val url: String,
    val directory: String,
    val fileName: String,
    val bytes: Long,
    val sha256: String,
    val title: String,
    val description: String,
    /** Extra free space needed beyond the file itself, for example to unpack it. */
    val spaceMargin: Long,
)

/**
 * A large file fetched with Android's download manager: resumable, shown in the notifications, and
 * checked against its published SHA-256 before it is used. It uses Wi-Fi only unless the user has
 * agreed to use mobile data.
 */
class VerifiedDownload(private val context: Context, private val spec: DownloadSpec) {
    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("download_${spec.key}", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    private val directory: File get() = context.getExternalFilesDir(spec.directory) ?: File(context.filesDir, spec.directory)
    val file: File get() = File(directory, spec.fileName)

    private val _state = MutableStateFlow<DownloadState>(DownloadState.NotDownloaded)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    /** Reads the state from disk and the download manager. Safe to call often. */
    suspend fun refresh() {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        when {
            prefs.getBoolean(KEY_VERIFIED, false) && file.length() == spec.bytes -> _state.value = DownloadState.Complete
            id >= 0 -> poll(id)
            file.exists() && file.length() == spec.bytes -> verify()
            else -> if (_state.value !is DownloadState.Failed) _state.value = DownloadState.NotDownloaded
        }
    }

    fun hasRoom(): Boolean {
        directory.mkdirs()
        return StatFs(directory.path).availableBytes > spec.bytes + spec.spaceMargin
    }

    /** Starts the download. With [allowMobileData] false it waits for Wi-Fi; it never downloads while roaming. */
    fun start(allowMobileData: Boolean = false) {
        if (!hasRoom()) {
            _state.value = DownloadState.Failed(DownloadState.Reason.NOT_ENOUGH_SPACE)
            return
        }
        directory.mkdirs()
        file.delete()
        val request = DownloadManager.Request(Uri.parse(spec.url))
            .setTitle(spec.title)
            .setDescription(spec.description)
            .setDestinationInExternalFilesDir(context, spec.directory, spec.fileName)
            .setAllowedNetworkTypes(
                if (allowMobileData) DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE else DownloadManager.Request.NETWORK_WIFI,
            )
            .setAllowedOverMetered(allowMobileData)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val id = try {
            downloads.enqueue(request)
        } catch (error: RuntimeException) {
            // For example a SecurityException if the download manager refuses the request. Report it instead of crashing.
            CrashReporter.record(context, error)
            _state.value = DownloadState.Failed(DownloadState.Reason.DOWNLOAD_FAILED)
            return
        }
        prefs.edit().putLong(KEY_DOWNLOAD_ID, id).putBoolean(KEY_VERIFIED, false).apply()
        _state.value = DownloadState.Downloading(0, spec.bytes)
        watch()
    }

    /** Polls the download while it is running. */
    fun watch() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                refresh()
                val current = _state.value
                if (current !is DownloadState.Downloading && current !is DownloadState.WaitingForWifi) break
                delay(1_000)
            }
        }
    }

    /** Starts again using mobile data, for a download that is waiting for Wi-Fi. */
    fun restartWithMobileData() {
        cancel()
        start(allowMobileData = true)
    }

    fun cancel() {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id >= 0) downloads.remove(id)
        prefs.edit().remove(KEY_DOWNLOAD_ID).putBoolean(KEY_VERIFIED, false).apply()
        file.delete()
        _state.value = DownloadState.NotDownloaded
    }

    private suspend fun poll(id: Long) {
        downloads.query(DownloadManager.Query().setFilterById(id)).use {
            if (!it.moveToFirst()) {
                prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                _state.value = DownloadState.NotDownloaded
                return
            }
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { t -> t > 0 } ?: spec.bytes
            val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                    verify()
                }
                DownloadManager.STATUS_FAILED -> {
                    prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                    downloads.remove(id)
                    _state.value = DownloadState.Failed(
                        if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) DownloadState.Reason.NOT_ENOUGH_SPACE else DownloadState.Reason.DOWNLOAD_FAILED,
                    )
                }
                DownloadManager.STATUS_PAUSED -> _state.value =
                    if (reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI || reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK) DownloadState.WaitingForWifi
                    else DownloadState.Downloading(done, total)
                else -> _state.value = DownloadState.Downloading(done, total)
            }
        }
    }

    /** Checks the downloaded file against the published hash, so a damaged file is never used. */
    private suspend fun verify() {
        _state.value = DownloadState.Verifying
        val ok = withContext(Dispatchers.IO) { file.length() == spec.bytes && sha256(file) == spec.sha256 }
        prefs.edit().putBoolean(KEY_VERIFIED, ok).apply()
        if (ok) {
            _state.value = DownloadState.Complete
        } else {
            file.delete()
            _state.value = DownloadState.Failed(DownloadState.Reason.CHECK_FAILED)
        }
    }

    companion object {
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_VERIFIED = "verified"

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
