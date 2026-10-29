package io.github.wkoller.glucosetracker.ui.components

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.ui.theme.LocalStatusColors
import io.github.wkoller.glucosetracker.ui.theme.LocalUiPrefs

enum class ButtonStyle { PRIMARY, SECONDARY, DANGER }

/**
 * The app's main button: full width, at least 64 dp tall, and always labelled. The icon and the
 * helper line underneath can be switched off in Display and accessibility.
 */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    helper: String? = null,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    enabled: Boolean = true,
) {
    val prefs = LocalUiPrefs.current
    val haptics = LocalHapticFeedback.current
    val click = {
        if (prefs.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }
    val shape = RoundedCornerShape(16.dp)
    val padding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (icon != null && prefs.showIcons) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Start)
                if (helper != null && prefs.showHelperText) {
                    Text(helper, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
                }
            }
        }
    }
    val sized = modifier.fillMaxWidth().heightIn(min = 72.dp)
    when (style) {
        ButtonStyle.PRIMARY -> Button(onClick = click, modifier = sized, enabled = enabled, shape = shape, contentPadding = padding) { content() }
        ButtonStyle.DANGER -> Button(
            onClick = click,
            modifier = sized,
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = LocalStatusColors.current.low,
                contentColor = LocalStatusColors.current.onLow,
            ),
        ) { content() }
        ButtonStyle.SECONDARY -> OutlinedButton(
            onClick = click,
            modifier = sized,
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) { content() }
    }
}

/** Australia's emergency number. */
const val EMERGENCY_NUMBER = "000"

/** Lifeline, for anyone in Australia who needs to talk to someone, at any hour. */
const val LIFELINE_NUMBER = "131114"

/**
 * Opens the phone's dialler with [number] ready to call. The user still presses call in the dialler, so a stray
 * tap never rings anyone.
 */
@Composable
fun DialButton(text: String, number: String, modifier: Modifier = Modifier, style: ButtonStyle = ButtonStyle.SECONDARY) {
    val context = LocalContext.current
    BigButton(
        text,
        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))) } },
        modifier = modifier,
        icon = R.drawable.ic_call,
        style = style,
    )
}
