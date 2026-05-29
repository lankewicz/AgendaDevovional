// Arquivo: MensagemDao.kt
// Finalidade: define as consultas Room usadas para carregar, inserir e favoritar mensagens devocionais.
package com.agendadevocional.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agendadevocional.model.MensagemDia
import kotlinx.coroutines.flow.Flow

@Dao
interface MensagemDao {
    @Query("SELECT * FROM mensagens ORDER BY rowid ASC")
    fun getAllMensagens(): Flow<List<MensagemDia>>

    @Query("SELECT * FROM mensagens ORDER BY rowid ASC")
    suspend fun getAllMensagensList(): List<MensagemDia>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mensagens: List<MensagemDia>)

    @Query("DELETE FROM mensagens")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM mensagens")
    suspend fun getCount(): Int

    @Query("UPDATE mensagens SET isFavorite = :isFavorite WHERE data = :data")
    suspend fun updateFavoriteStatus(data: String, isFavorite: Boolean)

    @Query("UPDATE mensagens SET anotacao = :anotacao WHERE data = :data")
    suspend fun updateAnotacao(data: String, anotacao: String?)

    @Query("UPDATE mensagens SET audioPath = :audioPath WHERE data = :data")
    suspend fun updateAudioPath(data: String, audioPath: String?)

    @Query("SELECT * FROM mensagens WHERE isFavorite = 1")
    fun getFavorites(): Flow<List<MensagemDia>>
}
