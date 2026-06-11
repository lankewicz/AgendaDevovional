package com.agendadevocional.data

import android.content.Context
import com.agendadevocional.model.MensagemDia
import org.json.JSONArray
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow

class MensagensRepository(
    private val context: Context,
    private val mensagemDao: MensagemDao,
    private val timelineNotaDao: TimelineNotaDao,
    private val dataLeituraDao: DataLeituraDao
) {

    fun getFilteredMensagens(query: String?, onlyFavorites: Boolean): Flow<List<MensagemDia>> {
        val formattedQuery = if (query.isNullOrBlank()) null else "%$query%"
        return mensagemDao.getFilteredMensagens(formattedQuery, onlyFavorites)
    }

    private fun parseMonthStr(monthStr: String): Int? {
        val clean = monthStr.trim().lowercase()
        val languages = listOf("pt", "es", "en")
        for (lang in languages) {
            for (m in 1..12) {
                val monthsString = com.agendadevocional.LocaleManager.getLocalizedString(lang, "month_$m")
                val monthVariants = monthsString.split(",").map { it.trim().lowercase() }
                if (monthVariants.contains(clean)) {
                    return m
                }
            }
        }
        return null
    }

    fun parseToIsoDate(dataStr: String): String? {
        val clean = dataStr.trim().lowercase()
        try {
            if (clean.contains(" de ")) {
                val parts = clean.split(" de ")
                if (parts.size == 3) {
                    val day = parts[0].trim().toIntOrNull() ?: return null
                    val monthStr = parts[1].trim()
                    val year = parts[2].trim().toIntOrNull() ?: return null
                    
                    val month = parseMonthStr(monthStr) ?: return null
                    return String.format("%04d-%02d-%02d", year, month, day)
                }
            } else {
                val parts = clean.replace(",", "").split(Regex("\\s+"))
                if (parts.size == 3) {
                    val monthStr = parts[0]
                    val day = parts[1].toIntOrNull() ?: return null
                    val year = parts[2].toIntOrNull() ?: return null
                    
                    val month = parseMonthStr(monthStr) ?: return null
                    return String.format("%04d-%02d-%02d", year, month, day)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    suspend fun carregarMensagens(): List<MensagemDia> = withContext(Dispatchers.IO) {
        migrarSharedPreferencesParaRoom()
        migrarAssiduidadeParaRoom()
        return@withContext mensagemDao.getAllMensagensList()
    }

    private fun parseJsonText(jsonText: String): List<MensagemDia> {
        val jsonArray = JSONArray(jsonText)
        val lista = mutableListOf<MensagemDia>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            lista += MensagemDia(
                data = obj.getString("data"),
                versiculo = obj.getString("versiculo"),
                referencia = obj.getString("referencia"),
                contexto = obj.getString("contexto"),
                significado = obj.getString("significado"),
                mensagem = obj.getString("mensagem"),
                youtubeUrl = if (obj.has("youtubeUrl")) obj.getString("youtubeUrl") else null,
                spotifyUrl = if (obj.has("spotifyUrl")) obj.getString("spotifyUrl") else null,
                instagramUrl = if (obj.has("instagramUrl")) obj.getString("instagramUrl") else null,
                facebookUrl = if (obj.has("facebookUrl")) obj.getString("facebookUrl") else null,
                tiktokUrl = if (obj.has("tiktokUrl")) obj.getString("tiktokUrl") else null
            )
        }
        return lista
    }

    suspend fun syncMensagens(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonText = URL(url).readText()
            // Validar se é um JSON válido
            val jsonArray = JSONArray(jsonText)
            
            // Obter mensagens locais atuais mapeadas por data ISO
            val mensagensLocais = mensagemDao.getAllMensagensList().associateBy { parseToIsoDate(it.data) }
            
            val novasMensagens = mutableListOf<MensagemDia>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val dataKey = obj.getString("data")
                val dataIso = parseToIsoDate(dataKey)
                val localMsg = mensagensLocais[dataIso]
                
                novasMensagens += MensagemDia(
                    data = dataKey,
                    versiculo = obj.getString("versiculo"),
                    referencia = obj.getString("referencia"),
                    contexto = obj.getString("contexto"),
                    significado = obj.getString("significado"),
                    mensagem = obj.getString("mensagem"),
                    youtubeUrl = if (obj.has("youtubeUrl")) obj.getString("youtubeUrl") else null,
                    spotifyUrl = if (obj.has("spotifyUrl")) obj.getString("spotifyUrl") else null,
                    instagramUrl = if (obj.has("instagramUrl")) obj.getString("instagramUrl") else null,
                    facebookUrl = if (obj.has("facebookUrl")) obj.getString("facebookUrl") else null,
                    tiktokUrl = if (obj.has("tiktokUrl")) obj.getString("tiktokUrl") else null,
                    isFavorite = localMsg?.isFavorite ?: false,
                    anotacao = localMsg?.anotacao,
                    audioPath = localMsg?.audioPath
                )
            }
            
            if (novasMensagens.isNotEmpty()) {
                mensagemDao.deleteAll()
                mensagemDao.insertAll(novasMensagens)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun setLanguageAndLoad(language: String, url: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            if (url.isNullOrBlank()) return@withContext false

            // 1. Baixar os dados primeiro (ponto com maior chance de falha/erro de rede)
            val jsonText = URL(url).readText()
            val jsonArray = JSONArray(jsonText)

            // 2. Carrega mensagens atuais para mapear favoritos, anotações e áudios
            val mensagensLocais = mensagemDao.getAllMensagensList().associateBy { parseToIsoDate(it.data) }
            val novasMensagens = mutableListOf<MensagemDia>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val dataKey = obj.getString("data")
                val dataIso = parseToIsoDate(dataKey)
                val localMsg = mensagensLocais[dataIso]

                novasMensagens += MensagemDia(
                    data = dataKey,
                    versiculo = obj.getString("versiculo"),
                    referencia = obj.getString("referencia"),
                    contexto = obj.getString("contexto"),
                    significado = obj.getString("significado"),
                    mensagem = obj.getString("mensagem"),
                    youtubeUrl = if (obj.has("youtubeUrl")) obj.getString("youtubeUrl") else null,
                    spotifyUrl = if (obj.has("spotifyUrl")) obj.getString("spotifyUrl") else null,
                    instagramUrl = if (obj.has("instagramUrl")) obj.getString("instagramUrl") else null,
                    facebookUrl = if (obj.has("facebookUrl")) obj.getString("facebookUrl") else null,
                    tiktokUrl = if (obj.has("tiktokUrl")) obj.getString("tiktokUrl") else null,
                    isFavorite = localMsg?.isFavorite ?: false,
                    anotacao = localMsg?.anotacao,
                    audioPath = localMsg?.audioPath
                )
            }

            // 3. Se tudo foi processado e baixado sem erros, agora sim salvamos no banco e nas configurações
            if (novasMensagens.isNotEmpty()) {
                val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                prefs.edit().putString("selected_language", language).apply()

                mensagemDao.deleteAll()
                mensagemDao.insertAll(novasMensagens)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun indiceHoje(mensagens: List<MensagemDia>): Int {
        if (mensagens.isEmpty()) return 0
        val hojeIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val idx = mensagens.indexOfFirst {
            parseToIsoDate(it.data) == hojeIso
        }
        return if (idx >= 0) idx else 0
    }

    suspend fun toggleFavorite(mensagem: MensagemDia) = withContext(Dispatchers.IO) {
        mensagemDao.updateFavoriteStatus(mensagem.data, !mensagem.isFavorite)
    }

    suspend fun salvarAnotacao(data: String, anotacao: String?) = withContext(Dispatchers.IO) {
        mensagemDao.updateAnotacao(data, anotacao)
    }

    suspend fun salvarAudioPath(data: String, audioPath: String?) = withContext(Dispatchers.IO) {
        mensagemDao.updateAudioPath(data, audioPath)
    }

    suspend fun limparAudiosOrfaos() = withContext(Dispatchers.IO) {
        try {
            val mensagens = mensagemDao.getAllMensagensList()
            val audiosAtivos = mensagens.mapNotNull { it.audioPath }.toSet()
            
            val filesDir = context.filesDir
            val arquivosNoDisco = filesDir.listFiles { _, name ->
                name.startsWith("audio_reflection_") && name.endsWith(".m4a")
            } ?: emptyArray()
            
            for (arquivo in arquivosNoDisco) {
                val path = arquivo.absolutePath
                if (path !in audiosAtivos) {
                    arquivo.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getNotasDoDia(data: String): Flow<List<com.agendadevocional.model.TimelineNota>> {
        val dataIso = parseToIsoDate(data) ?: data
        return timelineNotaDao.getNotasDoDia(dataIso)
    }

    fun getAllTimelineNotasFlow(): Flow<List<com.agendadevocional.model.TimelineNota>> {
        return timelineNotaDao.getAllTimelineNotasFlow()
    }

    suspend fun salvarTimelineNota(data: String, hora: Int, texto: String) = withContext(Dispatchers.IO) {
        val dataIso = parseToIsoDate(data) ?: data
        timelineNotaDao.insertOrUpdate(com.agendadevocional.model.TimelineNota(dataIso, hora, texto))
    }

    suspend fun excluirTimelineNota(data: String, hora: Int) = withContext(Dispatchers.IO) {
        val dataIso = parseToIsoDate(data) ?: data
        timelineNotaDao.deleteNota(dataIso, hora)
    }

    private suspend fun migrarSharedPreferencesParaRoom() {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val migrated = prefs.getBoolean("timeline_notes_migrated_v4", false)
        if (!migrated) {
            try {
                val allEntries = prefs.all
                val notesToInsert = mutableListOf<com.agendadevocional.model.TimelineNota>()
                val keysToRemove = mutableListOf<String>()

                for ((key, value) in allEntries) {
                    if (key.startsWith("note_") && value is String && value.isNotEmpty()) {
                        val parts = key.split("_")
                        if (parts.size >= 3) {
                            val hourStr = parts.last()
                            val hour = hourStr.toIntOrNull()
                            if (hour != null) {
                                val data = parts.subList(1, parts.size - 1).joinToString("_")
                                val dataIso = parseToIsoDate(data) ?: data
                                notesToInsert.add(com.agendadevocional.model.TimelineNota(dataIso, hour, value))
                                keysToRemove.add(key)
                            }
                        }
                    }
                }

                if (notesToInsert.isNotEmpty()) {
                    timelineNotaDao.insertAll(notesToInsert)
                }

                if (keysToRemove.isNotEmpty()) {
                    val editor = prefs.edit()
                    keysToRemove.forEach { editor.remove(it) }
                    editor.apply()
                }

                prefs.edit().putBoolean("timeline_notes_migrated_v4", true).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getAllLeiturasFlow(): Flow<List<com.agendadevocional.model.DataLeitura>> {
        return dataLeituraDao.getAllLeiturasFlow()
    }

    suspend fun marcarComoLido(dataIso: String) = withContext(Dispatchers.IO) {
        dataLeituraDao.marcarComoLido(com.agendadevocional.model.DataLeitura(dataIso))
    }

    private suspend fun migrarAssiduidadeParaRoom() {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val migrated = prefs.getBoolean("read_dates_migrated_v5", false)
        if (!migrated) {
            try {
                val readDates = prefs.getStringSet("read_dates", emptySet()) ?: emptySet()
                if (readDates.isNotEmpty()) {
                    val listToInsert = readDates.map { com.agendadevocional.model.DataLeitura(it) }
                    dataLeituraDao.insertAll(listToInsert)
                }
                prefs.edit().remove("read_dates").putBoolean("read_dates_migrated_v5", true).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun resetAllData() = withContext(Dispatchers.IO) {
        timelineNotaDao.deleteAll()
        dataLeituraDao.deleteAll()
        mensagemDao.resetAllMensagens()

        // physically delete all recorded audio files (.m4a) in filesDir
        try {
            val filesDir = context.filesDir
            val arquivosNoDisco = filesDir.listFiles { _, name ->
                name.startsWith("audio_reflection_") && name.endsWith(".m4a")
            } ?: emptyArray()
            for (arquivo in arquivosNoDisco) {
                arquivo.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // remove legacy read_dates keys in shared preferences if any exist
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().remove("read_dates").apply()
    }
}

