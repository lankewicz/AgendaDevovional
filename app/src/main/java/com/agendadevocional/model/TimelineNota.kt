package com.agendadevocional.model

import androidx.room.Entity

@Entity(tableName = "timeline_notas", primaryKeys = ["data", "hora"])
data class TimelineNota(
    val data: String, // formato original, ex: "06 de Junho de 2026"
    val hora: Int,    // 0..23
    val texto: String
)
