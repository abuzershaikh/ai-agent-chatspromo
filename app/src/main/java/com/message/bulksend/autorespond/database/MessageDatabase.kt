package com.message.bulksend.autorespond.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.message.bulksend.autorespond.smartlead.LeadCaptureEntity
import com.message.bulksend.autorespond.smartlead.LeadCaptureSettingsEntity
import com.message.bulksend.autorespond.smartlead.LeadCaptureDao
import com.message.bulksend.autorespond.statusscheduled.models.StatusBatch
import com.message.bulksend.autorespond.statusscheduled.database.StatusBatchDao

/**
 * Room Database for message tracking and lead capture
 */
@Database(
    entities = [
        MessageEntity::class,
        LeadCaptureEntity::class,
        LeadCaptureSettingsEntity::class,
        Product::class,
        com.message.bulksend.autorespond.ai.data.model.DoctorEntity::class,
        com.message.bulksend.autorespond.ai.data.model.AppointmentEntity::class,
        StatusBatch::class,
        Catalogue::class,
        AttributeGroup::class,
        AttributeOption::class,
        ProductVariant::class
    ],
    version = 15,
    exportSchema = true
)
abstract class MessageDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun leadCaptureDao(): LeadCaptureDao
    abstract fun productDao(): ProductDao
    abstract fun catalogueDao(): CatalogueDao
    abstract fun clinicDao(): com.message.bulksend.autorespond.ai.data.dao.ClinicDao
    abstract fun statusBatchDao(): StatusBatchDao
    
    companion object {
        private const val TAG = "MessageDatabase"
        private const val DB_NAME = "message_tracking_database"

        @Volatile
        private var INSTANCE: MessageDatabase? = null

        private val MIGRATION_14_15 =
            object : Migration(14, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Deduplicate rows before adding unique constraints.
                    db.execSQL(
                        """
                        DELETE FROM attribute_options
                        WHERE id NOT IN (
                            SELECT MIN(id)
                            FROM attribute_options
                            GROUP BY groupId, value
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        DELETE FROM product_variants
                        WHERE id NOT IN (
                            SELECT MIN(id)
                            FROM product_variants
                            GROUP BY productId, optionIds
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_products_catalogueId_sortOrder` ON `products` (`catalogueId`, `sortOrder`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_products_catalogueId_isVisible` ON `products` (`catalogueId`, `isVisible`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_attribute_groups_productId_displayOrder` ON `attribute_groups` (`productId`, `displayOrder`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_attribute_options_groupId_displayOrder` ON `attribute_options` (`groupId`, `displayOrder`)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_attribute_options_groupId_value` ON `attribute_options` (`groupId`, `value`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_product_variants_productId_isAvailable` ON `product_variants` (`productId`, `isAvailable`)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_product_variants_productId_optionIds` ON `product_variants` (`productId`, `optionIds`)"
                    )
                }
            }
        
        fun getDatabase(context: Context): MessageDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                var instance = buildDatabase(appContext)

                try {
                    // Force open once so migration problems are handled here, not at random DAO call sites.
                    instance.openHelper.writableDatabase
                } catch (migrationError: IllegalStateException) {
                    Log.e(TAG, "Database migration failed. Recreating database.", migrationError)
                    instance.close()
                    appContext.deleteDatabase(DB_NAME)
                    instance = buildDatabase(appContext)
                    instance.openHelper.writableDatabase
                }

                INSTANCE = instance
                instance
            }
        }

        private fun buildDatabase(context: Context): MessageDatabase {
            return Room.databaseBuilder(
                context,
                MessageDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_14_15)
                // Avoid startup crash when user upgrades from very old schema versions
                // that do not have a defined migration path (for example 8 -> 15).
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
        }
    }
}
