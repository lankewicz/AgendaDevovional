package com.agendadevocional

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import java.util.Locale

class TextToSpeechHelper(
    context: Context,
    private val onInitResult: (Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            onInitResult(true)
        } else {
            isInitialized = false
            onInitResult(false)
        }
    }

    fun speak(
        text: String,
        language: String,
        onStart: () -> Unit,
        onDone: () -> Unit
    ): Int {
        if (!isInitialized || tts == null) return -1

        val locale = when (language) {
            "en" -> Locale.US
            "es" -> Locale.forLanguageTag("es-ES")
            else -> Locale.forLanguageTag("pt-BR")
        }

        val ttsEngine = tts ?: return -1
        val langResult = ttsEngine.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            return -2
        }

        ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            private val handler = Handler(Looper.getMainLooper())

            override fun onStart(utteranceId: String?) {
                handler.post { onStart() }
            }

            override fun onDone(utteranceId: String?) {
                handler.post { onDone() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post { onDone() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                handler.post { onDone() }
            }
        })

        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "devotional_tts_id")
        }
        val speakResult = ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, params, "devotional_tts_id")
        return if (speakResult == TextToSpeech.SUCCESS) 1 else -3
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
