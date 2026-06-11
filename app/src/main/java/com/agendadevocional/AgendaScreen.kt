// Arquivo: AgendaScreen.kt
// Finalidade: tela principal da Agenda Devocional, com navegação diária, busca, favoritos, mídia, compartilhamento e configurações.
package com.agendadevocional

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import com.agendadevocional.ui.theme.AgendaTheme
import com.agendadevocional.worker.DevotionalWorker
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.MusicVideo
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agendadevocional.model.MensagemDia
import com.agendadevocional.model.TimelineNota
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import java.io.File
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.text.style.TextOverflow
import java.time.format.TextStyle

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    mensagens: List<MensagemDia>,
    indexHoje: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showOnlyFavorites: Boolean,
    onShowOnlyFavoritesChange: (Boolean) -> Unit,
    onSync: (String, (Boolean) -> Unit) -> Unit,
    onChangeLanguage: (String, String?, (Boolean) -> Unit) -> Unit,
    onToggleFavorite: (MensagemDia) -> Unit,
    onSaveAnotacao: (MensagemDia, String?) -> Unit,
    onSaveAudioPath: (MensagemDia, String?) -> Unit,
    timelineNotas: List<TimelineNota>,
    onSaveTimelineNota: (String, Int, String) -> Unit,
    onDeleteTimelineNota: (String, Int) -> Unit,
    readDates: Set<String>,
    onMarkAsRead: (String) -> Unit,
    onResetAllData: ((Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    LocaleManager.applicationContext = context.applicationContext
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    
    val ttsHelper = remember { TextToSpeechHelper(context) { } }
    var speakingData by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            ttsHelper.release()
        }
    }

    val selectedLanguage = remember(mensagens) { prefs.getString("selected_language", "pt") ?: "pt" }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showTtsDownloadDialog by remember { mutableStateOf(false) }

    var isSearchActive by remember { mutableStateOf(false) }
    
    var showAgendaTimeline by remember { mutableStateOf(false) }
    var timelineTargetMessage by remember { mutableStateOf<MensagemDia?>(null) }
    
    var fontSizeMultiplier by remember { 
        mutableStateOf(prefs.getFloat("font_size_multiplier", 1f)) 
    }
    
    val systemInDark = isSystemInDarkTheme()
    var isDarkMode by remember { 
        mutableStateOf(prefs.getBoolean("is_dark_mode", systemInDark)) 
    }
    var themeStyle by remember {
        mutableStateOf(prefs.getString("theme_style", "gold") ?: "gold")
    }

    var notificationsEnabled by remember {
        mutableStateOf(prefs.getBoolean("notifications_enabled", true))
    }
    var notificationHour by remember {
        mutableStateOf(prefs.getInt("notification_hour", 7))
    }
    var notificationMinute by remember {
        mutableStateOf(prefs.getInt("notification_minute", 0))
    }

    var showSettings by remember { mutableStateOf(false) }
    var showCopyrightDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val safeInitialPage = if (mensagens.isNotEmpty() && searchQuery.isEmpty()) {
        indexHoje.coerceIn(0, mensagens.lastIndex)
    } else {
        0
    }

    val pagerState = rememberPagerState(
        initialPage = safeInitialPage,
        pageCount = { mensagens.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val accessCount = remember { prefs.getInt("access_count", 0) }
    var showSwipeHint by remember { mutableStateOf(accessCount < 2) }

    val dateFormat = remember(selectedLanguage) {
        val pattern = when (selectedLanguage) {
            "en" -> "MMMM d, yyyy"
            "es" -> "d 'de' MMMM 'de' yyyy"
            else -> "dd 'de' MMMM 'de' yyyy"
        }
        val locale = when (selectedLanguage) {
            "en" -> Locale.US
            "es" -> Locale.forLanguageTag("es-ES")
            else -> Locale.forLanguageTag("pt-BR")
        }
        DateTimeFormatter.ofPattern(pattern, locale)
    }
    var pendingScrollIndex by remember { mutableStateOf<Int?>(null) }

    val audioRecorder = remember { AndroidAudioRecorder(context) }
    val audioPlayer = remember { AndroidAudioPlayer(context) }

    LaunchedEffect(Unit) {
        val todayStr = getTodayIso()
        onMarkAsRead(todayStr)
    }

    LaunchedEffect(pagerState.currentPage) {
        audioPlayer.stop()
        audioRecorder.stop()
    }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
            audioRecorder.stop()
        }
    }

    LaunchedEffect(mensagens.size) {
        if (mensagens.isNotEmpty() && pagerState.currentPage > mensagens.lastIndex) {
            pagerState.scrollToPage(mensagens.lastIndex)
        }
    }

    LaunchedEffect(pendingScrollIndex, mensagens.size) {
        val index = pendingScrollIndex
        if (index != null && mensagens.isNotEmpty()) {
            pagerState.animateScrollToPage(index.coerceIn(0, mensagens.lastIndex))
            pendingScrollIndex = null
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        ttsHelper.stop()
        speakingData = null
    }

    LaunchedEffect(Unit) {
        if (accessCount < 2) {
            prefs.edit().putInt("access_count", accessCount + 1).apply()
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedIso = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toString()

                        val index = mensagens.indexOfFirst {
                            parseToIsoDate(it.data) == selectedIso
                        }

                        if (index >= 0) {
                            onSearchQueryChange("")
                            isSearchActive = false
                            onShowOnlyFavoritesChange(false)
                            pendingScrollIndex = index
                        }
                    }
                    showDatePicker = false
                }) {
                    Text(getLocalizedString(selectedLanguage, "confirmar"), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(getLocalizedString(selectedLanguage, "cancelar"), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showLanguageDialog) {
        var tempSelectedLang by remember { mutableStateOf(selectedLanguage) }
        var isSwitchingLang by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSwitchingLang) showLanguageDialog = false },
            title = { Text(getLocalizedString(tempSelectedLang, "idioma"), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(getLocalizedString(tempSelectedLang, "idioma_desc"))
                    
                    listOf(
                        Triple("pt", "🇧🇷 Português", "Português (Brasil)"),
                        Triple("en", "🇺🇸 English", "English (US / UK)"),
                        Triple("es", "🇪🇸 Español", "Español (Latinoamérica / España)")
                    ).forEach { (code, label, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (tempSelectedLang == code) MaterialTheme.colorScheme.primaryContainer 
                                    else Color.Transparent
                               )
                                .clickable(enabled = !isSwitchingLang) { tempSelectedLang = code }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = tempSelectedLang == code,
                                onClick = { tempSelectedLang = code },
                                enabled = !isSwitchingLang
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }

                    if (isSwitchingLang) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(getLocalizedString(tempSelectedLang, "carregando_idioma"))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isSwitchingLang && tempSelectedLang != selectedLanguage,
                    onClick = {
                        val lang = tempSelectedLang
                        isSwitchingLang = true
                        val url = when (lang) {
                            "en" -> prefs.getString("sync_url_en", "https://raw.githubusercontent.com/usuario/repo/main/agenda_en.json")
                            "es" -> prefs.getString("sync_url_es", "https://raw.githubusercontent.com/usuario/repo/main/agenda_es.json")
                            "pt" -> prefs.getString("sync_url_pt", "https://raw.githubusercontent.com/usuario/repo/main/agenda.json")
                            else -> null
                        }
                        onChangeLanguage(lang, url) { success: Boolean ->
                            isSwitchingLang = false
                            if (success) {
                                showLanguageDialog = false
                                Toast.makeText(context, getLocalizedString(lang, "sucesso_idioma"), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, getLocalizedString(lang, "erro_idioma"), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text(getLocalizedString(tempSelectedLang, "confirmar"))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSwitchingLang,
                    onClick = { showLanguageDialog = false }
                ) {
                    Text(getLocalizedString(tempSelectedLang, "cancelar"))
                }
            }
        )
    }

    if (showTtsDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showTtsDownloadDialog = false },
            title = {
                Text(
                    text = getLocalizedString(selectedLanguage, "config_tts_gerenciar"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(getLocalizedString(selectedLanguage, "erro_tts_dados_ausentes"))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Erro ao abrir configurações de TTS", Toast.LENGTH_SHORT).show()
                        }
                        showTtsDownloadDialog = false
                    }
                ) {
                    Text(getLocalizedString(selectedLanguage, "abrir_configuracoes"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTtsDownloadDialog = false }) {
                    Text(getLocalizedString(selectedLanguage, "cancelar"))
                }
            }
        )
    }

    AgendaTheme(darkTheme = isDarkMode, themeStyle = themeStyle) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        // Subtle gradient glow at the top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp, bottom = 20.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Elegant Header
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

            if (isLandscape || isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { onSearchQueryChange(it) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            placeholder = { Text(getLocalizedString(selectedLanguage, "buscar")) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { 
                                    onSearchQueryChange("")
                                    isSearchActive = false 
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = getLocalizedString(selectedLanguage, "desc_fechar"))
                                }
                            }
                        )
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = getLocalizedString(selectedLanguage, "app_title"),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = FontFamily.Serif
                                ),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = getLocalizedString(selectedLanguage, "app_subtitle"),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable { showLanguageDialog = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    val flag = when (selectedLanguage) {
                                        "en" -> "🇺🇸"
                                        "es" -> "🇪🇸"
                                        else -> "🇧🇷"
                                    }
                                    val langLabel = when (selectedLanguage) {
                                        "en" -> "EN"
                                        "es" -> "ES"
                                        else -> "PT"
                                    }
                                    val bibleVersion = when (selectedLanguage) {
                                        "en" -> "WEB"
                                        "es" -> "RV1960"
                                        else -> "ARC"
                                    }
                                    Text(
                                        text = "$flag $langLabel | $bibleVersion",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!isSearchActive) {
                            IconButton(
                                onClick = { isSearchActive = true },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Search, contentDescription = getLocalizedString(selectedLanguage, "desc_buscar"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }


                            IconButton(
                                onClick = {
                                    onSearchQueryChange("")
                                    isSearchActive = false
                                    onShowOnlyFavoritesChange(false)
                                    pendingScrollIndex = indexHoje.coerceIn(0, mensagens.lastIndex)
                                },
                                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Home, contentDescription = getLocalizedString(selectedLanguage, "desc_hoje"), tint = MaterialTheme.colorScheme.primary)
                            }

                            IconButton(
                                onClick = {
                                    val youtubeUrl = "https://www.youtube.com/@bibliafaladaBR"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))
                                    intent.setPackage("com.google.android.youtube")
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl)))
                                    }
                                },
                                modifier = Modifier.background(Color(0xFFFF0000).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "YouTube", tint = Color(0xFFFF0000))
                            }

                            IconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = getLocalizedString(selectedLanguage, "desc_configuracoes"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            IconButton(
                                onClick = {
                                    val atual = mensagens.getOrNull(pagerState.currentPage)
                                    if (atual != null) {
                                        val (v, r, m) = getDisplayDevotional(atual, selectedLanguage)
                                        val textoCompartilhar = buildString {
                                            appendLine(atual.data)
                                            appendLine()
                                            appendLine(getLocalizedString(selectedLanguage, "compartilhar_versiculo"))
                                            appendLine("\"$v\" ($r)")
                                            appendLine()
                                            appendLine("${getLocalizedString(selectedLanguage, "compartilhar_msg")} $m")
                                            appendLine()
                                            append(getLocalizedString(selectedLanguage, "compartilhar_rodape"))
                                        }
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, textoCompartilhar)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, getLocalizedString(selectedLanguage, "compartilhar_titulo")))
                                    }
                                },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Share, contentDescription = getLocalizedString(selectedLanguage, "desc_compartilhar"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                // Portrait mode: Stack title/subtitle above the buttons row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = getLocalizedString(selectedLanguage, "app_title"),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = FontFamily.Serif
                                ),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = getLocalizedString(selectedLanguage, "app_subtitle"),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable { showLanguageDialog = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    val flag = when (selectedLanguage) {
                                        "en" -> "🇺🇸"
                                        "es" -> "🇪🇸"
                                        else -> "🇧🇷"
                                    }
                                    val langLabel = when (selectedLanguage) {
                                        "en" -> "EN"
                                        "es" -> "ES"
                                        else -> "PT"
                                    }
                                    val bibleVersion = when (selectedLanguage) {
                                        "en" -> "WEB"
                                        "es" -> "RV1960"
                                        else -> "ARC"
                                    }
                                    Text(
                                        text = "$flag $langLabel | $bibleVersion",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Search, contentDescription = getLocalizedString(selectedLanguage, "desc_buscar"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }


                        IconButton(
                            onClick = {
                                onSearchQueryChange("")
                                isSearchActive = false
                                onShowOnlyFavoritesChange(false)
                                pendingScrollIndex = indexHoje.coerceIn(0, mensagens.lastIndex)
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Home, contentDescription = getLocalizedString(selectedLanguage, "desc_hoje"), tint = MaterialTheme.colorScheme.primary)
                        }

                        IconButton(
                            onClick = {
                                val youtubeUrl = "https://www.youtube.com/@bibliafaladaBR"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))
                                intent.setPackage("com.google.android.youtube")
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl)))
                                }
                            },
                            modifier = Modifier.background(Color(0xFFFF0000).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "YouTube", tint = Color(0xFFFF0000))
                        }

                        IconButton(
                            onClick = { showSettings = true },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = getLocalizedString(selectedLanguage, "desc_configuracoes"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        IconButton(
                            onClick = {
                                val atual = mensagens.getOrNull(pagerState.currentPage)
                                if (atual != null) {
                                    val (v, r, m) = getDisplayDevotional(atual, selectedLanguage)
                                    val textoCompartilhar = buildString {
                                        appendLine(atual.data)
                                        appendLine()
                                        appendLine(getLocalizedString(selectedLanguage, "compartilhar_versiculo"))
                                        appendLine("\"$v\" ($r)")
                                        appendLine()
                                        appendLine("${getLocalizedString(selectedLanguage, "compartilhar_msg")} $m")
                                        appendLine()
                                        append(getLocalizedString(selectedLanguage, "compartilhar_rodape"))
                                    }
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, textoCompartilhar)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, getLocalizedString(selectedLanguage, "compartilhar_titulo")))
                                }
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = getLocalizedString(selectedLanguage, "desc_compartilhar"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Pager with elegant cards
            if (mensagens.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getLocalizedString(selectedLanguage, "nenhuma_mensagem"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                pageSpacing = 16.dp,
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) { page ->
                if (page < mensagens.size) {
                    val mensagemDia = mensagens[page]
                    val msgIso = parseToIsoDate(mensagemDia.data)
                    val isFuture = msgIso != null && msgIso > getTodayIso()
                    val (displayVerse, displayReferencia, displayMensagem) = getDisplayDevotional(mensagemDia, selectedLanguage)
                    val scrollState = rememberScrollState()

                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(24.dp)
                            ),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 20.dp)
                                .verticalScroll(scrollState)
                        ) {
                            // Date Tag & Media Icons
                            Box(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .border(
                                            0.5.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable { showDatePicker = true }
                                ) {
                                    Text(
                                        text = mensagemDia.data.uppercase(),
                                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                        letterSpacing = 1.5.sp
                                    )
                                }

                                Row(
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // TTS Read Aloud Toggle
                                    val isSpeaking = speakingData == mensagemDia.data
                                    IconButton(
                                        onClick = {
                                            if (isSpeaking) {
                                                ttsHelper.stop()
                                                speakingData = null
                                            } else {
                                                val (displayVerse, displayReferencia, displayMensagem) = getDisplayDevotional(mensagemDia, selectedLanguage)
                                                
                                                val regex = Regex("(\\d+)\\s*:\\s*(\\d+)(-\\d+)?")
                                                val matchResult = regex.find(displayReferencia)
                                                val speechReference = if (matchResult != null) {
                                                    val chapter = matchResult.groupValues[1]
                                                    val verse = matchResult.groupValues[2]
                                                    val range = matchResult.groupValues[3]
                                                    
                                                    val strCapitulo = getLocalizedString(selectedLanguage, "tts_capitulo")
                                                    val strVersiculo = getLocalizedString(selectedLanguage, "tts_versiculo")
                                                    val strAo = getLocalizedString(selectedLanguage, "tts_ao")
                                                    
                                                    val chapterText = "$strCapitulo $chapter"
                                                    var verseText = "$strVersiculo $verse"
                                                    if (!range.isNullOrEmpty()) {
                                                        val endVerse = range.substring(1)
                                                        verseText += " $strAo $endVerse"
                                                    }
                                                    displayReferencia.replace(regex, "$chapterText, $verseText")
                                                } else {
                                                    displayReferencia
                                                }

                                                val ttsText = when (selectedLanguage) {
                                                    "en" -> "Verse: $displayVerse. Reference: $speechReference. Message: $displayMensagem."
                                                    "es" -> "Versículo: $displayVerse. Referencia: $speechReference. Mensaje: $displayMensagem."
                                                    else -> "Versículo: $displayVerse. Referência: $speechReference. Mensagem: $displayMensagem."
                                                }
                                                speakingData = mensagemDia.data
                                                val result = ttsHelper.speak(
                                                    text = ttsText,
                                                    language = selectedLanguage,
                                                    onStart = { speakingData = mensagemDia.data },
                                                    onDone = { speakingData = null }
                                                )
                                                if (result != 1) {
                                                    speakingData = null
                                                    if (result == -4) {
                                                        showTtsDownloadDialog = true
                                                    } else {
                                                        val errorKey = when (result) {
                                                            -1 -> "erro_tts_inicializacao"
                                                            -2 -> "erro_tts_idioma"
                                                            else -> "erro_tts_geral"
                                                        }
                                                        Toast.makeText(context, getLocalizedString(selectedLanguage, errorKey), Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                if (isSpeaking) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (isSpeaking) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                            contentDescription = getLocalizedString(
                                                selectedLanguage, 
                                                if (isSpeaking) "desc_parar_ouvir" else "desc_ouvir"
                                            ),
                                            tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    // Favorite Toggle
                                    IconButton(
                                        onClick = { onToggleFavorite(mensagemDia) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                if (mensagemDia.isFavorite) 
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                else 
                                                    Color.Transparent,
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (mensagemDia.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = getLocalizedString(selectedLanguage, "desc_favoritar"),
                                            tint = if (mensagemDia.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    if (mensagemDia.youtubeUrl != null || mensagemDia.spotifyUrl != null || 
                                        mensagemDia.instagramUrl != null || mensagemDia.facebookUrl != null || 
                                        mensagemDia.tiktokUrl != null) {
                                        
                                        // YouTube
                                        mensagemDia.youtubeUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                            IconButton(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    intent.setPackage("com.google.android.youtube")
                                                    try { context.startActivity(intent) } catch (e: Exception) {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                    }
                                                },
                                                modifier = Modifier.size(36.dp).background(Color(0xFFFF0000).copy(alpha = 0.1f), CircleShape)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "YouTube", tint = Color(0xFFFF0000), modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        // Outros ícones de mídia simplificados para não poluir
                                        if (mensagemDia.spotifyUrl?.isNotBlank() == true || mensagemDia.instagramUrl?.isNotBlank() == true || 
                                            mensagemDia.facebookUrl?.isNotBlank() == true || mensagemDia.tiktokUrl?.isNotBlank() == true) {
                                             IconButton(
                                                onClick = { 
                                                    val url = listOfNotNull(
                                                        mensagemDia.spotifyUrl,
                                                        mensagemDia.instagramUrl,
                                                        mensagemDia.facebookUrl,
                                                        mensagemDia.tiktokUrl
                                                    ).firstOrNull { it.isNotBlank() }
                                                    url?.let {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                                                    }
                                                },
                                                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                            ) {
                                                 Icon(Icons.Default.MusicNote, contentDescription = getLocalizedString(selectedLanguage, "tab_mensagem_voz"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Highlighted Verse (Serif)
                            Text(
                                text = "“$displayVerse”",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = (MaterialTheme.typography.headlineSmall.fontSize.value * fontSizeMultiplier).sp,
                                    lineHeight = (36 * fontSizeMultiplier).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = displayReferencia,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Serif,
                                        fontSize = (MaterialTheme.typography.titleMedium.fontSize.value * fontSizeMultiplier).sp
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "©",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = (MaterialTheme.typography.bodySmall.fontSize.value * fontSizeMultiplier).sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { showCopyrightDialog = true }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 40.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Sections
                            if (!isFuture) {
                                SectionCard(
                                    title = "",
                                    content = mensagemDia.contexto,
                                    fontSizeMultiplier = fontSizeMultiplier
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                SectionCard(
                                    title = "",
                                    content = mensagemDia.significado,
                                    fontSizeMultiplier = fontSizeMultiplier
                                )

                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            if (displayMensagem.isNotEmpty()) {
                                SectionCard(
                                    title = if (isFuture) "" else getLocalizedString(selectedLanguage, "mensagem"),
                                    content = displayMensagem,
                                    highlighted = true,
                                    fontSizeMultiplier = fontSizeMultiplier
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            AnotacoesSection(
                                mensagemDia = mensagemDia,
                                onSaveAnotacao = onSaveAnotacao,
                                onSaveAudioPath = onSaveAudioPath,
                                audioRecorder = audioRecorder,
                                audioPlayer = audioPlayer,
                                fontSizeMultiplier = fontSizeMultiplier,
                                selectedLanguage = selectedLanguage,
                                onTimelineClick = {
                                    timelineTargetMessage = mensagemDia
                                    showAgendaTimeline = true
                                },
                                timelineOpened = showAgendaTimeline,
                                timelineNotas = timelineNotas
                            )
                        }
                    }
                }
            }
        }

            // Progress indicator (subtle)
            if (mensagens.isNotEmpty()) {
                val stats = remember(readDates) { getAssiduidadeStats(readDates) }
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${mensagens.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                    
                    if (stats.streak > 0) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "|",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "🔥 ${stats.streak} ${if (stats.streak == 1) getLocalizedString(selectedLanguage, "dia_seguido") else getLocalizedString(selectedLanguage, "dias_seguidos")}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFF8C00)
                        )
                    }
                }
            }
        }

        if (showCopyrightDialog) {
            AlertDialog(
                onDismissRequest = { showCopyrightDialog = false },
                title = {
                    Text(
                        text = getLocalizedString(selectedLanguage, "config_copyright_titulo"),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                },
                text = {
                    Text(
                        text = getLocalizedString(selectedLanguage, "config_copyright_desc"),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showCopyrightDialog = false }) {
                        Text(getLocalizedString(selectedLanguage, "confirmar"))
                    }
                }
            )
        }

        // Animated Swipe Hint
        if (showSwipeHint) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable { showSwipeHint = false },
                contentAlignment = Alignment.Center
            ) {
                 SwipeHintAnimation(selectedLanguage)
            }
        }

        // Settings Modal
        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                SettingsMenu(
                    isDarkMode = isDarkMode,
                    onDarkModeChange = { 
                        isDarkMode = it
                        prefs.edit().putBoolean("is_dark_mode", it).apply()
                    },
                    fontSizeMultiplier = fontSizeMultiplier,
                    onFontSizeChange = { 
                        fontSizeMultiplier = it
                        prefs.edit().putFloat("font_size_multiplier", it).apply()
                    },
                    notificationsEnabled = notificationsEnabled,
                    onNotificationsToggle = { enabled ->
                        notificationsEnabled = enabled
                        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
                        if (enabled) {
                            DevotionalWorker.scheduleDailyNotification(context, notificationHour, notificationMinute)
                        } else {
                            DevotionalWorker.cancelNotifications(context)
                        }
                    },
                    notificationHour = notificationHour,
                    notificationMinute = notificationMinute,
                    onTimeClick = {
                        TimePickerDialog(context, { _, h, m ->
                            notificationHour = h
                            notificationMinute = m
                            prefs.edit().putInt("notification_hour", h).putInt("notification_minute", m).apply()
                            if (notificationsEnabled) {
                                DevotionalWorker.scheduleDailyNotification(context, h, m)
                            }
                        }, notificationHour, notificationMinute, true).show()
                    },
                    onSync = onSync,
                    showOnlyFavorites = showOnlyFavorites,
                    onShowOnlyFavoritesChange = onShowOnlyFavoritesChange,
                    selectedLanguage = selectedLanguage,
                    onChangeLanguageClick = {
                        showSettings = false
                        showLanguageDialog = true
                    },
                    themeStyle = themeStyle,
                    onThemeStyleChange = {
                        themeStyle = it
                        prefs.edit().putString("theme_style", it).apply()
                    },
                    mensagens = mensagens,
                    timelineNotas = timelineNotas,
                    readDates = readDates,
                    onResetAllData = onResetAllData
                )
            }
        }
        if (showAgendaTimeline && timelineTargetMessage != null) {
            AgendaTimelineOverlay(
                mensagens = mensagens,
                initialPage = mensagens.indexOfFirst { it.data == timelineTargetMessage?.data }.coerceAtLeast(0),
                onPageChanged = { newPage ->
                    val nextMsg = mensagens.getOrNull(newPage)
                    if (nextMsg != null) {
                        timelineTargetMessage = nextMsg
                        coroutineScope.launch {
                            pagerState.scrollToPage(newPage)
                        }
                    }
                },
                onClose = { 
                    showAgendaTimeline = false
                },
                onSaveAnotacao = onSaveAnotacao,
                onSaveAudioPath = onSaveAudioPath,
                audioRecorder = audioRecorder,
                audioPlayer = audioPlayer,
                fontSizeMultiplier = fontSizeMultiplier,
                selectedLanguage = selectedLanguage,
                timelineNotas = timelineNotas,
                onSaveTimelineNota = onSaveTimelineNota,
                onDeleteTimelineNota = onDeleteTimelineNota
            )
        }
    }
}
}




@Composable
fun SwipeHintAnimation(selectedLanguage: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "swipe")
    
    // Animação Horizontal (Passar o dia)
    val offsetX by infiniteTransition.animateFloat(
        initialValue = 60f,
        targetValue = -60f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offsetX"
    )
    
    // Animação Vertical (Deslizar para cima)
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 40f,
        targetValue = -40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offsetY"
    )
    
    val opacity by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "opacity"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Deslizar para o lado (Mudar dia)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(150.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(80.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = offsetX.dp)
                            .graphicsLayer(alpha = opacity)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = getLocalizedString(selectedLanguage, "swipe_hint"),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Deslizar para cima (Ver conteúdo)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(150.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(80.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .offset(y = offsetY.dp)
                            .graphicsLayer(
                                alpha = opacity,
                                rotationZ = -90f
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = getLocalizedString(selectedLanguage, "swipe_up_hint"),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}



fun parseMonthStr(monthStr: String): Int? {
    val clean = monthStr.trim().lowercase()
    val languages = listOf("pt", "es", "en")
    for (lang in languages) {
        for (m in 1..12) {
            val monthsString = getLocalizedString(lang, "month_$m")
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

fun getLocalizedString(lang: String, key: String): String {
    return LocaleManager.getLocalizedString(lang, key)
}

fun getTodayIso(): String {
    val cal = java.util.Calendar.getInstance()
    val year = cal.get(java.util.Calendar.YEAR)
    val month = cal.get(java.util.Calendar.MONTH) + 1
    val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
    return String.format("%04d-%02d-%02d", year, month, day)
}

fun getTomorrowIso(): String {
    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.DATE, 1)
    val year = cal.get(java.util.Calendar.YEAR)
    val month = cal.get(java.util.Calendar.MONTH) + 1
    val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
    return String.format("%04d-%02d-%02d", year, month, day)
}

fun getDisplayDevotional(mensagemDia: MensagemDia, selectedLanguage: String): Triple<String, String, String> {
    val msgIso = parseToIsoDate(mensagemDia.data)
    val isFuture = msgIso != null && msgIso > getTodayIso()
    if (!isFuture) {
        return Triple(mensagemDia.versiculo, mensagemDia.referencia, mensagemDia.mensagem)
    }
    
    val stableHash = kotlin.math.abs(mensagemDia.data.hashCode())
    val idx = stableHash % 5
    val v = getLocalizedString(selectedLanguage, "future_verse_$idx")
    val r = getLocalizedString(selectedLanguage, "future_ref_$idx")
    
    val isTomorrow = msgIso == getTomorrowIso()
    val msg = if (isTomorrow) {
        getLocalizedString(selectedLanguage, "tomorrow_devotional_msg")
    } else {
        getLocalizedString(selectedLanguage, "future_devotional_msg")
    }
    return Triple(v, r, msg)
}

