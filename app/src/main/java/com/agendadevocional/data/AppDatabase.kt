package com.agendadevocional.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.agendadevocional.model.MensagemDia
import com.agendadevocional.model.TimelineNota
import com.agendadevocional.model.DataLeitura

@Database(entities = [MensagemDia::class, TimelineNota::class, DataLeitura::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mensagemDao(): MensagemDao
    abstract fun timelineNotaDao(): TimelineNotaDao
    abstract fun dataLeituraDao(): DataLeituraDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `timeline_notas` (
                        `data` TEXT NOT NULL, 
                        `hora` INTEGER NOT NULL, 
                        `texto` TEXT NOT NULL, 
                        PRIMARY KEY(`data`, `hora`)
                    )
                """)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `datas_leitura` (
                        `dataIso` TEXT NOT NULL, 
                        PRIMARY KEY(`dataIso`)
                    )
                """)
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `mensagens` ADD COLUMN `leituraReferencia` TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "agenda_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
