package io.github.wkoller.glucosetracker.ui.logbook

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.dosing.GlucoseStatus
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseTargets
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.progress.TrendPoint
import io.github.wkoller.glucosetracker.domain.records.CheckEntry
import io.github.wkoller.glucosetracker.domain.records.LogbookDay
import io.github.wkoller.glucosetracker.domain.records.LogbookEntry
import io.github.wkoller.glucosetracker.domain.records.MeasurementEntry
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.components.BackButton
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.MeasurePill
import io.github.wkoller.glucosetracker.ui.components.ReadingPill
import io.github.wkoller.glucosetracker.ui.components.ScreenTitle
import io.github.wkoller.glucosetracker.ui.components.StatusChip
import io.github.wkoller.glucosetracker.ui.components.label
import io.github.wkoller.glucosetracker.ui.theme.LocalUiPrefs
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The logbook as a list that is easy to scroll through or show to a doctor: a chart of the readings stays at
 * the top and follows the list, each day keeps its heading in view while its records scroll, and each record
 * is one line with its value in a coloured pill. Pressing a record shows its doses, notes and a button to
 * change the day. Back, the heading and [viewChoice] slide away as the list scrolls up, to give the list room.
 *
 * Pressing a day on the chart calls [onChart] to open that day on the full-screen chart. When the chart closes,
 * [returnDate] is the day it was showing: the list loads back to that day with [onLoadFrom] if it needs to,
 * brings it to the top and calls [onReturnHandled].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LogbookListScreen(
    ui: LogbookUi,
    onBack: () -> Unit,
    viewChoice: @Composable () -> Unit,
    onDay: (LocalDate) -> Unit,
    onChart: (LocalDate) -> Unit,
    onEarlier: () -> Unit,
    onLoadFrom: (LocalDate) -> Unit,
    returnDate: LocalDate?,
    onReturnHandled: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val header = rememberSaveable(saver = CollapsingHeaderState.Saver) { CollapsingHeaderState() }
    val targets = ui.plan?.targets ?: GlucoseTargets.DEFAULT
    var open by rememberSaveable { mutableStateOf(listOf<String>()) }
    val layout = remember(ui.days) { ListLayout.of(ui.days) }
    // Dragging up or down on the heading or the chart scrolls the list too, so the heading can slide away from anywhere,
    // which matters most with the phone turned sideways. Sideways drags on the chart move it through the days instead.
    val pageScroll = Modifier.scrollable(listState, Orientation.Vertical, reverseDirection = true)

    // Back from the full-screen chart: bring the day it was showing to the top, loading earlier days first if needed.
    LaunchedEffect(returnDate, layout, ui.loading) {
        val date = returnDate ?: return@LaunchedEffect
        if (ui.loading) return@LaunchedEffect
        if (date.isBefore(ui.from)) {
            onLoadFrom(date)
            return@LaunchedEffect
        }
        layout.indexAtOrBefore(date)?.let { listState.scrollToItem(it) }
        onReturnHandled()
    }

    Scaffold { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val chartHeight = (maxHeight * CHART_SHARE).coerceIn(MIN_CHART, MAX_CHART)
            Column(Modifier.fillMaxSize().nestedScroll(header.connection)) {
                CollapsingHeader(header, pageScroll) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        BackButton(onBack)
                        ScreenTitle(stringResource(R.string.logbook_title))
                        viewChoice()
                    }
                }
                if (!ui.loading && ui.days.isEmpty()) {
                    Text(stringResource(R.string.logbook_empty), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp))
                }
                if (ui.days.isEmpty()) return@Column

                val focus by remember(layout) { derivedStateOf { layout.focusOf(listState) } }
                val newest = maxOf(ui.days.first().date, ui.today)
                LogbookChart(
                    points = ui.points,
                    focus = focus.date,
                    focusPoint = focus.point,
                    first = ui.days.last().date,
                    last = newest,
                    targets = targets,
                    height = chartHeight,
                    describeDay = { date -> describeChartDay(context, date, layout.day(date)) },
                    onDay = onChart,
                    // Sliding the chart brings the day in its middle to the top of the list, or the nearest earlier day with records.
                    onScrub = { date -> layout.indexAtOrBefore(date)?.let { index -> scope.launch { listState.scrollToItem(index) } } },
                    modifier = pageScroll,
                    onEarliest = { if (ui.hasEarlier) onEarlier() },
                )
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ui.days.forEach { day ->
                        stickyHeader(key = ListLayout.dayKey(day.date)) { DayHeading(day.date, ui.today) }
                        items(day.entries, key = { it.key }) { entry ->
                            val expanded = entry.key in open
                            EntryRow(
                                entry = entry,
                                expanded = expanded,
                                onToggle = { open = if (expanded) open - entry.key else open + entry.key },
                                onOpenDay = { onDay(day.date) },
                            )
                        }
                    }
                    if (ui.hasEarlier) {
                        item(key = "earlier") {
                            Box(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                BigButton(stringResource(R.string.logbook_show_earlier), onClick = onEarlier, icon = R.drawable.ic_history, style = ButtonStyle.SECONDARY)
                            }
                        }
                    }
                    item(key = "end") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/** The day at the top of the list and the reading nearest the top, which the chart highlights. */
