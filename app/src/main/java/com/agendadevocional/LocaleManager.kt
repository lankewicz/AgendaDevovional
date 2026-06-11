package com.agendadevocional

import android.content.Context
import org.json.JSONObject
import java.lang.ref.WeakReference

object LocaleManager {
    private var contextRef: WeakReference<Context>? = null

    var applicationContext: Context?
        get() = contextRef?.get()
        set(value) {
            contextRef = value?.let { WeakReference(it.applicationContext) }
        }

    private var currentLang: String? = null
    private var stringsMap: Map<String, String> = emptyMap()

    @Synchronized
    fun getLocalizedString(lang: String, key: String): String {
        val context = applicationContext ?: return key
        if (currentLang != lang || stringsMap.isEmpty()) {
            val fileName = when (lang) {
                "en" -> "strings_en.json"
                "es" -> "strings_es.json"
                else -> "strings_pt.json"
            }
            try {
                val jsonString = context.assets.open(fileName).use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }
                val jsonObject = JSONObject(jsonString)
                val newMap = mutableMapOf<String, String>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    newMap[k] = jsonObject.optString(k, k)
                }
                stringsMap = newMap
                currentLang = lang
            } catch (e: Exception) {
                e.printStackTrace()
                return key
            }
        }
        return stringsMap[key] ?: key
    }
}

