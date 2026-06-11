package com.agendadevocional.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agendadevocional.model.TimelineNota
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineNotaDao {
    @Query("SELECT * FROM timeline_notas WHERE data = :data")
    fun getNotasDoDia(data: String): Flow<List<TimelineNota>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(nota: TimelineNota)

    @Query("DELETE FROM timeline_notas WHERE data = :data AND hora = :hora")
    suspend fun deleteNota(data: String, hora: Int)

    @Query("SELECT * FROM timeline_notas")
    suspend fun getAllNotas(): List<TimelineNota>

    @Query("SELECT * FROM timeline_notas")
    fun getAllTimelineNotasFlow(): Flow<List<TimelineNota>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notas: List<TimelineNota>)

    @Query("DELETE FROM timeline_notas")
    suspend fun deleteAll()
}