private data class Focus(val date: LocalDate, val point: TrendPoint?)

/** Where each day starts in the list, and the day and chart point of every item, so the chart can follow the list. */
private class ListLayout(
    private val days: Map<LocalDate, LogbookDay>,
    private val starts: Map<LocalDate, Int>,
    private val dates: List<LocalDate>,
    private val points: List<TrendPoint?>,
) {
    fun indexOf(date: LocalDate): Int? = starts[date]

    /** The start of [date] in the list, or of the nearest earlier day when [date] has no records. */
    fun indexAtOrBefore(date: LocalDate): Int? = starts.filterKeys { !it.isAfter(date) }.maxByOrNull { it.key }?.value

    fun day(date: LocalDate): LogbookDay? = days[date]

    fun focusOf(state: LazyListState): Focus {
        val visible = state.layoutInfo.visibleItemsInfo.filter { it.index < dates.size }
        val headingStarts = starts.values.toSet()
        // The pinned heading covers the top of the list, so the focus is the first record mostly below it.
        val heading = visible.filter { it.index in headingStarts }.minByOrNull { it.offset }
        val coveredTo = heading?.let { it.offset + it.size } ?: 0
        val item = visible.firstOrNull { it.index !in headingStarts && it.offset + it.size / 2 >= coveredTo }
        val index = item?.index ?: state.firstVisibleItemIndex.coerceAtMost(dates.lastIndex)
        // A weight at the top has no point of its own, so the day's next reading is ringed instead.
        val point = (index..dates.lastIndex).asSequence().takeWhile { dates[it] == dates[index] }.firstNotNullOfOrNull { points[it] }
        return Focus(dates[index], point)
    }

    companion object {
        fun dayKey(date: LocalDate) = "day-$date"

        fun of(days: List<LogbookDay>): ListLayout {
            val starts = mutableMapOf<LocalDate, Int>()
            val dates = mutableListOf<LocalDate>()
            val points = mutableListOf<TrendPoint?>()
            days.forEach { day ->
                starts[day.date] = dates.size
                dates += day.date
                points += null
                day.entries.forEach { entry ->
                    dates += day.date
                    points += (entry as? CheckEntry)?.point
                }
            }
            return ListLayout(days.associateBy { it.date }, starts, dates, points)
        }
    }
}

/**
 * How far the part above the chart has slid away. Scrolling the list up slides it away first; scrolling down
 * brings it back once the list is at its top, like the heading of any other screen scrolling with its page.
 */
@Stable
private class CollapsingHeaderState(initiallyHidden: Float = 0f) {
    var hidden by mutableFloatStateOf(initiallyHidden)
    var height = 0

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f) return Offset.Zero
            val taken = available.y.coerceAtLeast(hidden - height)
            hidden -= taken
            return Offset(0f, taken)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f) return Offset.Zero
            val given = available.y.coerceAtMost(hidden)
            hidden -= given
            return Offset(0f, given)
        }
    }

    companion object {
        /** Keeps how far the part above the chart has slid away while the full-screen chart is open. */
        val Saver: Saver<CollapsingHeaderState, Float> = Saver(save = { it.hidden }, restore = { CollapsingHeaderState(it) })
    }
}

@Composable
private fun CollapsingHeader(state: CollapsingHeaderState, modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                state.height = placeable.height
                val hidden = state.hidden.roundToInt().coerceIn(0, placeable.height)
                layout(placeable.width, placeable.height - hidden) { placeable.place(0, -hidden) }
            },
    ) { content() }
}

/** A day's heading, which stays at the top while its records scroll under it. */
@Composable
private fun DayHeading(date: LocalDate, today: LocalDate) {
    val name = UiFormat.dayLong(date)
    val text = when (date) {
        today -> stringResource(R.string.logbook_today, name)
        today.minusDays(1) -> stringResource(R.string.logbook_yesterday, name)
        else -> name
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp).semantics { heading() })
            HorizontalDivider()
        }
    }
}

