package com.agendadevocional.backup

import android.content.Context
import android.os.Build
import com.agendadevocional.data.AppDatabase
import com.agendadevocional.model.DataLeitura
import com.agendadevocional.model.TimelineNota
import org.json.JSONArray
import org.json.JSONObject

class BackupDataManager(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)

    suspend fun exportDataAsJson(): String {
        val backupJson = JSONObject()
        backupJson.put("backup_version", 1)
        backupJson.put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
        backupJson.put("timestamp", System.currentTimeMillis())

        // 1. SharedPreferences
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val settingsJson = JSONObject().apply {
            put("selected_language", prefs.getString("selected_language", "pt"))
            put("is_dark_mode", prefs.getBoolean("is_dark_mode", false))
            put("theme_style", prefs.getString("theme_style", "gold"))
            put("notifications_enabled", prefs.getBoolean("notifications_enabled", true))
            put("notification_hour", prefs.getInt("notification_hour", 7))
            put("notification_minute", prefs.getInt("notification_minute", 0))
            put("font_size_multiplier", prefs.getFloat("font_size_multiplier", 1.0f).toDouble())
        }
        backupJson.put("settings", settingsJson)

        // 2. Anotações pessoais e Favoritos das Mensagens do Dia
        val mensagens = db.mensagemDao().getAllMensagensList()
        val favoritesArray = JSONArray()
        val notesArray = JSONArray()

        for (m in mensagens) {
            if (m.isFavorite) {
                favoritesArray.put(m.data)
            }
            if (!m.anotacao.isNullOrBlank() || !m.audioPath.isNullOrBlank()) {
                val noteObj = JSONObject().apply {
                    put("data", m.data)
                    put("anotacao", m.anotacao ?: "")
                    put("audio_path", m.audioPath ?: "")
                }
                notesArray.put(noteObj)
            }
        }
        backupJson.put("favorites", favoritesArray)
        backupJson.put("notes", notesArray)

        // 3. Notas da Timeline
        val timelineNotas = db.timelineNotaDao().getAllNotas()
        val timelineArray = JSONArray()
        for (tn in timelineNotas) {
            val tnObj = JSONObject().apply {
                put("data", tn.data)
                put("hora", tn.hora)
                put("nota", tn.texto)
            }
            timelineArray.put(tnObj)
        }
        backupJson.put("timeline_notas", timelineArray)

        // 4. Histórico de Leitura (Assiduidade)
        val readDates = db.dataLeituraDao().getAllLeiturasList()
        val historyArray = JSONArray()
        for (rd in readDates) {
            historyArray.put(rd.dataIso)
        }
        backupJson.put("reading_history", historyArray)

        return backupJson.toString()
    }

    suspend fun importAndMergeData(jsonString: String): Boolean {
        try {
            val backupJson = JSONObject(jsonString)

            // 1. Restaurar configurações
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            if (backupJson.has("settings")) {
                val settings = backupJson.getJSONObject("settings")
                prefs.edit().apply {
                    putString("selected_language", settings.optString("selected_language", "pt"))
                    putBoolean("is_dark_mode", settings.optBoolean("is_dark_mode", false))
                    putString("theme_style", settings.optString("theme_style", "gold"))
                    putBoolean("notifications_enabled", settings.optBoolean("notifications_enabled", true))
                    putInt("notification_hour", settings.optInt("notification_hour", 7))
                    putInt("notification_minute", settings.optInt("notification_minute", 0))
                    putFloat("font_size_multiplier", settings.optDouble("font_size_multiplier", 1.0).toFloat())
                    apply()
                }
            }

            // 2. Mesclar favoritos e anotações na tabela mensagens
            val localMensagens = db.mensagemDao().getAllMensagensList().associateBy { it.data }

            // Ler favoritos do backup
            val favsSet = mutableSetOf<String>()
            if (backupJson.has("favorites")) {
                val favs = backupJson.getJSONArray("favorites")
                for (i in 0 until favs.length()) {
                    favsSet.add(favs.getString(i))
                }
            }

            // Ler anotações do backup
            val notesMap = mutableMapOf<String, Pair<String?, String?>>()
            if (backupJson.has("notes")) {
                val notes = backupJson.getJSONArray("notes")
                for (i in 0 until notes.length()) {
                    val noteObj = notes.getJSONObject(i)
                    val dataKey = noteObj.getString("data")
                    val anotacaoVal = noteObj.optString("anotacao", "").ifBlank { null }
                    val audioVal = noteObj.optString("audio_path", "").ifBlank { null }
                    notesMap[dataKey] = Pair(anotacaoVal, audioVal)
                }
            }

            // Mesclar nos registros locais
            val updatedMensagens = localMensagens.values.map { msg ->
                val backupFav = favsSet.contains(msg.data)
                val backupNote = notesMap[msg.data]

                val finalFav = msg.isFavorite || backupFav
                val finalAnotacao = if (msg.anotacao.isNullOrBlank()) backupNote?.first else msg.anotacao
                val finalAudio = if (msg.audioPath.isNullOrBlank()) backupNote?.second else msg.audioPath

                msg.copy(
                    isFavorite = finalFav,
                    anotacao = finalAnotacao,
                    audioPath = finalAudio
                )
            }
            if (updatedMensagens.isNotEmpty()) {
                db.mensagemDao().insertAll(updatedMensagens)
            }

            // 3. Mesclar notas da Timeline (timeline_notas)
            if (backupJson.has("timeline_notas")) {
                val timelineArray = backupJson.getJSONArray("timeline_notas")
                val localTimeline = db.timelineNotaDao().getAllNotas().associateBy { "${it.data}_${it.hora}" }
                val newTimelineList = mutableListOf<TimelineNota>()

                for (i in 0 until timelineArray.length()) {
                    val tnObj = timelineArray.getJSONObject(i)
                    val dataVal = tnObj.getString("data")
                    val horaVal = tnObj.getInt("hora")
                    val notaVal = tnObj.getString("nota")
                    val key = "${dataVal}_${horaVal}"

                    if (!localTimeline.containsKey(key)) {
                        newTimelineList.add(TimelineNota(data = dataVal, hora = horaVal, texto = notaVal))
                    }
                }
                if (newTimelineList.isNotEmpty()) {
                    db.timelineNotaDao().insertAll(newTimelineList)
                }
            }

            // 4. Mesclar Histórico de Leitura (datas_leitura)
            if (backupJson.has("reading_history")) {
                val historyArray = backupJson.getJSONArray("reading_history")
                val localHistory = db.dataLeituraDao().getAllLeiturasList().map { it.dataIso }.toSet()
                val newHistoryList = mutableListOf<DataLeitura>()

                for (i in 0 until historyArray.length()) {
                    val dateIso = historyArray.getString(i)
                    if (!localHistory.contains(dateIso)) {
                        newHistoryList.add(DataLeitura(dataIso = dateIso))
                    }
                }
                if (newHistoryList.isNotEmpty()) {
                    db.dataLeituraDao().insertAll(newHistoryList)
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    suspend fun importAndOverwriteData(jsonString: String): Boolean {
        try {
            val backupJson = JSONObject(jsonString)

            // 1. Restaurar configurações
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            if (backupJson.has("settings")) {
                val settings = backupJson.getJSONObject("settings")
                prefs.edit().apply {
                    putString("selected_language", settings.optString("selected_language", "pt"))
                    putBoolean("is_dark_mode", settings.optBoolean("is_dark_mode", false))
                    putString("theme_style", settings.optString("theme_style", "gold"))
                    putBoolean("notifications_enabled", settings.optBoolean("notifications_enabled", true))
                    putInt("notification_hour", settings.optInt("notification_hour", 7))
                    putInt("notification_minute", settings.optInt("notification_minute", 0))
                    putFloat("font_size_multiplier", settings.optDouble("font_size_multiplier", 1.0).toFloat())
                    apply()
                }
            }

            // 2. Limpar dados locais de mensagens primeiro (anotações e favoritos)
            db.mensagemDao().resetAllMensagens()

            val localMensagens = db.mensagemDao().getAllMensagensList().associateBy { it.data }

            val favsSet = mutableSetOf<String>()
            if (backupJson.has("favorites")) {
                val favs = backupJson.getJSONArray("favorites")
                for (i in 0 until favs.length()) {
                    favsSet.add(favs.getString(i))
                }
            }

            val notesMap = mutableMapOf<String, Pair<String?, String?>>()
            if (backupJson.has("notes")) {
                val notes = backupJson.getJSONArray("notes")
                for (i in 0 until notes.length()) {
                    val noteObj = notes.getJSONObject(i)
                    val dataKey = noteObj.getString("data")
                    val anotacaoVal = noteObj.optString("anotacao", "").ifBlank { null }
                    val audioVal = noteObj.optString("audio_path", "").ifBlank { null }
                    notesMap[dataKey] = Pair(anotacaoVal, audioVal)
                }
            }

            val updatedMensagens = localMensagens.values.map { msg ->
                val backupFav = favsSet.contains(msg.data)
                val backupNote = notesMap[msg.data]

                msg.copy(
                    isFavorite = backupFav,
                    anotacao = backupNote?.first,
                    audioPath = backupNote?.second
                )
            }
            if (updatedMensagens.isNotEmpty()) {
                db.mensagemDao().insertAll(updatedMensagens)
            }

            // 3. Limpar e restaurar notas da Timeline
            db.timelineNotaDao().deleteAll()
            if (backupJson.has("timeline_notas")) {
                val timelineArray = backupJson.getJSONArray("timeline_notas")
                val newTimelineList = mutableListOf<TimelineNota>()

                for (i in 0 until timelineArray.length()) {
                    val tnObj = timelineArray.getJSONObject(i)
                    val dataVal = tnObj.getString("data")
                    val horaVal = tnObj.getInt("hora")
                    val notaVal = tnObj.getString("nota")
                    newTimelineList.add(TimelineNota(data = dataVal, hora = horaVal, texto = notaVal))
                }
                if (newTimelineList.isNotEmpty()) {
                    db.timelineNotaDao().insertAll(newTimelineList)
                }
            }

            // 4. Limpar e restaurar Histórico de Leitura
            db.dataLeituraDao().deleteAll()
            if (backupJson.has("reading_history")) {
                val historyArray = backupJson.getJSONArray("reading_history")
                val newHistoryList = mutableListOf<DataLeitura>()

                for (i in 0 until historyArray.length()) {
                    val dateIso = historyArray.getString(i)
                    newHistoryList.add(DataLeitura(dataIso = dateIso))
                }
                if (newHistoryList.isNotEmpty()) {
                    db.dataLeituraDao().insertAll(newHistoryList)
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
