package com.agendadevocional.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "datas_leitura")
data class DataLeitura(
    @PrimaryKey
    val dataIso: String // formato "yyyy-MM-dd"
)
