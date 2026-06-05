// Arquivo: MainActivity.kt
// Finalidade: ponto de entrada do app Agenda Devocional, inicializando tema, permissões, dados, seleção de idioma e tela principal.
package com.agendadevocional

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.agendadevocional.data.MensagensRepository
import com.agendadevocional.ui.theme.AgendaTheme
import com.agendadevocional.worker.DevotionalWorker
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

import androidx.activity.viewModels
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar URLs de sincronização padrão se necessário
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (!prefs.contains("sync_url_pt")) {
            prefs.edit()
                .putString("sync_url_pt", "https://raw.githubusercontent.com/usuario/repo/main/agenda.json")
                .putString("sync_url_en", "https://raw.githubusercontent.com/usuario/repo/main/agenda_en.json")
                .putString("sync_url_es", "https://raw.githubusercontent.com/usuario/repo/main/agenda_es.json")
                .apply()
        }

        // Agendar notificações diárias
        DevotionalWorker.scheduleDailyNotification(this)
        
        // Solicitar permissão de notificação (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != 
                PackageManager.PERMISSION_GRANTED) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val database = com.agendadevocional.data.AppDatabase.getDatabase(this)
        val repo = MensagensRepository(this, database.mensagemDao())
        val viewModel: AgendaViewModel by viewModels { AgendaViewModelFactory(repo) }

        setContent {
            val context = LocalContext.current
            val currentPrefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
            val systemInDark = isSystemInDarkTheme()
            val isDarkMode = currentPrefs.getBoolean("is_dark_mode", systemInDark)
            val themeStyle = currentPrefs.getString("theme_style", "gold") ?: "gold"

            AgendaTheme(darkTheme = isDarkMode, themeStyle = themeStyle) {
                LocaleManager.applicationContext = context.applicationContext
                var showSplash by remember { mutableStateOf(true) }
                val state by viewModel.uiState.collectAsState()
                
                var selectedLanguage by remember { mutableStateOf(currentPrefs.getString("selected_language", null)) }
                var isDownloading by remember { mutableStateOf(false) }

                LaunchedEffect(selectedLanguage) {
                    selectedLanguage?.let { lang ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            try {
                                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                                    val langTag = when (lang) {
                                        "en" -> "en-US"
                                        "es" -> "es-ES"
                                        else -> "pt-BR"
                                    }
                                    val triggerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                                    }
                                    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                                    recognizer.triggerModelDownload(triggerIntent)
                                    recognizer.destroy()
                                }
                            } catch (e: Throwable) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

                if (showSplash) {
                    SplashScreen(onSplashFinished = { showSplash = false })
                } else if (selectedLanguage == null) {
                    LanguageSelectionScreen(
                        isDownloading = isDownloading,
                        onLanguageSelected = { lang ->
                            isDownloading = true
                            val url = when (lang) {
                                "en" -> currentPrefs.getString("sync_url_en", "https://raw.githubusercontent.com/usuario/repo/main/agenda_en.json")
                                "es" -> currentPrefs.getString("sync_url_es", "https://raw.githubusercontent.com/usuario/repo/main/agenda_es.json")
                                "pt" -> currentPrefs.getString("sync_url_pt", "https://raw.githubusercontent.com/usuario/repo/main/agenda.json")
                                else -> null
                            }
                            viewModel.changeLanguage(lang, url) { success ->
                                isDownloading = false
                                if (success) {
                                    selectedLanguage = lang
                                    showSplash = true
                                } else {
                                    val errMsg = LocaleManager.getLocalizedString(lang, "erro_idioma")
                                    Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (val s = state) {
                            is AgendaState.Loading -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            is AgendaState.Error -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    val lang = selectedLanguage ?: "pt"
                                    val errFormat = LocaleManager.getLocalizedString(lang, "erro_prefix")
                                    val errorMsg = if (s.message == "Erro desconhecido ao carregar mensagens") {
                                        LocaleManager.getLocalizedString(lang, "erro_carregar_mensagens")
                                    } else {
                                        s.message
                                    }
                                    Text(String.format(errFormat, errorMsg), color = MaterialTheme.colorScheme.error)
                                }
                            }
                            is AgendaState.Success -> {
                                if (s.mensagens.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        val lang = selectedLanguage ?: "pt"
                                        val noMsg = LocaleManager.getLocalizedString(lang, "nenhuma_mensagem")
                                        Text(
                                            noMsg, 
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                } else {
                                    AgendaScreen(
                                        mensagens = s.mensagens,
                                        indexHoje = s.indexHoje,
                                        onSync = { url, callback -> viewModel.syncData(url, callback) },
                                        onChangeLanguage = { lang, url, callback -> 
                                            viewModel.changeLanguage(lang, url) { success ->
                                                if (success) {
                                                    selectedLanguage = lang
                                                    showSplash = true
                                                }
                                                callback(success)
                                            }
                                        },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onSaveAnotacao = { msg, text -> viewModel.salvarAnotacao(msg, text) },
                                        onSaveAudioPath = { msg, path -> viewModel.salvarAudioPath(msg, path) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(1)
    }
}
