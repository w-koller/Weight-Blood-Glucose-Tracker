package io.github.wkoller.glucosetracker.backup

import io.github.wkoller.glucosetracker.data.db.BackupContents
import io.github.wkoller.glucosetracker.data.db.BodyMeasurementEntity
import io.github.wkoller.glucosetracker.data.db.DoseLogEntity
import io.github.wkoller.glucosetracker.data.db.GlucoseReadingEntity
import io.github.wkoller.glucosetracker.data.db.PlanEntity
import io.github.wkoller.glucosetracker.data.db.PlanMedicineEntity
import io.github.wkoller.glucosetracker.data.db.PlanSlotEntity
import io.github.wkoller.glucosetracker.data.db.ScaleBandEntity
import io.github.wkoller.glucosetracker.data.prefs.Settings
import io.github.wkoller.glucosetracker.data.prefs.TextSize
import io.github.wkoller.glucosetracker.data.prefs.ThemeChoice
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** What a backup file holds: every record, and the settings that belong to the person. */
data class Backup(val createdAt: Long, val appVersion: String, val contents: BackupContents, val settings: Settings) {
    val summary: BackupSummary
        get() = BackupSummary(
            createdAt = createdAt,
            readings = contents.readings.size,
            doses = contents.doses.size,
            weights = contents.measurements.count { it.weightGrams != null },
        )
}

data class BackupSummary(val createdAt: Long, val readings: Int, val doses: Int, val weights: Int)

sealed interface BackupReadResult {
    data class Ok(val backup: Backup) : BackupReadResult
    data object NeedsPassword : BackupReadResult

    /** The password is wrong, or the encrypted file has been damaged. The two cannot be told apart. */
    data object WrongPassword : BackupReadResult
    data object NotABackup : BackupReadResult
    data object NewerVersion : BackupReadResult
}

/**
 * The backup file: JSON with a short header. With a password, the records are encrypted with
 * AES-256-GCM using a key made from the password with PBKDF2, so a wrong password or a changed file
 * is detected rather than producing wrong records.
 */
