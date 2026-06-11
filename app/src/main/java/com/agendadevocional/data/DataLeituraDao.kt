package com.agendadevocional.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agendadevocional.model.DataLeitura
import kotlinx.coroutines.flow.Flow

@Dao
interface DataLeituraDao {
    @Query("SELECT * FROM datas_leitura")
    fun getAllLeiturasFlow(): Flow<List<DataLeitura>>

    @Query("SELECT * FROM datas_leitura")
    suspend fun getAllLeiturasList(): List<DataLeitura>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun marcarComoLido(dataLeitura: DataLeitura)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(datas: List<DataLeitura>)

    @Query("DELETE FROM datas_leitura")
    suspend fun deleteAll()
}
