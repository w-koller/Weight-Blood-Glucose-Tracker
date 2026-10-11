package io.github.wkoller.glucosetracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlanEntity::class,
        ScaleBandEntity::class,
        PlanSlotEntity::class,
        PlanMedicineEntity::class,
        GlucoseReadingEntity::class,
        DoseLogEntity::class,
        BodyMeasurementEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun recordDao(): RecordDao

    companion object {
        const val NAME = "tracker.db"

        // Health records must survive upgrades, so there is deliberately no destructive migration fallback.
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
