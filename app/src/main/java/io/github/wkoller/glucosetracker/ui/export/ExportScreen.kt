package io.github.wkoller.glucosetracker.ui.export

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.wkoller.glucosetracker.AppContainer
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.records.DailyLog
import io.github.wkoller.glucosetracker.export.CsvExporter
import io.github.wkoller.glucosetracker.export.PdfReportWriter
import io.github.wkoller.glucosetracker.ui.appViewModel
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.NoticeCard
import io.github.wkoller.glucosetracker.ui.components.NoticeKind
import io.github.wkoller.glucosetracker.ui.components.OptionRow
import io.github.wkoller.glucosetracker.ui.components.ScreenScaffold
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

enum class ExportFormat(val mimeType: String, val extension: String) { CSV("text/csv", "csv"), PDF("application/pdf", "pdf") }

data class ExportUi(
    val days: Int? = 30,
    val busy: Boolean = false,
    val share: Pair<Uri, ExportFormat>? = null,
    val error: Boolean = false,
)

class ExportViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(ExportUi())
    val ui: StateFlow<ExportUi> = _ui.asStateFlow()

    fun setDays(days: Int?) = _ui.update { it.copy(days = days) }

    fun create(format: ExportFormat) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = false) }
        viewModelScope.launch {
            val uri = runCatching { withContext(Dispatchers.IO) { writeFile(format) } }.getOrNull()
            _ui.update { it.copy(busy = false, share = uri?.let { u -> u to format }, error = uri == null) }
        }
    }

    fun shared() = _ui.update { it.copy(share = null) }

    private suspend fun writeFile(format: ExportFormat): Uri? {
        val repository = container.repository
        val plan = repository.currentPlan() ?: return null
        val today = repository.logicalToday(plan)
        val first = listOfNotNull(repository.firstReadingDate(), repository.allMeasurements().firstOrNull()?.date).minOrNull() ?: today
        val from = _ui.value.days?.let { today.minusDays(it - 1L) } ?: first
        val days = DailyLog.build(
            repository.readingsBetween(from, today),
            repository.dosesBetween(from, today),
            repository.allMeasurements().filter { !it.date.isBefore(from) && !it.date.isAfter(today) },
        )
        val directory = File(container.appContext.cacheDir, "exports").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, "weight-glucose-$from-to-$today.${format.extension}")
        when (format) {
            ExportFormat.CSV -> file.writeText(CsvExporter(container.appContext).build(plan, days))
            ExportFormat.PDF -> PdfReportWriter(container.appContext).write(
                file, plan, from, today, days, LocalDate.now(container.clock), container.settingsStore.current().fullName,
            )
        }
        return FileProvider.getUriForFile(container.appContext, "${container.appContext.packageName}.files", file)
    }
}

@Composable
fun ExportScreen(onBack: () -> Unit) {
    val vm = appViewModel { container, _ -> ExportViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.export_share_title)

    LaunchedEffect(ui.share) {
        val (uri, format) = ui.share ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND)
            .setType(format.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, chooserTitle))
        vm.shared()
    }

    ScreenScaffold(title = stringResource(R.string.settings_export), onBack = onBack) {
        Text(stringResource(R.string.export_body), style = MaterialTheme.typography.bodyLarge)
        SectionCard(stringResource(R.string.export_period)) {
            listOf(7, 14, 30, 90, null).forEach { span ->
                OptionRow(
                    if (span == null) stringResource(R.string.export_all) else stringResource(R.string.progress_days, span),
                    null,
                    ui.days == span,
                    { vm.setDays(span) },
                )
            }
        }
        if (ui.error) NoticeCard(NoticeKind.DANGER, stringResource(R.string.export_error_title), stringResource(R.string.export_error))
        BigButton(
            stringResource(R.string.export_pdf),
            onClick = { vm.create(ExportFormat.PDF) },
            icon = R.drawable.ic_description,
            helper = stringResource(R.string.export_pdf_helper),
            enabled = !ui.busy,
        )
        BigButton(
            stringResource(R.string.export_csv),
            onClick = { vm.create(ExportFormat.CSV) },
            icon = R.drawable.ic_share,
            helper = stringResource(R.string.export_csv_helper),
            enabled = !ui.busy,
        )
    }
}
