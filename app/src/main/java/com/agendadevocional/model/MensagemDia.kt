package com.agendadevocional.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mensagens")
data class MensagemDia(
    @PrimaryKey
    val data: String,
    val versiculo: String,
    val referencia: String,
    val contexto: String,
    val significado: String,
    val mensagem: String,
    val youtubeUrl: String? = null,
    val spotifyUrl: String? = null,
    val instagramUrl: String? = null,
    val facebookUrl: String? = null,
    val tiktokUrl: String? = null,
    val leituraReferencia: String? = null,
    val isFavorite: Boolean = false,
    val anotacao: String? = null,
    val audioPath: String? = null
)
