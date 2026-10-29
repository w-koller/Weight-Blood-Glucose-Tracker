package io.github.wkoller.glucosetracker.ui.checkin

import io.github.wkoller.glucosetracker.domain.model.Slot
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/**
 * Where a check-in had got to, kept so that it carries on if Android closes the app in the background, for
 * example while the user is on the phone after a low reading. The readings and doses themselves are already
 * saved, so only their ids are kept, and they are read back from the database.
 */
data class SavedCheckIn(
    val step: CheckInStep,
    val slot: Slot,
    val date: LocalDate,
    val readingId: Long?,
    val readingIds: List<Long>,
    val doseIds: List<Long>,
    val lowThisCheck: Boolean,
    val recheckAt: Instant?,
    val treatments: Map<Long, LowTreatment>,
    val note: String,
    val noteReadingId: Long?,
) {
    companion object {
        fun of(ui: CheckInUi) = SavedCheckIn(
            step = ui.step,
            slot = ui.slot,
            date = ui.date,
            readingId = ui.reading?.id,
            readingIds = ui.readings.map { it.id },
            doseIds = ui.recorded.map { it.id },
            lowThisCheck = ui.lowThisCheck,
            recheckAt = ui.recheckAt,
            treatments = ui.treatments,
            note = ui.note,
            noteReadingId = ui.noteReadingId,
        )
    }
}

/** [SavedCheckIn] as JSON text, which a SavedStateHandle can hold. */
object CheckInStateJson {

    fun encode(saved: SavedCheckIn): String = JSONObject()
        .put("step", saved.step.name)
        .put("slot", saved.slot.name)
        .put("date", saved.date.toString())
        .put("readingId", saved.readingId ?: JSONObject.NULL)
        .put("readingIds", JSONArray(saved.readingIds))
        .put("doseIds", JSONArray(saved.doseIds))
        .put("lowThisCheck", saved.lowThisCheck)
        .put("recheckAt", saved.recheckAt?.toEpochMilli() ?: JSONObject.NULL)
        .put("treatments", JSONObject().apply { saved.treatments.forEach { (id, treatment) -> put(id.toString(), treatment.name) } })
        .put("note", saved.note)
        .put("noteReadingId", saved.noteReadingId ?: JSONObject.NULL)
        .toString()

    /** The saved check-in, or null when the text cannot be read, in which case the check-in starts afresh. */
    fun decode(text: String): SavedCheckIn? = try {
        val o = JSONObject(text)
        fun longs(name: String): List<Long> = o.getJSONArray(name).let { array -> (0 until array.length()).map { array.getLong(it) } }
        val treatments = o.getJSONObject("treatments")
        SavedCheckIn(
            step = CheckInStep.valueOf(o.getString("step")),
            slot = Slot.valueOf(o.getString("slot")),
            date = LocalDate.parse(o.getString("date")),
            readingId = if (o.isNull("readingId")) null else o.getLong("readingId"),
            readingIds = longs("readingIds"),
            doseIds = longs("doseIds"),
            lowThisCheck = o.getBoolean("lowThisCheck"),
            recheckAt = if (o.isNull("recheckAt")) null else Instant.ofEpochMilli(o.getLong("recheckAt")),
            treatments = treatments.keys().asSequence().associate { it.toLong() to LowTreatment.valueOf(treatments.getString(it)) },
            note = o.getString("note"),
            noteReadingId = if (o.isNull("noteReadingId")) null else o.getLong("noteReadingId"),
        )
    } catch (_: JSONException) {
        null
    } catch (_: RuntimeException) {
        null
    }
}
