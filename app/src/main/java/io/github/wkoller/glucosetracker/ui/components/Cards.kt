package io.github.wkoller.glucosetracker.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.ui.theme.LocalStatusColors

enum class NoticeKind { INFO, WARNING, DANGER }

/** A coloured box for something the user should read before going on. The icon and title carry the meaning, not just the colour. */
@Composable
fun NoticeCard(kind: NoticeKind, title: String, body: String?, modifier: Modifier = Modifier, extra: @Composable ColumnScope.() -> Unit = {}) {
    val status = LocalStatusColors.current
    val (container, content, icon) = when (kind) {
        NoticeKind.INFO -> Triple(status.infoContainer, status.onInfoContainer, R.drawable.ic_info)
        NoticeKind.WARNING -> Triple(status.warningContainer, status.onWarningContainer, R.drawable.ic_warning)
        NoticeKind.DANGER -> Triple(status.dangerContainer, status.onDangerContainer, R.drawable.ic_error)
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, content),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            if (body != null) Text(body, style = MaterialTheme.typography.bodyLarge)
            extra()
        }
    }
}

/** A plain card with an optional heading, used to group related information. */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                }
            }
            content()
        }
    }
}

/** A label and value on one line, wrapping onto two lines when the text is large. TalkBack reads them together. */
@Composable
fun FactRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
