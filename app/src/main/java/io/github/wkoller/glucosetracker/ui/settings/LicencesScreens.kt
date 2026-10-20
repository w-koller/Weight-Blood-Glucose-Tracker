package io.github.wkoller.glucosetracker.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.ui.components.BackButton
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.ScreenScaffold
import io.github.wkoller.glucosetracker.ui.components.ScreenTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A piece of software in the app, the licence it comes under, and the file in assets/licences with that licence. */
private data class LicenceItem(@StringRes val name: Int, @StringRes val licence: Int, val file: String)

/** The app's own licence, which also covers the eSpeak NG data. Copied from the repository by the build. */
private const val GPL = "weight-glucose-tracker-LICENSE.txt"

/** The Apache License 2.0, as Apache Commons Compress ships it. Copied from its archive by the build. */
private const val APACHE = "commons-compress-LICENSE.txt"

private val licences = listOf(
    LicenceItem(R.string.licence_item_app, R.string.licence_gpl, GPL),
    LicenceItem(R.string.licence_item_litertlm, R.string.licence_apache, "litertlm-android-LICENSE.txt"),
    LicenceItem(R.string.licence_item_litertlm_notices, R.string.licence_notices, "litertlm-android-THIRD_PARTY_NOTICE.txt"),
    LicenceItem(R.string.licence_item_gemma, R.string.licence_apache, APACHE),
    LicenceItem(R.string.licence_item_sherpa, R.string.licence_apache, APACHE),
    LicenceItem(R.string.licence_item_onnxruntime, R.string.licence_mit, "onnxruntime-LICENSE.txt"),
    LicenceItem(R.string.licence_item_kokoro, R.string.licence_apache, APACHE),
    LicenceItem(R.string.licence_item_espeak, R.string.licence_gpl, GPL),
    LicenceItem(R.string.licence_item_commons_compress, R.string.licence_apache, APACHE),
    LicenceItem(R.string.licence_item_commons_compress_notice, R.string.licence_notices, "commons-compress-NOTICE.txt"),
    LicenceItem(R.string.licence_item_jetpack, R.string.licence_apache, APACHE),
    LicenceItem(R.string.licence_item_material_symbols, R.string.licence_apache, APACHE),
)

/** About › Licences: the software the app is built with, each with the licence it comes under. */
@Composable
fun LicencesScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    ScreenScaffold(title = stringResource(R.string.licences_title), onBack = onBack) {
        Text(stringResource(R.string.licences_body), style = MaterialTheme.typography.bodyLarge)
        licences.forEach { item ->
            BigButton(
                stringResource(item.name),
                onClick = { onOpen(item.file) },
                icon = R.drawable.ic_description,
                helper = stringResource(item.licence),
                style = ButtonStyle.SECONDARY,
            )
        }
    }
}

/**
 * The full text of one licence. Some notices run to thousands of paragraphs, so they are read away from the main
 * thread and shown a paragraph at a time. Lines are joined within each paragraph, so the text wraps to the screen
 * at any text size.
 */
@Composable
fun LicenceTextScreen(file: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val paragraphs by produceState<List<String>?>(null, file) {
        value = withContext(Dispatchers.IO) {
            val text = runCatching { context.assets.open("licences/$file").bufferedReader().use { it.readText() } }.getOrDefault("")
            text.split(Regex("""\n\s*\n""")).map { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.trim() }.filter { it.isNotEmpty() }
        }
    }
    Scaffold { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BackButton(onBack) }
            item { ScreenTitle(stringResource(R.string.licences_title)) }
            val loaded = paragraphs
            if (loaded == null) {
                item { CircularProgressIndicator() }
            } else {
                items(loaded) { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