@Composable
private fun EntryRow(entry: LogbookEntry, expanded: Boolean, onToggle: () -> Unit, onOpenDay: () -> Unit) {
    when (entry) {
        is CheckEntry -> CheckRow(entry, expanded, onToggle, onOpenDay)
        is MeasurementEntry -> MeasurementRow(entry, expanded, onToggle, onOpenDay)
    }
}

@Composable
private fun CheckRow(entry: CheckEntry, expanded: Boolean, onToggle: () -> Unit, onOpenDay: () -> Unit) {
    val context = LocalContext.current
    val time = UiFormat.time(context, entry.takenAt)
    val slot = entry.slot.label()
    val reading = entry.reading
    val status = entry.status
    val summary = doseSummary(context, entry.doses)
    val notes = entry.notes
    RowFrame(
        time = time,
        label = slot,
        description = checkDescription(entry),
        expanded = expanded,
        onToggle = onToggle,
        value = { if (reading != null && status != null) ReadingPill(reading.tenths, reading.meterFlag, status) },
        summary = {
            summary?.let { IconLine(R.drawable.ic_medication, it) }
            if (notes.isNotEmpty()) IconLine(R.drawable.ic_description, stringResource(R.string.logbook_note, notes.joinToString(" ")), maxLines = 2)
        },
        details = {
            CheckFacts(entry)
            BigButton(stringResource(R.string.logbook_open_day), onClick = onOpenDay, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY)
        },
    )
}

/** What TalkBack reads for a check: its time and time of day, the reading and its status, the doses and any notes. */
@Composable
internal fun checkDescription(entry: CheckEntry): String {
    val context = LocalContext.current
    val time = UiFormat.time(context, entry.takenAt)
    val slot = entry.slot.label()
    val reading = entry.reading
    val status = entry.status
    return buildList {
        if (reading != null && status != null) {
            add(stringResource(R.string.logbook_entry_reading, time, slot, UiFormat.reading(context, reading.tenths, reading.meterFlag), statusPhrase(context, status)))
        } else {
            add(stringResource(R.string.logbook_entry_time, time, slot))
        }
        doseSummary(context, entry.doses)?.let { add(stringResource(R.string.logbook_sentence, it)) }
        entry.notes.forEach { add(stringResource(R.string.logbook_note, it)) }
    }.joinToString(" ")
}

/** A check's status in words, what LO or HI means, every dose and the reading's note. */
@Composable
internal fun CheckFacts(entry: CheckEntry) {
    val context = LocalContext.current
    val reading = entry.reading
    entry.status?.let { StatusChip(it) }
    reading?.meterFlag?.let { Text(UiFormat.reading(context, reading.tenths, it), style = MaterialTheme.typography.bodyLarge) }
    entry.doses.forEach { dose -> IconLine(R.drawable.ic_medication, doseText(context, dose)) }
    reading?.note?.let { IconLine(R.drawable.ic_description, stringResource(R.string.logbook_note, it)) }
}

@Composable
private fun MeasurementRow(entry: MeasurementEntry, expanded: Boolean, onToggle: () -> Unit, onOpenDay: () -> Unit) {
    val context = LocalContext.current
    val m = entry.measurement
    val time = UiFormat.time(context, m.takenAt)
    val label = stringResource(
        when {
            m.weightGrams != null && m.waistMm != null -> R.string.day_measurements
            m.weightGrams != null -> R.string.progress_weight_title
            else -> R.string.progress_waist_title
        },
    )
    val parts = listOfNotNull(
        m.weightGrams?.let { stringResource(R.string.logbook_weight, Measures.formatKg(it)) },
        m.waistMm?.let { stringResource(R.string.logbook_waist, Measures.formatCm(it)) },
    )
    val description = (listOf(time).plus(parts).map { stringResource(R.string.logbook_sentence, it) } +
        listOfNotNull(m.note?.let { stringResource(R.string.logbook_note, it) })).joinToString(" ")
    RowFrame(
        time = time,
        label = label,
        description = description,
        expanded = expanded,
        onToggle = onToggle,
        value = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                m.weightGrams?.let { MeasurePill(Measures.formatKg(it), stringResource(R.string.unit_kg)) }
                m.waistMm?.let { MeasurePill(Measures.formatCm(it), stringResource(R.string.unit_cm)) }
            }
        },
        summary = { m.note?.let { IconLine(R.drawable.ic_description, stringResource(R.string.logbook_note, it), maxLines = 2) } },
        details = {
            m.note?.let { IconLine(R.drawable.ic_description, stringResource(R.string.logbook_note, it)) }
            BigButton(stringResource(R.string.logbook_open_day), onClick = onOpenDay, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY)
        },
    )
}

