package io.github.wkoller.glucosetracker.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

// Dates are stored as ISO-8601 text (yyyy-MM-dd) so they sort correctly; instants as epoch milliseconds.

@Entity(tableName = "plans", indices = [Index("cycleId")])
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: String,
    val version: Int,
    val effectiveFrom: Long,
    val correctionInsulin: String,
    val prescriber: String,
    val orderDate: String?,
    val startDate: String,
    val startWeightGrams: Int?,
    val startWaistMm: Int?,
    val goalWeightGrams: Int?,
    val goalWaistMm: Int?,
    val targetDate: String?,
    val weighEvery: String,
    val waistEvery: String,
    val targetLowTenths: Int,
    val targetHighTenths: Int,
    val hypoTenths: Int,
)

@Entity(
    tableName = "scale_bands",
    foreignKeys = [ForeignKey(entity = PlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planId")],
)
data class ScaleBandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val position: Int,
    val upperTenths: Int?,
    val units: Int,
)

@Entity(
    tableName = "plan_slots",
    foreignKeys = [ForeignKey(entity = PlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planId")],
)
data class PlanSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val slot: String,
    val checkBgl: Boolean,
    val correctionPrescribed: Boolean,
    val minuteOfDay: Int,
    val reminderOn: Boolean,
)

/** One row per medicine and time of day. Rows with the same [medicineKey] are the same medicine. */
@Entity(
    tableName = "plan_medicines",
    foreignKeys = [ForeignKey(entity = PlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planId")],
)
data class PlanMedicineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val medicineKey: String,
    val position: Int,
    val name: String,
    val strength: String,
    val kind: String,
    val unit: String,
    val slot: String,
    val amount: Double,
)

data class PlanWithDetails(
    @Embedded val plan: PlanEntity,
    @Relation(parentColumn = "id", entityColumn = "planId") val bands: List<ScaleBandEntity>,
    @Relation(parentColumn = "id", entityColumn = "planId") val slots: List<PlanSlotEntity>,
    @Relation(parentColumn = "id", entityColumn = "planId") val medicines: List<PlanMedicineEntity>,
)

@Entity(tableName = "glucose_readings", indices = [Index("date")])
data class GlucoseReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val takenAt: Long,
    val date: String,
    val slot: String,
    val tenths: Int,
    val meterFlag: String?,
    val note: String?,
)

@Entity(tableName = "dose_log", indices = [Index("date"), Index("readingId")])
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val readingId: Long?,
    val takenAt: Long,
    val date: String,
    val slot: String,
    val kind: String,
    val medicineKey: String?,
    val medicineName: String,
    val medicineKind: String,
    val prescribedAmount: Double?,
    val givenAmount: Double?,
    val unit: String,
    val status: String,
    val reason: String?,
    val note: String?,
)

@Entity(tableName = "body_measurements", indices = [Index("date")])
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val takenAt: Long,
    val date: String,
    val weightGrams: Int?,
    val waistMm: Int?,
    val note: String?,
)
