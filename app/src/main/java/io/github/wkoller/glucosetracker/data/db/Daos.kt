package io.github.wkoller.glucosetracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {
    @Transaction
    @Query("SELECT * FROM plans ORDER BY effectiveFrom DESC, id DESC LIMIT 1")
    fun observeActive(): Flow<PlanWithDetails?>

    @Transaction
    @Query("SELECT * FROM plans ORDER BY effectiveFrom DESC, id DESC LIMIT 1")
    suspend fun active(): PlanWithDetails?

    @Transaction
    @Query("SELECT * FROM plans ORDER BY effectiveFrom DESC, id DESC")
    fun observeAll(): Flow<List<PlanWithDetails>>

    @Transaction
    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun byId(id: Long): PlanWithDetails?

    @Query("SELECT COALESCE(MAX(version), 0) FROM plans WHERE cycleId = :cycleId")
    suspend fun latestVersion(cycleId: String): Int

    @Insert
    suspend fun insertPlan(plan: PlanEntity): Long

    @Insert
    suspend fun insertBands(bands: List<ScaleBandEntity>)

    @Insert
    suspend fun insertSlots(slots: List<PlanSlotEntity>)

    @Insert
    suspend fun insertMedicines(medicines: List<PlanMedicineEntity>)

    @Transaction
    suspend fun insertFull(
        plan: PlanEntity,
        bands: List<ScaleBandEntity>,
        slots: List<PlanSlotEntity>,
        medicines: List<PlanMedicineEntity>,
    ): Long {
        val id = insertPlan(plan)
        insertBands(bands.map { it.copy(planId = id) })
        insertSlots(slots.map { it.copy(planId = id) })
        insertMedicines(medicines.map { it.copy(planId = id) })
        return id
    }
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM glucose_readings WHERE date BETWEEN :from AND :to ORDER BY takenAt")
    fun observeReadings(from: String, to: String): Flow<List<GlucoseReadingEntity>>

    @Query("SELECT * FROM glucose_readings WHERE date BETWEEN :from AND :to ORDER BY takenAt")
    suspend fun readings(from: String, to: String): List<GlucoseReadingEntity>

    @Insert
    suspend fun insertReading(reading: GlucoseReadingEntity): Long

    @Update
    suspend fun updateReading(reading: GlucoseReadingEntity)

    @Delete
    suspend fun deleteReading(reading: GlucoseReadingEntity)

    @Query("SELECT * FROM dose_log WHERE date BETWEEN :from AND :to ORDER BY takenAt")
    fun observeDoses(from: String, to: String): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_log WHERE date BETWEEN :from AND :to ORDER BY takenAt")
    suspend fun doses(from: String, to: String): List<DoseLogEntity>

    @Insert
    suspend fun insertDose(dose: DoseLogEntity): Long

    @Update
    suspend fun updateDose(dose: DoseLogEntity)

    @Delete
    suspend fun deleteDose(dose: DoseLogEntity)

    @Query("SELECT * FROM body_measurements ORDER BY takenAt")
    fun observeMeasurements(): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements ORDER BY takenAt")
    suspend fun measurements(): List<BodyMeasurementEntity>

    @Insert
    suspend fun insertMeasurement(measurement: BodyMeasurementEntity): Long

    @Update
    suspend fun updateMeasurement(measurement: BodyMeasurementEntity)

    @Delete
    suspend fun deleteMeasurement(measurement: BodyMeasurementEntity)

    @Query("SELECT MIN(date) FROM glucose_readings")
    suspend fun firstReadingDate(): String?
}
