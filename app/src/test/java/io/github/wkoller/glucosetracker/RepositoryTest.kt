package io.github.wkoller.glucosetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.glucosetracker.data.TrackerRepository
import io.github.wkoller.glucosetracker.data.db.AppDatabase
import io.github.wkoller.glucosetracker.data.db.toEntities
import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.Slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: TrackerRepository
    private val clock = Clock.fixed(Instant.parse("2026-10-09T02:00:00Z"), ZoneId.of("Australia/Brisbane"))

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TrackerRepository(db, clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a saved plan reads back the same`() = runBlocking {
        val plan = TestPlans.example()
        repository.savePlanVersion(plan)
        val loaded = repository.currentPlan()!!
        assertEquals(plan.scale, loaded.scale)
        assertEquals(plan.slots, loaded.slots)
        assertEquals(plan.medicines, loaded.medicines)
        assertEquals(plan.goals, loaded.goals)
        assertEquals(plan.targets, loaded.targets)
        assertEquals(1, loaded.version)
    }

    @Test
    fun `a change is saved as a new version of the same cycle`() = runBlocking {
        val plan = TestPlans.example()
        repository.savePlanVersion(plan)
        val first = repository.currentPlan()!!
        repository.savePlanVersion(plan.copy(correctionInsulin = "Fiasp"))
        val second = repository.currentPlan()!!
        assertEquals(2, second.version)
        assertEquals("Fiasp", second.correctionInsulin)
        assertEquals(listOf(second.id, first.id), repository.planHistory.first().map { it.id })
        repository.savePlanVersion(TestPlans.example(cycleId = "cycle-2"))
        assertEquals(1, repository.currentPlan()!!.version)
    }

    @Test
    fun `records are found by day`() = runBlocking {
        val date = LocalDate.of(2026, 10, 9)
        val at = Instant.parse("2026-10-08T21:40:00Z")
        val readingId = repository.addReading(GlucoseReading(0, 1, at, date, Slot.BREAKFAST, 137, null, null))
        repository.addDose(
            DoseRecord(0, 1, readingId, at, date, Slot.BREAKFAST, DoseKind.CORRECTION, null, "NovoRapid", MedicineKind.INSULIN, 4.0, 4.0, "units", DoseStatus.GIVEN, null, null),
        )
        repository.addMeasurement(BodyMeasurement(0, at, date, 116_300, null, null))
        val day = repository.dayRecords(date)
        assertEquals(137, day.readings.single().tenths)
        assertEquals(4.0, day.doses.single().givenAmount!!, 0.0)
        assertEquals(date, day.lastWeightDate)
        assertEquals(0, repository.dayRecords(date.plusDays(1)).readings.size)
    }

    @Test
    fun `a record added later gets the plan version in force at the time`() = runBlocking {
        val first = TestPlans.example().toEntities(1, Instant.parse("2026-10-01T00:00:00Z"))
        val second = TestPlans.example().copy(correctionInsulin = "Humalog").toEntities(2, Instant.parse("2026-10-05T00:00:00Z"))
        val firstId = db.planDao().insertFull(first.plan, first.bands, first.slots, first.medicines)
        val secondId = db.planDao().insertFull(second.plan, second.bands, second.slots, second.medicines)

        assertEquals(firstId, repository.planAt(Instant.parse("2026-10-03T08:00:00Z"))!!.id)
        assertEquals(secondId, repository.planAt(Instant.parse("2026-10-07T08:00:00Z"))!!.id)
        // A moment before the first version belongs to the first version.
        assertEquals(firstId, repository.planAt(Instant.parse("2026-09-20T08:00:00Z"))!!.id)
    }
}
