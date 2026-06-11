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

@Database(entities = [MensagemDia::class, TimelineNota::class, DataLeitura::class], version = 5, exportSchema = false)
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "agenda_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
