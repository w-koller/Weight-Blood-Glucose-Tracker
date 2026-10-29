package io.github.wkoller.glucosetracker

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.glucosetracker.backup.BackupFile
import io.github.wkoller.glucosetracker.backup.BackupReadResult
import io.github.wkoller.glucosetracker.backup.BackupReminderPolicy
import io.github.wkoller.glucosetracker.data.db.ScaleBandEntity
import io.github.wkoller.glucosetracker.data.prefs.Settings
import io.github.wkoller.glucosetracker.data.prefs.TextSize
import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.model.SlotNaming
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val app: TrackerApp get() = ApplicationProvider.getApplicationContext()
    private val container get() = app.container
    private val dao get() = container.database.backupDao()

    @Before
    fun resetSettings() = runBlocking { container.settingsStore.update { Settings() } }

    /** Two plan versions, a reading linked to the first, a dose linked to the reading, a weight, and settings. */
    private fun seed() = runBlocking {
        val repository = container.repository
        repository.savePlanVersion(TestPlans.example())
        val first = repository.currentPlan()!!
        val date = LocalDate.of(2026, 10, 9)
        val readingId = repository.addReading(GlucoseReading(0, first.id, repository.instant(), date, Slot.BREAKFAST, 137, null, "Before a walk"))
        repository.addDose(
            DoseRecord(0, first.id, readingId, repository.instant(), date, Slot.BREAKFAST, DoseKind.CORRECTION, null, "NovoRapid", MedicineKind.INSULIN, 4.0, 4.0, "units", DoseStatus.GIVEN, null, null),
        )
        repository.addMeasurement(BodyMeasurement(0, repository.instant(), date, 116_300, 1_150, null))
        repository.savePlanVersion(first.copy(correctionInsulin = "Fiasp"))
        container.settingsStore.update {
            it.copy(
                setupDone = true, firstName = "Mary", textSize = TextSize.EXTRA_LARGE, voice = "natural:7",
                slotNaming = SlotNaming.TIMES_OF_DAY, supportName = "Anne", supportPhone = "0400 000 000",
            )
        }
    }

    private fun wipe() = runBlocking {
        dao.replaceAll(dao.everything().copy(plans = emptyList(), bands = emptyList(), slots = emptyList(), medicines = emptyList(), readings = emptyList(), doses = emptyList(), measurements = emptyList()))
        container.settingsStore.update { Settings() }
    }

    private fun roundTrip(password: String?) {
        seed()
        val before = runBlocking { dao.everything() }
        val bytes = runBlocking { container.backup.create(password) }
        wipe()
        assertTrue(runBlocking { dao.everything() }.plans.isEmpty())

        val result = BackupFile.read(bytes, password)
        assertTrue(result is BackupReadResult.Ok)
        runBlocking { container.backup.restore((result as BackupReadResult.Ok).backup) }

        val after = runBlocking { dao.everything() }
        assertEquals(before, after)
        val reading = after.readings.single()
        assertEquals(reading.id, after.doses.single().readingId)
        assertTrue(after.plans.any { it.id == reading.planId })
        val settings = runBlocking { container.settingsStore.current() }
        assertEquals("Mary", settings.firstName)
        assertEquals(TextSize.EXTRA_LARGE, settings.textSize)
        assertEquals("natural:7", settings.voice)
        assertEquals(SlotNaming.TIMES_OF_DAY, settings.slotNaming)
        assertEquals("Anne", settings.supportName)
        assertEquals("0400 000 000", settings.supportPhone)
        assertTrue(settings.setupDone)
        assertEquals("Fiasp", runBlocking { container.repository.currentPlan()!!.correctionInsulin })
    }

    @Test
    fun `a plain backup restores every record and setting`() = roundTrip(password = null)

    @Test
    fun `a password-protected backup restores with the right password`() = roundTrip(password = "correct horse")

    @Test
    fun `problems with a backup file are told apart`() {
        seed()
        val locked = runBlocking { container.backup.create("secret pass") }
        assertEquals(BackupReadResult.NeedsPassword, BackupFile.read(locked, null))
        assertEquals(BackupReadResult.WrongPassword, BackupFile.read(locked, "wrong pass"))

        // Change one bit of the encrypted data: the file is now damaged, which the encryption reports as it does a
        // wrong password. The bit is changed in the decoded data, not the file's text, because JSON writes a '/'
        // as "\/": editing the text where the data starts with '/' added a character instead, about 1 run in 64.
        val file = JSONObject(String(locked))
        val data = Base64.getDecoder().decode(file.getString("data"))
        data[0] = (data[0].toInt() xor 1).toByte()
        file.put("data", Base64.getEncoder().encodeToString(data))
        assertEquals(BackupReadResult.WrongPassword, BackupFile.read(file.toString().toByteArray(), "secret pass"))

        // Encrypted data that is not base64 at all is damaged.
        file.put("data", "not base64!")
        assertEquals(BackupReadResult.Damaged, BackupFile.read(file.toString().toByteArray(), "secret pass"))

        assertEquals(BackupReadResult.NotABackup, BackupFile.read("Date,Weight\n9/10/2026,116.3".toByteArray(), null))
        assertEquals(BackupReadResult.NotABackup, BackupFile.read("""{"format":"something-else","version":1}""".toByteArray(), null))
        assertEquals(BackupReadResult.NewerVersion, BackupFile.read("""{"format":"weight-glucose-backup","version":99}""".toByteArray(), null))
    }

    @Test
    fun `a damaged backup is refused before anything is replaced`() {
        seed()
        val text = String(runBlocking { container.backup.create(null) })
        val before = runBlocking { dao.everything() }

        // A time of day this version does not know, as an older app would find in a file from a newer one.
        val unknownSlot = text.replaceFirst("\"slot\": \"BREAKFAST\"", "\"slot\": \"BRUNCH\"")
        assertEquals(BackupReadResult.Damaged, BackupFile.read(unknownSlot.toByteArray(), null))

        // A plan whose sliding scale has no rows cannot give a dose.
        val noScale = text.replace(Regex("\"bands\": \\[[^\\]]*]"), "\"bands\": []")
        assertEquals(BackupReadResult.Damaged, BackupFile.read(noScale.toByteArray(), null))

        // A file that asks for an impossible amount of work to open it is refused at once.
        val locked = String(runBlocking { container.backup.create("secret pass") })
        val slow = locked.replace("\"iterations\": 210000", "\"iterations\": 2000000000")
        assertEquals(BackupReadResult.Damaged, BackupFile.read(slow.toByteArray(), "secret pass"))

        assertEquals(before, runBlocking { dao.everything() })
    }

    @Test
    fun `a restore that fails leaves the records on this phone`() {
        seed()
        val before = runBlocking { dao.everything() }
        // A scale band pointing at a plan that does not exist breaks the database's rules part way through.
        val broken = before.copy(bands = before.bands + ScaleBandEntity(id = 999, planId = 12345, position = 0, upperTenths = 100, units = 0))
        val failed = runCatching { runBlocking { dao.replaceAll(broken) } }
        assertTrue(failed.isFailure)
        assertEquals(before, runBlocking { dao.everything() })
    }

    @Test
    fun `the backup reminder is due after 30 days, at most once a week`() {
        val day = BackupReminderPolicy.DAY_MS
        val now = 100 * day
        assertFalse(BackupReminderPolicy.isDue(now, lastBackupAt = now - 10 * day, firstPlanAt = null, lastReminderAt = 0))
        assertTrue(BackupReminderPolicy.isDue(now, lastBackupAt = now - 31 * day, firstPlanAt = null, lastReminderAt = 0))
        assertFalse(BackupReminderPolicy.isDue(now, lastBackupAt = now - 31 * day, firstPlanAt = null, lastReminderAt = now - 2 * day))
        assertTrue(BackupReminderPolicy.isDue(now, lastBackupAt = 0, firstPlanAt = now - 40 * day, lastReminderAt = 0))
        assertFalse(BackupReminderPolicy.isDue(now, lastBackupAt = 0, firstPlanAt = null, lastReminderAt = 0))
    }
}
