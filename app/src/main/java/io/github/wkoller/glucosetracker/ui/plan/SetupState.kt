package io.github.wkoller.glucosetracker.ui.plan

import io.github.wkoller.glucosetracker.domain.model.Frequency
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.Slot
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * The part of a plan setup that is worth keeping if Android closes the app while it is in the background, for
 * example while the user looks at a photo of the doctor's order. A chosen PIN is kept only as its salt and hash.
 */
data class SavedSetup(
    val step: SetupStep,
    val returnToReview: Boolean,
    val draft: PlanDraft,
    val firstName: String,
    val familyName: String,
    val supportName: String,
    val supportPhone: String,
    val downloadModel: Boolean?,
    val modelOverMobile: Boolean,
    val downloadVoices: Boolean?,
    val voicesOverMobile: Boolean,
    val pinSalt: String?,
    val pinHash: String?,
) {
    fun applyTo(ui: SetupUi): SetupUi = ui.copy(
        step = if (step in ui.steps) step else ui.step,
        returnToReview = returnToReview,
        draft = draft,
        firstName = firstName,
        familyName = familyName,
        supportName = supportName,
        supportPhone = supportPhone,
        downloadModel = downloadModel,
        modelOverMobile = modelOverMobile,
        downloadVoices = downloadVoices,
        voicesOverMobile = voicesOverMobile,
        chosenPinSalt = pinSalt,
        chosenPinHash = pinHash,
    )

    companion object {
        fun of(ui: SetupUi) = SavedSetup(
            ui.step, ui.returnToReview, ui.draft, ui.firstName, ui.familyName, ui.supportName, ui.supportPhone,
            ui.downloadModel, ui.modelOverMobile, ui.downloadVoices, ui.voicesOverMobile, ui.chosenPinSalt, ui.chosenPinHash,
        )
    }
}

/** [SavedSetup] as JSON text, which a SavedStateHandle can hold. */
object SetupStateJson {

    fun encode(saved: SavedSetup): String = JSONObject()
        .put("step", saved.step.name)
        .put("returnToReview", saved.returnToReview)
        .put("draft", draftJson(saved.draft))
        .put("firstName", saved.firstName)
        .put("familyName", saved.familyName)
        .put("supportName", saved.supportName)
        .put("supportPhone", saved.supportPhone)
        .put("downloadModel", saved.downloadModel ?: JSONObject.NULL)
        .put("modelOverMobile", saved.modelOverMobile)
        .put("downloadVoices", saved.downloadVoices ?: JSONObject.NULL)
        .put("voicesOverMobile", saved.voicesOverMobile)
        .put("pinSalt", saved.pinSalt ?: JSONObject.NULL)
        .put("pinHash", saved.pinHash ?: JSONObject.NULL)
        .toString()

    /** The saved setup, or null when the text cannot be read, in which case the setup starts afresh. */
    fun decode(text: String): SavedSetup? = try {
        val o = JSONObject(text)
        SavedSetup(
            step = SetupStep.valueOf(o.getString("step")),
            returnToReview = o.getBoolean("returnToReview"),
            draft = draftFrom(o.getJSONObject("draft")),
            firstName = o.getString("firstName"),
            familyName = o.getString("familyName"),
            supportName = o.getString("supportName"),
            supportPhone = o.getString("supportPhone"),
            downloadModel = if (o.isNull("downloadModel")) null else o.getBoolean("downloadModel"),
            modelOverMobile = o.getBoolean("modelOverMobile"),
            downloadVoices = if (o.isNull("downloadVoices")) null else o.getBoolean("downloadVoices"),
            voicesOverMobile = o.getBoolean("voicesOverMobile"),
            pinSalt = if (o.isNull("pinSalt")) null else o.getString("pinSalt"),
            pinHash = if (o.isNull("pinHash")) null else o.getString("pinHash"),
        )
    } catch (_: JSONException) {
        null
    } catch (_: RuntimeException) {
        // An unknown step or time from an older version of the app.
        null
    }

