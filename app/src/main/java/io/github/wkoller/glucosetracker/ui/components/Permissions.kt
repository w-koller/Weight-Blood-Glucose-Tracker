package io.github.wkoller.glucosetracker.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Asks to show notifications, and returns the action that asks. Android stops showing its question after it has
 * been refused twice, and a button that then did nothing would be confusing, so the app's notification settings
 * open instead, where notifications can still be turned on. They also open on Android 12, which has no question,
 * and when notifications were turned off in the phone's settings. [onResult] is told whether they are allowed.
 */
@Composable
fun rememberNotificationPermission(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val canAskAgain = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        val allowed = granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!allowed && !canAskAgain) openNotificationSettings(context)
        onResult(allowed)
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings(context)
        }
    }
}

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
}
