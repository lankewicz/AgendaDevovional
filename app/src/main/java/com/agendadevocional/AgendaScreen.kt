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
    onSync: (String, (Boolean) -> Unit) -> Unit,
    onChangeLanguage: (String, String?, (Boolean) -> Unit) -> Unit,
    onToggleFavorite: (MensagemDia) -> Unit,
    onSaveAnotacao: (MensagemDia, String?) -> Unit,
    onSaveAudioPath: (MensagemDia, String?) -> Unit
) {
    val context = LocalContext.current
    LocaleManager.applicationContext = context.applicationContext
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    
    val selectedLanguage = remember(mensagens) { prefs.getString("selected_language", "pt") ?: "pt" }
    var showLanguageDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showOnlyFavorites by remember { mutableStateOf(false) }
    
    var showAgendaTimeline by remember { mutableStateOf(false) }
    var timelineTargetMessage by remember { mutableStateOf<MensagemDia?>(null) }
    
    var fontSizeMultiplier by remember { 
        mutableStateOf(prefs.getFloat("font_size_multiplier", 1f)) 
    }
    
    val systemInDark = isSystemInDarkTheme()
    var isDarkMode by remember { 
        mutableStateOf(prefs.getBoolean("is_dark_mode", systemInDark)) 
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
    val sheetState = rememberModalBottomSheetState()

    val filteredMensagens = remember(searchQuery, showOnlyFavorites, mensagens) {
        val results = if (searchQuery.isEmpty()) {
            mensagens
        } else {
            mensagens.filter { 
                it.versiculo.contains(searchQuery, ignoreCase = true) ||
                it.mensagem.contains(searchQuery, ignoreCase = true) ||
                it.referencia.contains(searchQuery, ignoreCase = true) ||
                it.data.contains(searchQuery, ignoreCase = true)
            }
        }
        
        if (showOnlyFavorites) {
            results.filter { it.isFavorite }
        } else {
            results
        }
    }

    val safeInitialPage = if (filteredMensagens.isNotEmpty() && searchQuery.isEmpty()) {
        indexHoje.coerceIn(0, filteredMensagens.lastIndex)
    } else {
        0
    }

    val pagerState = rememberPagerState(
        initialPage = safeInitialPage,
        pageCount = { filteredMensagens.size }
    )
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
        val todayStr = LocalDate.now().toString()
        markDateAsRead(context, todayStr)
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

    LaunchedEffect(filteredMensagens.size) {
        if (filteredMensagens.isNotEmpty() && pagerState.currentPage > filteredMensagens.lastIndex) {
            pagerState.scrollToPage(filteredMensagens.lastIndex)
        }
    }

    LaunchedEffect(pendingScrollIndex, filteredMensagens.size) {
        val index = pendingScrollIndex
        if (index != null && filteredMensagens.isNotEmpty()) {
            pagerState.animateScrollToPage(index.coerceIn(0, filteredMensagens.lastIndex))
            pendingScrollIndex = null
        }
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
                            searchQuery = ""
                            isSearchActive = false
                            showOnlyFavorites = false
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

    AgendaTheme(darkTheme = isDarkMode) {
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
                            onValueChange = { searchQuery = it },
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
                                    searchQuery = ""
                                    isSearchActive = false 
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Fechar")
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
                                Icon(Icons.Default.Search, contentDescription = "Buscar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            IconButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = "Data", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    isSearchActive = false
                                    showOnlyFavorites = false
                                    pendingScrollIndex = indexHoje.coerceIn(0, mensagens.lastIndex)
                                },
                                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Home, contentDescription = "Hoje", tint = MaterialTheme.colorScheme.primary)
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
                                Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            IconButton(
                                onClick = {
                                    val atual = filteredMensagens.getOrNull(pagerState.currentPage)
                                    if (atual != null) {
                                        val textoCompartilhar = buildString {
                                            appendLine(atual.data)
                                            appendLine()
                                            appendLine(getLocalizedString(selectedLanguage, "compartilhar_versiculo"))
                                            appendLine("\"${atual.versiculo}\" (${atual.referencia})")
                                            appendLine()
                                            appendLine("${getLocalizedString(selectedLanguage, "compartilhar_msg")} ${atual.mensagem}")
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
                                Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Icon(Icons.Default.Search, contentDescription = "Buscar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        IconButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = "Data", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        IconButton(
                            onClick = {
                                searchQuery = ""
                                isSearchActive = false
                                showOnlyFavorites = false
                                pendingScrollIndex = indexHoje.coerceIn(0, mensagens.lastIndex)
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Home, contentDescription = "Hoje", tint = MaterialTheme.colorScheme.primary)
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
                            Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        IconButton(
                            onClick = {
                                val atual = filteredMensagens.getOrNull(pagerState.currentPage)
                                if (atual != null) {
                                    val textoCompartilhar = buildString {
                                        appendLine(atual.data)
                                        appendLine()
                                        appendLine(getLocalizedString(selectedLanguage, "compartilhar_versiculo"))
                                        appendLine("\"${atual.versiculo}\" (${atual.referencia})")
                                        appendLine()
                                        appendLine("${getLocalizedString(selectedLanguage, "compartilhar_msg")} ${atual.mensagem}")
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
                            Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Pager with elegant cards
            if (filteredMensagens.isEmpty()) {
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
                if (page < filteredMensagens.size) {
                    val mensagemDia = filteredMensagens[page]
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
                                .padding(28.dp)
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
                                            contentDescription = "Favoritar",
                                            tint = if (mensagemDia.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
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
                                                Icon(Icons.Default.MusicNote, contentDescription = "Mídia", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // Highlighted Verse (Serif)
                            Text(
                                text = "“${mensagemDia.versiculo}”",
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

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = mensagemDia.referencia,
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

                            Spacer(modifier = Modifier.height(32.dp))

                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 40.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            // Sections
                            SectionCard(
                                title = getLocalizedString(selectedLanguage, "contexto"),
                                content = mensagemDia.contexto,
                                fontSizeMultiplier = fontSizeMultiplier
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            SectionCard(
                                title = getLocalizedString(selectedLanguage, "significado"),
                                content = mensagemDia.significado,
                                fontSizeMultiplier = fontSizeMultiplier
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            SectionCard(
                                title = getLocalizedString(selectedLanguage, "mensagem"),
                                content = mensagemDia.mensagem,
                                highlighted = true,
                                fontSizeMultiplier = fontSizeMultiplier
                            )

                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(24.dp))

                            AnotacoesPreviewCard(
                                mensagemDia = mensagemDia,
                                onClick = {
                                    timelineTargetMessage = mensagemDia
                                    showAgendaTimeline = true
                                },
                                fontSizeMultiplier = fontSizeMultiplier,
                                selectedLanguage = selectedLanguage
                            )
                        }
                    }
                }
            }
        }

            // Progress indicator (subtle)
            if (filteredMensagens.isNotEmpty()) {
                val stats = remember { getAssiduidadeStats(context) }
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${filteredMensagens.size}",
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
                    onShowOnlyFavoritesChange = { showOnlyFavorites = it },
                    selectedLanguage = selectedLanguage,
                    onChangeLanguageClick = {
                        showSettings = false
                        showLanguageDialog = true
                    }
                )
            }
        }
        if (showAgendaTimeline && timelineTargetMessage != null) {
            AgendaTimelineOverlay(
                mensagemDia = timelineTargetMessage!!,
                onClose = { showAgendaTimeline = false },
                onSaveAnotacao = onSaveAnotacao,
                onSaveAudioPath = onSaveAudioPath,
                audioRecorder = audioRecorder,
                audioPlayer = audioPlayer,
                fontSizeMultiplier = fontSizeMultiplier,
                selectedLanguage = selectedLanguage
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

fun markDateAsRead(context: Context, isoDate: String) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val readDates = prefs.getStringSet("read_dates", emptySet())?.toMutableSet() ?: mutableSetOf()
    if (readDates.add(isoDate)) {
        prefs.edit().putStringSet("read_dates", readDates).apply()
    }
}



fun getLocalizedString(lang: String, key: String): String {
    return LocaleManager.getLocalizedString(lang, key)
}

object LocaleManager {
    var applicationContext: Context? = null
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
                val jsonObject = org.json.JSONObject(jsonString)
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