    private fun draftJson(d: PlanDraft): JSONObject = JSONObject()
        .put("cycleId", d.cycleId)
        .put("correctionInsulin", d.correctionInsulin)
        .put("prescriber", d.prescriber)
        .put("orderDate", d.orderDate?.toString() ?: JSONObject.NULL)
        .put("bands", JSONArray(d.bands.map { JSONObject().put("upper", it.upper).put("units", it.units) }))
        .put("scaleChecked", d.scaleChecked)
        .put(
            "slots",
            JSONArray(
                d.slots.map {
                    JSONObject().put("slot", it.slot.name).put("checkBgl", it.checkBgl).put("correctionPrescribed", it.correctionPrescribed)
                        .put("time", it.time.toString()).put("reminderOn", it.reminderOn)
                },
            ),
        )
        .put(
            "medicines",
            JSONArray(
                d.medicines.map { m ->
                    JSONObject().put("key", m.key).put("name", m.name).put("strength", m.strength).put("kind", m.kind.name).put("unit", m.unit)
                        .put("amounts", JSONObject().apply { m.amounts.forEach { (slot, text) -> put(slot.name, text) } })
                },
            ),
        )
        .put("startDate", d.startDate.toString())
        .put("startWeight", d.startWeight)
        .put("startWaist", d.startWaist)
        .put("goalWeight", d.goalWeight)
        .put("goalWaist", d.goalWaist)
        .put("targetDate", d.targetDate?.toString() ?: JSONObject.NULL)
        .put("weighEvery", d.weighEvery.name)
        .put("waistEvery", d.waistEvery.name)
        .put("targetLow", d.targetLow)
        .put("targetHigh", d.targetHigh)
        .put("hypo", d.hypo)

    private fun draftFrom(o: JSONObject): PlanDraft {
        fun <T> JSONArray.map(read: (JSONObject) -> T): List<T> = (0 until length()).map { read(getJSONObject(it)) }
        fun JSONObject.dateOrNull(name: String): LocalDate? = if (isNull(name)) null else LocalDate.parse(getString(name))
        return PlanDraft(
            cycleId = o.getString("cycleId"),
            correctionInsulin = o.getString("correctionInsulin"),
            prescriber = o.getString("prescriber"),
            orderDate = o.dateOrNull("orderDate"),
            bands = o.getJSONArray("bands").map { BandDraft(it.getString("upper"), it.getString("units")) },
            scaleChecked = o.getBoolean("scaleChecked"),
            slots = o.getJSONArray("slots").map {
                SlotDraft(
                    slot = Slot.valueOf(it.getString("slot")),
                    checkBgl = it.getBoolean("checkBgl"),
                    correctionPrescribed = it.getBoolean("correctionPrescribed"),
                    time = LocalTime.parse(it.getString("time")),
                    reminderOn = it.getBoolean("reminderOn"),
                )
            },
            medicines = o.getJSONArray("medicines").map { m ->
                val amounts = m.getJSONObject("amounts")
                MedicineDraft(
                    key = m.getString("key"),
                    name = m.getString("name"),
                    strength = m.getString("strength"),
                    kind = MedicineKind.valueOf(m.getString("kind")),
                    unit = m.getString("unit"),
                    amounts = amounts.keys().asSequence().associate { Slot.valueOf(it) to amounts.getString(it) },
                )
            },
            startDate = LocalDate.parse(o.getString("startDate")),
            startWeight = o.getString("startWeight"),
            startWaist = o.getString("startWaist"),
            goalWeight = o.getString("goalWeight"),
            goalWaist = o.getString("goalWaist"),
            targetDate = o.dateOrNull("targetDate"),
            weighEvery = Frequency.valueOf(o.getString("weighEvery")),
            waistEvery = Frequency.valueOf(o.getString("waistEvery")),
            targetLow = o.getString("targetLow"),
            targetHigh = o.getString("targetHigh"),
            hypo = o.getString("hypo"),
        )
    }
}
