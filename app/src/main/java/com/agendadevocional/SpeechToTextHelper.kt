package com.agendadevocional

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import java.util.concurrent.Executors

class SpeechToTextHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onReadyForSpeech: () -> Unit,
    private val onEndOfSpeech: () -> Unit,
    private val onPreparing: (String) -> Unit = {}
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val selectedLanguage: String
        get() {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            return prefs.getString("selected_language", "pt") ?: "pt"
        }

    private val recognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            handler.post { onReadyForSpeech() }
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            handler.post { onEndOfSpeech() }
        }

        override fun onError(error: Int) {
            val lang = selectedLanguage
            val key = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "erro_audio"
                SpeechRecognizer.ERROR_CLIENT -> "erro_cliente"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permissoes_insuficientes"
                SpeechRecognizer.ERROR_NETWORK -> "sem_conexao_rede"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "tempo_esgotado_rede"
                SpeechRecognizer.ERROR_NO_MATCH -> "nenhuma_fala_reconhecida"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "servico_reconhecimento_ocupado"
                SpeechRecognizer.ERROR_SERVER -> "erro_servidor_reconhecimento"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "nenhuma_fala_detectada"
                else -> "erro_desconhecido_reconhecimento"
            }
            val message = LocaleManager.getLocalizedString(lang, key)
            handler.post { onError(message) }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                handler.post { onResult(matches[0]) }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                handler.post { onResult(matches[0]) }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        val lang = selectedLanguage
        val langTag = when (lang) {
            "en" -> "en-US"
            "es" -> "es-ES"
            else -> "pt-BR"
        }
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
        
        handler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    val preparingMsg = LocaleManager.getLocalizedString(lang, "preparando_voz")
                    onPreparing(preparingMsg)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        checkAndStart(lang, langTag)
                    } else {
                        createAndStart()
                    }
                } else {
                    val msg = LocaleManager.getLocalizedString(lang, "reconhecimento_indisponivel")
                    onError(msg)
                }
            } catch (e: Exception) {
                val format = LocaleManager.getLocalizedString(lang, "falha_inicializar_gravador")
                onError(String.format(format, e.message ?: ""))
            }
        }
    }

    private fun checkAndStart(lang: String, langTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.checkRecognitionSupport(
                recognizerIntent,
                Executors.newSingleThreadExecutor(),
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        val isInstalled = recognitionSupport.installedOnDeviceLanguages.contains(langTag)
                        val isPending = recognitionSupport.pendingOnDeviceLanguages.contains(langTag)
                        recognizer.destroy()
                        handler.post {
                            if (isPending || !isInstalled) {
                                val downloadingMsg = LocaleManager.getLocalizedString(lang, "baixando_modelos")
                                onPreparing(downloadingMsg)
                                android.widget.Toast.makeText(context, downloadingMsg, android.widget.Toast.LENGTH_LONG).show()
                            }
                            createAndStart()
                        }
                    }

                    override fun onError(error: Int) {
                        recognizer.destroy()
                        handler.post { createAndStart() }
                    }
                }
            )
        }
    }

    private fun createAndStart() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
            startListening(recognizerIntent)
        }
    }

    fun stopListening() {
        handler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                speechRecognizer = null
            }
        }
    }
}
