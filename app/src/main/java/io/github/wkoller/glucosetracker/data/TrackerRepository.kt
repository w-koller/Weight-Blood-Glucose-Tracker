package io.github.wkoller.glucosetracker.data

import io.github.wkoller.glucosetracker.data.db.AppDatabase
import io.github.wkoller.glucosetracker.data.db.toDomain
import io.github.wkoller.glucosetracker.data.db.toEntities
import io.github.wkoller.glucosetracker.data.db.toEntity
import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.schedule.DayRecords
import io.github.wkoller.glucosetracker.domain.schedule.SlotResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** The single place the app reads and writes plans and records. */
class TrackerRepository(private val db: AppDatabase, private val clock: Clock) {
    private val plans = db.planDao()
    private val records = db.recordDao()

    val activePlan: Flow<Plan?> = plans.observeActive().map { it?.toDomain() }

    val planHistory: Flow<List<Plan>> = plans.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun currentPlan(): Plan? = plans.active()?.toDomain()

    suspend fun planById(id: Long): Plan? = plans.byId(id)?.toDomain()

    /** Saves [plan] as a new version of its cycle, in force from now. Earlier records keep their own version. */
    suspend fun savePlanVersion(plan: Plan): Long {
        val version = plans.latestVersion(plan.cycleId) + 1
        val rows = plan.toEntities(version, Instant.now(clock))
        return plans.insertFull(rows.plan, rows.bands, rows.slots, rows.medicines)
    }

    fun now(): LocalDateTime = LocalDateTime.now(clock)

    fun instant(): Instant = Instant.now(clock)

    /** The day records belong to. A late bedtime check after midnight still counts towards the evening before. */
    fun logicalToday(plan: Plan?): LocalDate = if (plan == null) LocalDate.now(clock) else SlotResolver.logicalDate(plan, now())

    fun readings(from: LocalDate, to: LocalDate): Flow<List<GlucoseReading>> =
        records.observeReadings(from.toString(), to.toString()).map { list -> list.map { it.toDomain() } }

    fun doses(from: LocalDate, to: LocalDate): Flow<List<DoseRecord>> =
        records.observeDoses(from.toString(), to.toString()).map { list -> list.map { it.toDomain() } }

    val measurements: Flow<List<BodyMeasurement>> = records.observeMeasurements().map { list -> list.map { it.toDomain() } }

    suspend fun readingsBetween(from: LocalDate, to: LocalDate): List<GlucoseReading> =
        records.readings(from.toString(), to.toString()).map { it.toDomain() }

    suspend fun dosesBetween(from: LocalDate, to: LocalDate): List<DoseRecord> =
        records.doses(from.toString(), to.toString()).map { it.toDomain() }

    suspend fun allMeasurements(): List<BodyMeasurement> = records.measurements().map { it.toDomain() }

    suspend fun firstReadingDate(): LocalDate? = records.firstReadingDate()?.let(LocalDate::parse)

    fun day(date: LocalDate): Flow<DayRecords> = combine(readings(date, date), doses(date, date), measurements) { r, d, m ->
        DayRecords(
            date = date,
            readings = r,
            doses = d,
            lastWeightDate = m.lastOrNull { it.weightGrams != null }?.date,
            lastWaistDate = m.lastOrNull { it.waistMm != null }?.date,
        )
    }

    suspend fun dayRecords(date: LocalDate): DayRecords {
        val m = allMeasurements()
        return DayRecords(
            date = date,
            readings = readingsBetween(date, date),
            doses = dosesBetween(date, date),
            lastWeightDate = m.lastOrNull { it.weightGrams != null }?.date,
            lastWaistDate = m.lastOrNull { it.waistMm != null }?.date,
        )
    }

    suspend fun addReading(reading: GlucoseReading): Long = records.insertReading(reading.toEntity())
    suspend fun updateReading(reading: GlucoseReading) = records.updateReading(reading.toEntity())
    suspend fun deleteReading(reading: GlucoseReading) = records.deleteReading(reading.toEntity())

    suspend fun addDose(dose: DoseRecord): Long = records.insertDose(dose.toEntity())
    suspend fun updateDose(dose: DoseRecord) = records.updateDose(dose.toEntity())
    suspend fun deleteDose(dose: DoseRecord) = records.deleteDose(dose.toEntity())

    suspend fun addMeasurement(measurement: BodyMeasurement): Long = records.insertMeasurement(measurement.toEntity())
    suspend fun updateMeasurement(measurement: BodyMeasurement) = records.updateMeasurement(measurement.toEntity())
    suspend fun deleteMeasurement(measurement: BodyMeasurement) = records.deleteMeasurement(measurement.toEntity())
}
