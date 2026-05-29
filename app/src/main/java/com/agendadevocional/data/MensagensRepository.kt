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

class MensagensRepository(
    private val context: Context,
    private val mensagemDao: MensagemDao
) {

    fun parseToIsoDate(dataStr: String): String? {
        val clean = dataStr.trim().lowercase()
        try {
            if (clean.contains(" de ")) {
                val parts = clean.split(" de ")
                if (parts.size == 3) {
                    val day = parts[0].trim().toIntOrNull() ?: return null
                    val monthStr = parts[1].trim()
                    val year = parts[2].trim().toIntOrNull() ?: return null
                    
                    val month = when (monthStr) {
                        "janeiro", "enero" -> 1
                        "fevereiro", "febrero" -> 2
                        "março", "marco", "marzo" -> 3
                        "abril" -> 4
                        "maio", "mayo" -> 5
                        "junho", "junio" -> 6
                        "julho", "julio" -> 7
                        "agosto" -> 8
                        "setembro", "septiembre", "setiembre" -> 9
                        "outubro", "octubre" -> 10
                        "novembro", "noviembre" -> 11
                        "dezembro", "diciembre" -> 12
                        else -> return null
                    }
                    return String.format("%04d-%02d-%02d", year, month, day)
                }
            } else {
                val parts = clean.replace(",", "").split(Regex("\\s+"))
                if (parts.size == 3) {
                    val monthStr = parts[0]
                    val day = parts[1].toIntOrNull() ?: return null
                    val year = parts[2].toIntOrNull() ?: return null
                    
                    val month = when (monthStr) {
                        "january" -> 1
                        "february" -> 2
                        "march" -> 3
                        "april" -> 4
                        "may" -> 5
                        "june" -> 6
                        "july" -> 7
                        "august" -> 8
                        "september" -> 9
                        "october" -> 10
                        "november" -> 11
                        "december" -> 12
                        else -> return null
                    }
                    return String.format("%04d-%02d-%02d", year, month, day)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    suspend fun carregarMensagens(): List<MensagemDia> = withContext(Dispatchers.IO) {
        var lista = mensagemDao.getAllMensagensList()
        
        if (lista.isEmpty()) {
            lista = carregarMensagensDoJson()
            if (lista.isNotEmpty()) {
                mensagemDao.insertAll(lista)
            }
        }
        
        return@withContext lista
    }

    private fun carregarMensagensDoJson(): List<MensagemDia> {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("selected_language", "pt") ?: "pt"
        val assetName = when (lang) {
            "en" -> "agenda_en.json"
            "es" -> "agenda_es.json"
            else -> "agenda_pt.json"
        }

        val jsonText = try {
            val file = File(context.filesDir, "updated_agenda.json")
            if (file.exists()) {
                file.readText()
            } else {
                context.assets.open(assetName).bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            context.assets.open(assetName).bufferedReader().use { it.readText() }
        }

        return parseJsonText(jsonText)
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
            
            val file = File(context.filesDir, "updated_agenda.json")
            file.writeText(jsonText)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun setLanguageAndLoad(language: String, url: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            prefs.edit().putString("selected_language", language).apply()

            val assetName = when (language) {
                "en" -> "agenda_en.json"
                "es" -> "agenda_es.json"
                else -> "agenda_pt.json"
            }

            // Carrega mensagens atuais para mapear favoritos, anotações e áudios
            val mensagensLocais = mensagemDao.getAllMensagensList().associateBy { parseToIsoDate(it.data) }

            val jsonText: String
            val file = File(context.filesDir, "updated_agenda.json")

            if (!url.isNullOrBlank()) {
                jsonText = try {
                    URL(url).readText()
                } catch (e: Exception) {
                    context.assets.open(assetName).bufferedReader().use { it.readText() }
                }
                file.writeText(jsonText)
            } else {
                jsonText = context.assets.open(assetName).bufferedReader().use { it.readText() }
                if (file.exists()) {
                    file.delete()
                }
            }

            val jsonArray = JSONArray(jsonText)
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
}