/**
 * One record of the list: the time and the time of day on the left, the value on the right and a short summary
 * underneath. Pressing it shows [details] in place of the summary. TalkBack reads [description] for the whole line.
 */
@Composable
private fun RowFrame(
    time: String,
    label: String,
    description: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    value: @Composable () -> Unit,
    summary: @Composable ColumnScope.() -> Unit,
    details: @Composable ColumnScope.() -> Unit,
) {
    val prefs = LocalUiPrefs.current
    val actionLabel = stringResource(if (expanded) R.string.logbook_hide_details else R.string.logbook_show_details)
    val state = stringResource(if (expanded) R.string.logbook_details_shown else R.string.logbook_details_hidden)
    Column(Modifier.fillMaxWidth().then(if (prefs.reduceMotion) Modifier else Modifier.animateContentSize())) {
        Column(
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = description
                    stateDescription = state
                    role = Role.Button
                    onClick(label = actionLabel) {
                        onToggle()
                        true
                    }
                }
                .clickable(onClick = onToggle)
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // With large text the value moves under the time rather than squeezing it.
            FlowRow(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(end = 12.dp)) {
                    Text(time, style = MaterialTheme.typography.titleLarge)
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    value()
                    Icon(
                        painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp).size(32.dp),
                    )
                }
            }
            if (!expanded) summary()
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                details()
            }
        }
        HorizontalDivider()
    }
}

/** A line of text with a small icon in front of it, such as a dose or a note. */
@Composable
internal fun IconLine(icon: Int, text: String, maxLines: Int = Int.MAX_VALUE) {
    Row(verticalAlignment = Alignment.Top) {
        if (LocalUiPrefs.current.showIcons) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp).size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The doses of a check in a few words: each insulin given or not given, then how many other medicines were
 * recorded. Doses the scale said were not needed are left out here; the details list every dose.
 */
fun doseSummary(context: Context, doses: List<DoseRecord>): String? {
    val insulin = doses.filter { it.medicineKind == MedicineKind.INSULIN && it.status != DoseStatus.NOT_NEEDED }
    val others = doses.count { it.medicineKind != MedicineKind.INSULIN }
    val parts = insulin.map { dose ->
        when (dose.status) {
            DoseStatus.GIVEN -> context.getString(R.string.logbook_summary_given, dose.medicineName, UiFormat.dose(context, dose.givenAmount ?: 0.0, dose.medicineKind, dose.unit))
            else -> context.getString(R.string.logbook_dose_skipped, dose.medicineName)
        }
    } + listOfNotNull(
        when {
            others == 0 -> null
            insulin.isEmpty() -> context.resources.getQuantityString(R.plurals.logbook_summary_medicines, others, others)
            else -> context.resources.getQuantityString(R.plurals.logbook_summary_others, others, others)
        },
    )
    return if (parts.isEmpty()) null else UiFormat.joinWithAnd(context, parts)
}

private fun statusPhrase(context: Context, status: GlucoseStatus): String = context.getString(
    when (status) {
        GlucoseStatus.LOW -> R.string.progress_point_low
        GlucoseStatus.BELOW_TARGET -> R.string.progress_point_below
        GlucoseStatus.IN_TARGET -> R.string.progress_point_in
        GlucoseStatus.ABOVE_TARGET -> R.string.progress_point_above
    },
)

/** What TalkBack reads for a day of the chart: how many readings it had and their range. */
internal fun describeChartDay(context: Context, date: LocalDate, day: LogbookDay?): String {
    val name = UiFormat.dayLong(date)
    val readings = day?.readings.orEmpty()
    return when (readings.size) {
        0 -> context.getString(R.string.logbook_chart_day_none, name)
        1 -> context.getString(R.string.logbook_chart_day_one, name, UiFormat.reading(context, readings[0].tenths, readings[0].meterFlag))
        else -> {
            val low = readings.minBy { it.tenths }
            val high = readings.maxBy { it.tenths }
            context.getString(
                R.string.logbook_chart_day_range,
                name,
                UiFormat.readings(context, readings.size),
                UiFormat.reading(context, low.tenths, low.meterFlag),
                UiFormat.reading(context, high.tenths, high.meterFlag),
            )
        }
    }
}

/** The chart takes about this share of the screen's height, within the limits below. */
private const val CHART_SHARE = 0.22f
private val MIN_CHART = 120.dp
private val MAX_CHART = 176.dp