object BackupFile {
    const val FORMAT = "weight-glucose-backup"
    const val VERSION = 1
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    fun write(backup: Backup, password: String?): ByteArray {
        val data = dataJson(backup)
        val file = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", backup.createdAt)
            .put("appVersion", backup.appVersion)
        if (password.isNullOrEmpty()) {
            file.put("encrypted", false).put("data", data)
        } else {
            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
            val encrypted = cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8))
            file.put("encrypted", true)
                .put("kdf", JSONObject().put("name", "PBKDF2WithHmacSHA256").put("iterations", ITERATIONS).put("salt", b64(salt)))
                .put("cipher", JSONObject().put("name", "AES/GCM/NoPadding").put("iv", b64(iv)))
                .put("data", b64(encrypted))
        }
        return file.toString(2).toByteArray(Charsets.UTF_8)
    }

    fun read(bytes: ByteArray, password: String?): BackupReadResult {
        val file = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (_: JSONException) {
            return BackupReadResult.NotABackup
        }
        if (file.optString("format") != FORMAT) return BackupReadResult.NotABackup
        if (file.optInt("version", 0) > VERSION) return BackupReadResult.NewerVersion
        return try {
            val data = if (file.optBoolean("encrypted")) {
                if (password.isNullOrEmpty()) return BackupReadResult.NeedsPassword
                val kdf = file.getJSONObject("kdf")
                val cipherInfo = file.getJSONObject("cipher")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key(password, unb64(kdf.getString("salt")), kdf.getInt("iterations")),
                    GCMParameterSpec(TAG_BITS, unb64(cipherInfo.getString("iv"))),
                )
                val plain = try {
                    cipher.doFinal(unb64(file.getString("data")))
                } catch (_: AEADBadTagException) {
                    return BackupReadResult.WrongPassword
                }
                JSONObject(String(plain, Charsets.UTF_8))
            } else {
                file.getJSONObject("data")
            }
            BackupReadResult.Ok(backupFrom(file, data))
        } catch (_: JSONException) {
            BackupReadResult.NotABackup
        } catch (_: IllegalArgumentException) {
            BackupReadResult.NotABackup
        }
    }

    private fun key(password: String, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)

    // Records as JSON. Every id is kept so that readings, doses and plan versions stay linked.

    private fun dataJson(backup: Backup): JSONObject {
        val c = backup.contents
        return JSONObject()
            .put("plans", JSONArray(c.plans.map { planJson(it) }))
            .put("bands", JSONArray(c.bands.map { JSONObject().put("id", it.id).put("planId", it.planId).put("position", it.position).put("upperTenths", it.upperTenths ?: JSONObject.NULL).put("units", it.units) }))
            .put("slots", JSONArray(c.slots.map { JSONObject().put("id", it.id).put("planId", it.planId).put("slot", it.slot).put("checkBgl", it.checkBgl).put("correctionPrescribed", it.correctionPrescribed).put("minuteOfDay", it.minuteOfDay).put("reminderOn", it.reminderOn) }))
            .put("medicines", JSONArray(c.medicines.map { JSONObject().put("id", it.id).put("planId", it.planId).put("medicineKey", it.medicineKey).put("position", it.position).put("name", it.name).put("strength", it.strength).put("kind", it.kind).put("unit", it.unit).put("slot", it.slot).put("amount", it.amount) }))
            .put("readings", JSONArray(c.readings.map { JSONObject().put("id", it.id).put("planId", it.planId).put("takenAt", it.takenAt).put("date", it.date).put("slot", it.slot).put("tenths", it.tenths).put("meterFlag", it.meterFlag ?: JSONObject.NULL).put("note", it.note ?: JSONObject.NULL) }))
            .put("doses", JSONArray(c.doses.map { doseJson(it) }))
            .put("measurements", JSONArray(c.measurements.map { JSONObject().put("id", it.id).put("takenAt", it.takenAt).put("date", it.date).put("weightGrams", it.weightGrams ?: JSONObject.NULL).put("waistMm", it.waistMm ?: JSONObject.NULL).put("note", it.note ?: JSONObject.NULL) }))
            .put("settings", settingsJson(backup.settings))
    }

    private fun planJson(p: PlanEntity) = JSONObject()
        .put("id", p.id).put("cycleId", p.cycleId).put("version", p.version).put("effectiveFrom", p.effectiveFrom)
        .put("correctionInsulin", p.correctionInsulin).put("prescriber", p.prescriber).put("orderDate", p.orderDate ?: JSONObject.NULL)
        .put("startDate", p.startDate).put("startWeightGrams", p.startWeightGrams ?: JSONObject.NULL).put("startWaistMm", p.startWaistMm ?: JSONObject.NULL)
        .put("goalWeightGrams", p.goalWeightGrams ?: JSONObject.NULL).put("goalWaistMm", p.goalWaistMm ?: JSONObject.NULL)
        .put("targetDate", p.targetDate ?: JSONObject.NULL).put("weighEvery", p.weighEvery).put("waistEvery", p.waistEvery)
        .put("targetLowTenths", p.targetLowTenths).put("targetHighTenths", p.targetHighTenths).put("hypoTenths", p.hypoTenths)

    private fun doseJson(d: DoseLogEntity) = JSONObject()
        .put("id", d.id).put("planId", d.planId).put("readingId", d.readingId ?: JSONObject.NULL).put("takenAt", d.takenAt).put("date", d.date)
        .put("slot", d.slot).put("kind", d.kind).put("medicineKey", d.medicineKey ?: JSONObject.NULL).put("medicineName", d.medicineName)
        .put("medicineKind", d.medicineKind).put("prescribedAmount", d.prescribedAmount ?: JSONObject.NULL).put("givenAmount", d.givenAmount ?: JSONObject.NULL)
        .put("unit", d.unit).put("status", d.status).put("reason", d.reason ?: JSONObject.NULL).put("note", d.note ?: JSONObject.NULL)

    private fun settingsJson(s: Settings) = JSONObject()
        .put("textSize", s.textSize.name).put("maxContrast", s.maxContrast).put("theme", s.theme.name)
        .put("showIcons", s.showIcons).put("showHelperText", s.showHelperText).put("simpleMode", s.simpleMode)
        .put("readAloud", s.readAloud).put("speechRate", s.speechRate.toDouble()).put("haptics", s.haptics).put("reduceMotion", s.reduceMotion)
        .put("remindersOn", s.remindersOn).put("pinHash", s.pinHash ?: JSONObject.NULL).put("pinSalt", s.pinSalt ?: JSONObject.NULL)
        .put("firstName", s.firstName).put("familyName", s.familyName).put("voice", s.voice).put("backupReminderOn", s.backupReminderOn)

    private fun backupFrom(file: JSONObject, d: JSONObject): Backup {
        fun JSONObject.intOrNull(name: String): Int? = if (isNull(name)) null else getInt(name)
        fun JSONObject.longOrNull(name: String): Long? = if (isNull(name)) null else getLong(name)
        fun JSONObject.doubleOrNull(name: String): Double? = if (isNull(name)) null else getDouble(name)
        fun JSONObject.stringOrNull(name: String): String? = if (isNull(name)) null else getString(name)
        fun <T> JSONObject.rows(name: String, read: (JSONObject) -> T): List<T> {
            val array = getJSONArray(name)
            return (0 until array.length()).map { read(array.getJSONObject(it)) }
        }
        val contents = BackupContents(
            plans = d.rows("plans") {
                PlanEntity(
                    id = it.getLong("id"), cycleId = it.getString("cycleId"), version = it.getInt("version"), effectiveFrom = it.getLong("effectiveFrom"),
                    correctionInsulin = it.getString("correctionInsulin"), prescriber = it.getString("prescriber"), orderDate = it.stringOrNull("orderDate"),
                    startDate = it.getString("startDate"), startWeightGrams = it.intOrNull("startWeightGrams"), startWaistMm = it.intOrNull("startWaistMm"),
                    goalWeightGrams = it.intOrNull("goalWeightGrams"), goalWaistMm = it.intOrNull("goalWaistMm"), targetDate = it.stringOrNull("targetDate"),
                    weighEvery = it.getString("weighEvery"), waistEvery = it.getString("waistEvery"), targetLowTenths = it.getInt("targetLowTenths"),
                    targetHighTenths = it.getInt("targetHighTenths"), hypoTenths = it.getInt("hypoTenths"),
                )
            },
            bands = d.rows("bands") { ScaleBandEntity(it.getLong("id"), it.getLong("planId"), it.getInt("position"), it.intOrNull("upperTenths"), it.getInt("units")) },
            slots = d.rows("slots") {
                PlanSlotEntity(it.getLong("id"), it.getLong("planId"), it.getString("slot"), it.getBoolean("checkBgl"), it.getBoolean("correctionPrescribed"), it.getInt("minuteOfDay"), it.getBoolean("reminderOn"))
            },
            medicines = d.rows("medicines") {
                PlanMedicineEntity(
                    it.getLong("id"), it.getLong("planId"), it.getString("medicineKey"), it.getInt("position"), it.getString("name"),
                    it.getString("strength"), it.getString("kind"), it.getString("unit"), it.getString("slot"), it.getDouble("amount"),
                )
            },
            readings = d.rows("readings") {
                GlucoseReadingEntity(
                    it.getLong("id"), it.getLong("planId"), it.getLong("takenAt"), it.getString("date"), it.getString("slot"),
                    it.getInt("tenths"), it.stringOrNull("meterFlag"), it.stringOrNull("note"),
                )
            },
            doses = d.rows("doses") {
                DoseLogEntity(
                    id = it.getLong("id"), planId = it.getLong("planId"), readingId = it.longOrNull("readingId"), takenAt = it.getLong("takenAt"),
                    date = it.getString("date"), slot = it.getString("slot"), kind = it.getString("kind"), medicineKey = it.stringOrNull("medicineKey"),
                    medicineName = it.getString("medicineName"), medicineKind = it.getString("medicineKind"), prescribedAmount = it.doubleOrNull("prescribedAmount"),
                    givenAmount = it.doubleOrNull("givenAmount"), unit = it.getString("unit"), status = it.getString("status"),
                    reason = it.stringOrNull("reason"), note = it.stringOrNull("note"),
                )
            },
            measurements = d.rows("measurements") {
                BodyMeasurementEntity(it.getLong("id"), it.getLong("takenAt"), it.getString("date"), it.intOrNull("weightGrams"), it.intOrNull("waistMm"), it.stringOrNull("note"))
            },
        )
        val s = d.getJSONObject("settings")
        val defaults = Settings()
        val settings = Settings(
            setupDone = true,
            textSize = runCatching { TextSize.valueOf(s.getString("textSize")) }.getOrDefault(defaults.textSize),
            maxContrast = s.optBoolean("maxContrast", defaults.maxContrast),
            theme = runCatching { ThemeChoice.valueOf(s.getString("theme")) }.getOrDefault(defaults.theme),
            showIcons = s.optBoolean("showIcons", defaults.showIcons),
            showHelperText = s.optBoolean("showHelperText", defaults.showHelperText),
            simpleMode = s.optBoolean("simpleMode", defaults.simpleMode),
            readAloud = s.optBoolean("readAloud", defaults.readAloud),
            speechRate = s.optDouble("speechRate", defaults.speechRate.toDouble()).toFloat(),
            haptics = s.optBoolean("haptics", defaults.haptics),
            reduceMotion = s.optBoolean("reduceMotion", defaults.reduceMotion),
            remindersOn = s.optBoolean("remindersOn", defaults.remindersOn),
            pinHash = s.stringOrNull("pinHash"),
            pinSalt = s.stringOrNull("pinSalt"),
            firstName = s.optString("firstName", ""),
            familyName = s.optString("familyName", ""),
            voice = s.optString("voice", ""),
            backupReminderOn = s.optBoolean("backupReminderOn", defaults.backupReminderOn),
        )
        return Backup(file.getLong("createdAt"), file.optString("appVersion"), contents, settings)
    }
}
