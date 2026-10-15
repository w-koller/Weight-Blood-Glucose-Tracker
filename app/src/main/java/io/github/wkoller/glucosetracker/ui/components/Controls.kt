package io.github.wkoller.glucosetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.tts.LocalSpeaker
import io.github.wkoller.glucosetracker.ui.theme.LocalUiPrefs

/** An on/off setting. The whole row is the touch target, not just the switch. */
@Composable
fun ToggleRow(title: String, description: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp).heightIn(min = 48.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (description != null && LocalUiPrefs.current.showHelperText) {
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** One choice in a group where only one can be picked. */
@Composable
fun OptionRow(title: String, description: String?, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp).heightIn(min = 48.dp)) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (description != null && LocalUiPrefs.current.showHelperText) {
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** A tick box with a full-width label, used where the user confirms they have checked something. */
@Composable
fun CheckRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp).heightIn(min = 48.dp)) {
            Checkbox(checked = checked, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
    }
}

/** A large text box with a visible label above it and any error below it. */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    suffix: String? = null,
    error: String? = null,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    password: Boolean = false,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.titleLarge,
            placeholder = hint?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
            suffix = suffix?.let { { Text(it, style = MaterialTheme.typography.titleMedium) } },
            isError = error != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    password -> KeyboardType.Password
                    numeric -> KeyboardType.Decimal
                    else -> KeyboardType.Text
                },
                imeAction = ImeAction.Next,
            ),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        )
        if (error != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** A confirmation with two clearly worded choices, shown as full-size buttons rather than small links. */
@Composable
fun ConfirmDialog(
    title: String,
    messages: List<String>,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** What happens when the dialog is closed without choosing, for example with Back. Defaults to [onDismiss]. */
    onCancel: () -> Unit = onDismiss,
) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                messages.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                BigButton(confirmText, onClick = onConfirm)
                BigButton(dismissText, onClick = onDismiss, style = ButtonStyle.SECONDARY)
            }
        }
    }
}

/** A message with a single button to close it, for results such as "Your records have been restored." */
@Composable
fun MessageDialog(title: String, message: String, buttonText: String, onClose: () -> Unit, busy: Boolean = false) {
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(message, style = MaterialTheme.typography.bodyLarge)
                if (busy) {
                    androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    BigButton(buttonText, onClick = onClose)
                }
            }
        }
    }
}

/**
 * Reads [text] aloud. While the text is being read, including when it was read out automatically, the
 * same button stops it. Shown wherever an important result appears.
 */
@Composable
fun SpeakButton(text: String, modifier: Modifier = Modifier) {
    val speaker = LocalSpeaker.current
    val rate = LocalUiPrefs.current.speechRate
    val reading = speaker?.speaking?.collectAsStateWithLifecycle()?.value == text
    // Leaving the screen stops the text, so the voice never carries on without a button to stop it.
    DisposableEffect(speaker, text) { onDispose { speaker?.stop(text) } }
    BigButton(
        text = stringResource(if (reading) R.string.stop_reading_aloud else R.string.read_aloud),
        onClick = { if (reading) speaker?.stop() else speaker?.speak(text, rate) },
        icon = if (reading) R.drawable.ic_stop_circle else R.drawable.ic_volume_up,
        style = ButtonStyle.SECONDARY,
        modifier = modifier,
    )
}

@Composable
fun SmallIcon(res: Int, modifier: Modifier = Modifier) {
    Icon(painterResource(res), contentDescription = null, modifier = modifier.size(24.dp))
}
