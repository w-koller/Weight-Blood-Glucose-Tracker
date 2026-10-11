package io.github.wkoller.glucosetracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.download.Connection
import io.github.wkoller.glucosetracker.download.NetworkStatus

/**
 * Starts a large download. On Wi-Fi it starts straight away. On mobile data the user is told that
 * charges may apply and chooses between using mobile data and waiting for Wi-Fi.
 * [start] receives true when the user agreed to use mobile data.
 */
@Composable
fun rememberDownloadStarter(size: String, start: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    var asking by remember { mutableStateOf(false) }
    if (asking) {
        MobileDataDialog(
            size = size,
            onMobileData = { asking = false; start(true) },
            onWait = { asking = false; start(false) },
            onDismiss = { asking = false },
        )
    }
    return { if (NetworkStatus.current(context) == Connection.MOBILE) asking = true else start(false) }
}

/** For a download waiting for Wi-Fi: confirms that mobile data may cost money before using it. */
@Composable
fun rememberMobileDataSwitch(size: String, switch: () -> Unit): () -> Unit {
    var asking by remember { mutableStateOf(false) }
    if (asking) {
        MobileDataDialog(
            size = size,
            onMobileData = { asking = false; switch() },
            onWait = { asking = false },
            onDismiss = { asking = false },
        )
    }
    return { asking = true }
}

@Composable
private fun MobileDataDialog(size: String, onMobileData: () -> Unit, onWait: () -> Unit, onDismiss: () -> Unit) {
    // Waiting for Wi-Fi is the first, main choice, so an accidental tap does not cost money.
    ConfirmDialog(
        title = stringResource(R.string.download_mobile_title),
        messages = listOf(stringResource(R.string.download_mobile_body, size), stringResource(R.string.download_mobile_question)),
        confirmText = stringResource(R.string.download_mobile_wait),
        dismissText = stringResource(R.string.download_mobile_yes),
        onConfirm = onWait,
        onDismiss = onMobileData,
        onCancel = onDismiss,
    )
}
