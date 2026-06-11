package com.agendadevocional

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.agendadevocional.backup.GoogleDriveBackupHelper
import com.agendadevocional.backup.BackupDataManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agendadevocional.model.MensagemDia
import com.agendadevocional.model.TimelineNota
import java.io.File
import androidx.core.content.FileProvider
import com.agendadevocional.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow


@Composable
fun SettingsMenu(
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    fontSizeMultiplier: Float,
    onFontSizeChange: (Float) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsToggle: (Boolean) -> Unit,
    notificationHour: Int,
    notificationMinute: Int,
    onTimeClick: () -> Unit,
    onSync: (String, (Boolean) -> Unit) -> Unit,
    showOnlyFavorites: Boolean,
    onShowOnlyFavoritesChange: (Boolean) -> Unit,
    selectedLanguage: String,
    onChangeLanguageClick: () -> Unit,
    themeStyle: String,
    onThemeStyleChange: (String) -> Unit,
    mensagens: List<MensagemDia>,
    timelineNotas: List<TimelineNota>,
    readDates: Set<String>,
    onResetAllData: ((Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showUrlEditor by remember { mutableStateOf(false) }

    // Google Drive Backup States & Helper
    val backupHelper = remember { GoogleDriveBackupHelper(context) }
    val backupManager = remember { BackupDataManager(context) }
    var isGoogleSignedIn by remember { mutableStateOf(backupHelper.isUserSignedIn()) }
    var googleUserEmail by remember { mutableStateOf(backupHelper.getSignedInAccount()?.email) }
    var isBackupLoading by remember { mutableStateOf(false) }
    var isRestoreLoading by remember { mutableStateOf(false) }
    var lastBackupTime by remember { mutableStateOf(prefs.getString("last_backup_time", "-") ?: "-") }

    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var backupJsonToRestore by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var ttsSpeechRate by remember { mutableStateOf(prefs.getFloat("tts_speech_rate", 1.0f)) }
    var ttsPitch by remember { mutableStateOf(prefs.getFloat("tts_pitch", 1.0f)) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                isGoogleSignedIn = true
                googleUserEmail = account.email
                Toast.makeText(context, "Conectado: ${account.email}", Toast.LENGTH_SHORT).show()

                // Verificar se existem dados locais a mesclar
                val hasLocalData = readDates.isNotEmpty() || 
                                   timelineNotas.isNotEmpty() || 
                                   mensagens.any { it.isFavorite || !it.anotacao.isNullOrBlank() }

                isRestoreLoading = true
                backupHelper.downloadBackup(
                    onSuccess = { json ->
                        if (json != null) {
                            if (hasLocalData) {
                                isRestoreLoading = false
                                backupJsonToRestore = json
                                showConflictDialog = true
                            } else {
                                // Se o local está vazio, podemos apenas restaurar direto sem perguntar
                                coroutineScope.launch {
                                    val success = backupManager.importAndOverwriteData(json)
                                    isRestoreLoading = false
                                    if (success) {
                                        Toast.makeText(context, "Dados da nuvem recuperados com sucesso!", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } else {
                            // Se não houver backup na nuvem, fazemos upload dos dados locais (se houver algum)
                            if (hasLocalData) {
                                coroutineScope.launch {
                                    try {
                                        val localJson = backupManager.exportDataAsJson()
                                        backupHelper.uploadBackup(
                                            jsonContent = localJson,
                                            onSuccess = {
                                                isRestoreLoading = false
                                                val nowStr = java.text.DateFormat.getDateTimeInstance().format(java.util.Date())
                                                prefs.edit().putString("last_backup_time", nowStr).apply()
                                                lastBackupTime = nowStr
                                                Toast.makeText(context, "Backup inicial enviado para o Google Drive!", Toast.LENGTH_SHORT).show()
                                            },
                                            onFailure = { err ->
                                                isRestoreLoading = false
                                                Toast.makeText(context, "Erro ao enviar backup inicial: ${err.message}", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    } catch (e: Exception) {
                                        isRestoreLoading = false
                                    }
                                }
                            } else {
                                isRestoreLoading = false
                            }
                        }
                    },
                    onFailure = { err ->
                        isRestoreLoading = false
                        Toast.makeText(context, "Erro ao verificar backups: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = if (e is ApiException) {
                "Erro ao conectar conta Google (Código: ${e.statusCode})"
            } else {
                "Erro ao conectar conta Google: ${e.message}"
            }
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(top = 12.dp)
    ) {
        Text(
            text = getLocalizedString(selectedLanguage, "config_titulo"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Left sidebar for Tabs
            Column(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                NavigationTabItem(
                    title = getLocalizedString(selectedLanguage, "config_tab_visual"),
                    icon = Icons.Default.Palette,
                    isSelected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationTabItem(
                    title = getLocalizedString(selectedLanguage, "config_tab_notificacoes"),
                    icon = Icons.Default.Notifications,
                    isSelected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationTabItem(
                    title = getLocalizedString(selectedLanguage, "config_tab_conteudo"),
                    icon = Icons.Default.Sync,
                    isSelected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
                NavigationTabItem(
                    title = getLocalizedString(selectedLanguage, "config_tab_backup"),
                    icon = Icons.Default.Cloud,
                    isSelected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
                )
                NavigationTabItem(
                    title = getLocalizedString(selectedLanguage, "config_tab_sobre"),
                    icon = Icons.Default.Info,
                    isSelected = selectedTab == 4,
                    onClick = { selectedTab = 4 }
                )
            }

            // Vertical Divider
            VerticalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxHeight()
            )

            // Right Content Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // Visual Section
                        val stats = remember(readDates, selectedLanguage) { getAssiduidadeStats(readDates) }
                        AssiduidadeDashboard(stats = stats, selectedLanguage = selectedLanguage)

                        Spacer(modifier = Modifier.height(20.dp))

                        // Mover Canal aqui para maior visibilidade
                        SettingsSection(title = getLocalizedString(selectedLanguage, "config_social")) {
                            val youtubeUrl = "https://www.youtube.com/@bibliafaladaBR"
                            val openYoutube = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))
                                intent.setPackage("com.google.android.youtube")
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl)))
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openYoutube() },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Transparent
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color(0xFFE50914), // Premium YouTube Red
                                                    Color(0xFFFF3333)  // Vibrant Red Accent
                                                )
                                            )
                                        )
                                        .padding(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(Color.White.copy(alpha = 0.2f), shape = CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = getLocalizedString(selectedLanguage, "config_youtube_canal"),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 16.sp
                                                )
                                                Text(
                                                    text = getLocalizedString(selectedLanguage, "config_youtube_nome"),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Button(
                                            onClick = openYoutube,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White,
                                                contentColor = Color(0xFFE50914)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(38.dp)
                                        ) {
                                            Text(
                                                text = getLocalizedString(selectedLanguage, "visitar").uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        SettingsSection(title = getLocalizedString(selectedLanguage, "config_visual")) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(getLocalizedString(selectedLanguage, "config_modo_escuro"), style = MaterialTheme.typography.bodyLarge)
                                Switch(checked = isDarkMode, onCheckedChange = onDarkModeChange)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(getLocalizedString(selectedLanguage, "config_tema_cor"), style = MaterialTheme.typography.bodyLarge)
                                    val themeLabel = when (themeStyle) {
                                        "olive" -> getLocalizedString(selectedLanguage, "tema_oliva")
                                        "royal" -> getLocalizedString(selectedLanguage, "tema_azul")
                                        "rose" -> getLocalizedString(selectedLanguage, "tema_rosa")
                                        else -> getLocalizedString(selectedLanguage, "tema_ouro")
                                    }
                                    Text(
                                        themeLabel, 
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ThemeColorDot(
                                        color = ElegantGold,
                                        isSelected = themeStyle == "gold",
                                        onClick = { onThemeStyleChange("gold") }
                                    )
                                    ThemeColorDot(
                                        color = SageGreen,
                                        isSelected = themeStyle == "olive",
                                        onClick = { onThemeStyleChange("olive") }
                                    )
                                    ThemeColorDot(
                                        color = RoyalBlue,
                                        isSelected = themeStyle == "royal",
                                        onClick = { onThemeStyleChange("royal") }
                                    )
                                    ThemeColorDot(
                                        color = RoseWine,
                                        isSelected = themeStyle == "rose",
                                        onClick = { onThemeStyleChange("rose") }
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(getLocalizedString(selectedLanguage, "config_tamanho_fonte"), style = MaterialTheme.typography.bodyLarge)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onFontSizeChange((fontSizeMultiplier - 0.1f).coerceAtLeast(0.7f)) }) {
                                        Text("A-", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text(
                                        text = "${(fontSizeMultiplier * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(onClick = { onFontSizeChange((fontSizeMultiplier + 0.1f).coerceAtMost(2.0f)) }) {
                                        Text("A+", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Notifications Section
                        SettingsSection(title = getLocalizedString(selectedLanguage, "config_notificacoes")) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(getLocalizedString(selectedLanguage, "config_notificacoes_diarias"), style = MaterialTheme.typography.bodyLarge)
                                Switch(checked = notificationsEnabled, onCheckedChange = onNotificationsToggle)
                            }
                            
                            if (notificationsEnabled) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(getLocalizedString(selectedLanguage, "config_horario"), style = MaterialTheme.typography.bodyLarge)
                                    Surface(
                                        onClick = onTimeClick,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = String.format("%02d:%02d", notificationHour, notificationMinute),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Content Sync & Language Section
                        SettingsSection(title = getLocalizedString(selectedLanguage, "config_conteudo")) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(getLocalizedString(selectedLanguage, "config_sincronizacao"), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        getLocalizedString(selectedLanguage, "config_sincronizacao_desc"), 
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                
                                Button(
                                    onClick = {
                                        isSyncing = true
                                        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                                        val urlKey = when (selectedLanguage) {
                                            "en" -> "sync_url_en"
                                            "es" -> "sync_url_es"
                                            else -> "sync_url_pt"
                                        }
                                        val defaultUrl = when (selectedLanguage) {
                                            "en" -> "https://lankewicz.github.io/AgendaDevovional/dados/agenda_${currentYear}_en.json"
                                            "es" -> "https://lankewicz.github.io/AgendaDevovional/dados/agenda_${currentYear}_es.json"
                                            else -> "https://lankewicz.github.io/AgendaDevovional/dados/agenda_${currentYear}_pt.json"
                                        }
                                        val url = prefs.getString(urlKey, defaultUrl) ?: defaultUrl
                                        
                                        onSync(url) { success ->
                                            isSyncing = false
                                            val msgKey = if (success) "sucesso_sincronizacao" else "erro_sincronizacao"
                                            Toast.makeText(context, getLocalizedString(selectedLanguage, msgKey), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !isSyncing,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text(getLocalizedString(selectedLanguage, "sincronizar"))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(getLocalizedString(selectedLanguage, "config_editar_urls"), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        getLocalizedString(selectedLanguage, "config_editar_urls_desc"), 
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Button(
                                    onClick = { showUrlEditor = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(getLocalizedString(selectedLanguage, "editar"))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(getLocalizedString(selectedLanguage, "config_idioma"), style = MaterialTheme.typography.bodyLarge)
                                    val langLabel = when (selectedLanguage) {
                                        "en" -> getLocalizedString(selectedLanguage, "idioma_ingles")
                                        "es" -> getLocalizedString(selectedLanguage, "idioma_espanhol")
                                        else -> getLocalizedString(selectedLanguage, "idioma_portugues")
                                    }
                                    Text(
                                        "${getLocalizedString(selectedLanguage, "idioma_ativo_prefix")} $langLabel", 
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Button(
                                    onClick = { onChangeLanguageClick() },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(getLocalizedString(selectedLanguage, "alterar"))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(getLocalizedString(selectedLanguage, "config_apenas_favoritos"), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        getLocalizedString(selectedLanguage, "config_apenas_favoritos_desc"), 
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Switch(checked = showOnlyFavorites, onCheckedChange = onShowOnlyFavoritesChange)
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(16.dp))

                            // TTS Speed Slider
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = getLocalizedString(selectedLanguage, "config_tts_velocidade"),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = String.format("%.1fx", ttsSpeechRate),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = ttsSpeechRate,
                                    onValueChange = {
                                        ttsSpeechRate = it
                                        prefs.edit().putFloat("tts_speech_rate", it).apply()
                                    },
                                    valueRange = 0.5f..2.0f,
                                    steps = 14
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // TTS Pitch Slider
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = getLocalizedString(selectedLanguage, "config_tts_tom"),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    val pitchLabel = when {
                                        ttsPitch < 0.9f -> getLocalizedString(selectedLanguage, "tts_pitch_grave")
                                        ttsPitch > 1.1f -> getLocalizedString(selectedLanguage, "tts_pitch_agudo")
                                        else -> getLocalizedString(selectedLanguage, "tts_pitch_normal")
                                    }
                                    Text(
                                        text = pitchLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = ttsPitch,
                                    onValueChange = {
                                        ttsPitch = it
                                        prefs.edit().putFloat("tts_pitch", it).apply()
                                    },
                                    valueRange = 0.5f..2.0f,
                                    steps = 14
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Manage Phone Voices Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = getLocalizedString(selectedLanguage, "config_tts_gerenciar"),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = getLocalizedString(selectedLanguage, "config_tts_gerenciar_desc"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent("com.android.settings.TTS_SETTINGS")
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            try {
                                                val intent = Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                                                context.startActivity(intent)
                                            } catch (e2: Exception) {
                                                Toast.makeText(context, "Erro ao abrir configurações de TTS", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(getLocalizedString(selectedLanguage, "editar"))
                                }
                            }
                        }
                    }
                    3 -> {
                        // Backup & Export Section
                        SettingsSection(title = getLocalizedString(selectedLanguage, "backup_titulo")) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (!isGoogleSignedIn) {
                                    Button(
                                        onClick = {
                                            googleSignInLauncher.launch(backupHelper.googleSignInClient.signInIntent)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(getLocalizedString(selectedLanguage, "backup_conectar"))
                                    }
                                } else {
                                    Text(
                                        text = "Conectado como: ${googleUserEmail ?: ""}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = String.format(getLocalizedString(selectedLanguage, "backup_ultimo"), lastBackupTime),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                isBackupLoading = true
                                                coroutineScope.launch {
                                                    try {
                                                        val json = backupManager.exportDataAsJson()
                                                        backupHelper.uploadBackup(
                                                            jsonContent = json,
                                                            onSuccess = {
                                                                isBackupLoading = false
                                                                val nowStr = java.text.DateFormat.getDateTimeInstance().format(java.util.Date())
                                                                prefs.edit().putString("last_backup_time", nowStr).apply()
                                                                lastBackupTime = nowStr
                                                                Toast.makeText(context, getLocalizedString(selectedLanguage, "backup_sucesso"), Toast.LENGTH_SHORT).show()
                                                            },
                                                            onFailure = { err ->
                                                                isBackupLoading = false
                                                                Toast.makeText(context, "${getLocalizedString(selectedLanguage, "backup_erro")}: ${err.message}", Toast.LENGTH_LONG).show()
                                                            }
                                                        )
                                                    } catch (e: Exception) {
                                                        isBackupLoading = false
                                                        Toast.makeText(context, "${getLocalizedString(selectedLanguage, "backup_erro")}: ${e.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            enabled = !isBackupLoading,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (isBackupLoading) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            } else {
                                                Text(getLocalizedString(selectedLanguage, "backup_fazer_backup"))
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                showRestoreConfirmDialog = true
                                            },
                                            enabled = !isRestoreLoading,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (isRestoreLoading) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            } else {
                                                Text(getLocalizedString(selectedLanguage, "backup_restaurar"))
                                            }
                                        }
                                    }

                                    TextButton(
                                        onClick = {
                                            backupHelper.googleSignInClient.signOut().addOnCompleteListener {
                                                isGoogleSignedIn = false
                                                googleUserEmail = null
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Text(getLocalizedString(selectedLanguage, "backup_desconectar"), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        SettingsSection(title = getLocalizedString(selectedLanguage, "config_exportar_dados")) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(getLocalizedString(selectedLanguage, "config_exportar"), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        getLocalizedString(selectedLanguage, "config_exportar_desc"), 
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Button(
                                    onClick = { showExportDialog = true },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(getLocalizedString(selectedLanguage, "exportar"))
                                }
                            }
                        }
                    }
                    4 -> {
                        // About Section (Copyright, Reset Data)
                        SettingsSection(title = getLocalizedString(selectedLanguage, "config_copyright_titulo")) {
                            Text(
                                text = getLocalizedString(selectedLanguage, "config_copyright_desc"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        SettingsSection(title = getLocalizedString(selectedLanguage, "config_limpar_dados_titulo")) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        getLocalizedString(selectedLanguage, "config_limpar_dados_titulo"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        getLocalizedString(selectedLanguage, "config_limpar_dados_desc"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Button(
                                    onClick = { showResetDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text(getLocalizedString(selectedLanguage, "limpar_dados_botao"))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        val versionName = remember {
                            try {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName
                            } catch (e: Exception) {
                                "1.1.0"
                            }
                        }

                        Text(
                            text = "v$versionName",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        // Dialogs positioned at the root
        if (showUrlEditor) {
            var urlPt by remember { mutableStateOf(prefs.getString("sync_url_pt", "https://raw.githubusercontent.com/usuario/repo/main/agenda.json") ?: "") }
            var urlEn by remember { mutableStateOf(prefs.getString("sync_url_en", "https://raw.githubusercontent.com/usuario/repo/main/agenda_en.json") ?: "") }
            var urlEs by remember { mutableStateOf(prefs.getString("sync_url_es", "https://raw.githubusercontent.com/usuario/repo/main/agenda_es.json") ?: "") }

            AlertDialog(
                onDismissRequest = { showUrlEditor = false },
                title = { Text(getLocalizedString(selectedLanguage, "config_editar_urls"), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = urlPt,
                            onValueChange = { urlPt = it },
                            label = { Text(getLocalizedString(selectedLanguage, "url_pt")) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = urlEn,
                            onValueChange = { urlEn = it },
                            label = { Text(getLocalizedString(selectedLanguage, "url_en")) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = urlEs,
                            onValueChange = { urlEs = it },
                            label = { Text(getLocalizedString(selectedLanguage, "url_es")) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        prefs.edit()
                            .putString("sync_url_pt", urlPt)
                            .putString("sync_url_en", urlEn)
                            .putString("sync_url_es", urlEs)
                            .apply()
                        showUrlEditor = false
                        Toast.makeText(context, getLocalizedString(selectedLanguage, "sucesso_url_update"), Toast.LENGTH_SHORT).show()
                    }) {
                        Text(getLocalizedString(selectedLanguage, "salvar"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUrlEditor = false }) {
                        Text(getLocalizedString(selectedLanguage, "cancelar"))
                    }
                }
            )
        }

        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text(getLocalizedString(selectedLanguage, "exportar_anotacoes_titulo"), fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = getLocalizedString(selectedLanguage, "exportar_anotacoes_desc"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                showExportDialog = false
                                handleExportWhatsapp(context, mensagens, timelineNotas, selectedLanguage)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(getLocalizedString(selectedLanguage, "exportar_whatsapp"))
                        }
                        
                        Button(
                            onClick = {
                                showExportDialog = false
                                handleExportEmail(context, mensagens, timelineNotas, selectedLanguage)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(getLocalizedString(selectedLanguage, "exportar_email"))
                        }
                        
                        Button(
                            onClick = {
                                showExportDialog = false
                                handleExportIcs(context, mensagens, timelineNotas, selectedLanguage)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text(
                                text = getLocalizedString(selectedLanguage, "exportar_drive"),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Button(
                            onClick = {
                                showExportDialog = false
                                handleOpenLocalCalendarApp(context, mensagens, timelineNotas, selectedLanguage)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Text(
                                text = getLocalizedString(selectedLanguage, "exportar_local_app"),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showExportDialog = false }) {
                        Text(getLocalizedString(selectedLanguage, "cancelar"))
                    }
                }
            )
        }

        if (showRestoreConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = false },
                title = { Text(getLocalizedString(selectedLanguage, "backup_confirmacao_restaurar_titulo"), fontWeight = FontWeight.Bold) },
                text = { Text(getLocalizedString(selectedLanguage, "backup_confirmacao_restaurar_desc")) },
                confirmButton = {
                    Button(onClick = {
                        showRestoreConfirmDialog = false
                        isRestoreLoading = true
                        backupHelper.downloadBackup(
                            onSuccess = { json ->
                                if (json == null) {
                                    isRestoreLoading = false
                                    Toast.makeText(context, "Nenhum backup encontrado no Google Drive.", Toast.LENGTH_LONG).show()
                                    return@downloadBackup
                                }
                                coroutineScope.launch {
                                    val success = backupManager.importAndMergeData(json)
                                    isRestoreLoading = false
                                    if (success) {
                                        Toast.makeText(context, getLocalizedString(selectedLanguage, "backup_restaurado"), Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, getLocalizedString(selectedLanguage, "backup_erro"), Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onFailure = { err ->
                                isRestoreLoading = false
                                Toast.makeText(context, "${getLocalizedString(selectedLanguage, "backup_erro")}: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                    }) {
                        Text(getLocalizedString(selectedLanguage, "confirmar"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirmDialog = false }) {
                        Text(getLocalizedString(selectedLanguage, "cancelar"))
                    }
                }
            )
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = {
                    Text(
                        text = getLocalizedString(selectedLanguage, "limpar_dados_confirmacao_titulo"),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text(
                        text = getLocalizedString(selectedLanguage, "limpar_dados_confirmacao_desc"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showResetDialog = false
                            onResetAllData { success ->
                                val msgKey = if (success) "limpar_dados_sucesso" else "limpar_dados_erro"
                                Toast.makeText(context, getLocalizedString(selectedLanguage, msgKey), Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(getLocalizedString(selectedLanguage, "limpar_dados_botao"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text(getLocalizedString(selectedLanguage, "cancelar"))
                    }
                }
            )
        }

        if (showConflictDialog && backupJsonToRestore != null) {
            AlertDialog(
                onDismissRequest = { 
                    showConflictDialog = false
                    backupJsonToRestore = null
                },
                title = { 
                    Text(
                        text = "Conflito de Backup Encontrado", 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ) 
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Identificamos um backup salvo nesta conta do Google. Como você já possui dados locais neste celular, escolha como deseja prosseguir:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Option 1: Merge
                        Button(
                            onClick = {
                                val json = backupJsonToRestore!!
                                showConflictDialog = false
                                backupJsonToRestore = null
                                isRestoreLoading = true
                                coroutineScope.launch {
                                    val success = backupManager.importAndMergeData(json)
                                    if (success) {
                                        // Upload the merged data back to the cloud
                                        try {
                                            val mergedJson = backupManager.exportDataAsJson()
                                            backupHelper.uploadBackup(
                                                jsonContent = mergedJson,
                                                onSuccess = {
                                                    isRestoreLoading = false
                                                    val nowStr = java.text.DateFormat.getDateTimeInstance().format(java.util.Date())
                                                    prefs.edit().putString("last_backup_time", nowStr).apply()
                                                    lastBackupTime = nowStr
                                                    Toast.makeText(context, "Dados mesclados e sincronizados com o Drive!", Toast.LENGTH_LONG).show()
                                                },
                                                onFailure = { err ->
                                                    isRestoreLoading = false
                                                    Toast.makeText(context, "Mesclado localmente, mas erro ao salvar na nuvem: ${err.message}", Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        } catch (e: Exception) {
                                            isRestoreLoading = false
                                            Toast.makeText(context, "Mesclado localmente, erro ao exportar: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        isRestoreLoading = false
                                        Toast.makeText(context, "Erro ao mesclar dados.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("1. Mesclar Dados (Recomendado)", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Junta os dados antigos da nuvem com os novos deste aparelho sem apagar nada.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Option 2: Overwrite local with cloud
                        Button(
                            onClick = {
                                val json = backupJsonToRestore!!
                                showConflictDialog = false
                                backupJsonToRestore = null
                                isRestoreLoading = true
                                coroutineScope.launch {
                                    val success = backupManager.importAndOverwriteData(json)
                                    isRestoreLoading = false
                                    if (success) {
                                        val nowStr = java.text.DateFormat.getDateTimeInstance().format(java.util.Date())
                                        prefs.edit().putString("last_backup_time", nowStr).apply()
                                        lastBackupTime = nowStr
                                        Toast.makeText(context, "Dados locais substituídos pelo backup da nuvem!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Erro ao restaurar dados.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("2. Manter Antigo (Substituir local)", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Apaga os dados atuais do aparelho e restaura apenas o que estava na nuvem.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Option 3: Overwrite cloud with local
                        Button(
                            onClick = {
                                showConflictDialog = false
                                backupJsonToRestore = null
                                isBackupLoading = true
                                coroutineScope.launch {
                                    try {
                                        val localJson = backupManager.exportDataAsJson()
                                        backupHelper.uploadBackup(
                                            jsonContent = localJson,
                                            onSuccess = {
                                                isBackupLoading = false
                                                val nowStr = java.text.DateFormat.getDateTimeInstance().format(java.util.Date())
                                                prefs.edit().putString("last_backup_time", nowStr).apply()
                                                lastBackupTime = nowStr
                                                Toast.makeText(context, "Nuvem atualizada com os dados do celular!", Toast.LENGTH_SHORT).show()
                                            },
                                            onFailure = { err ->
                                                isBackupLoading = false
                                                Toast.makeText(context, "Erro ao atualizar nuvem: ${err.message}", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    } catch (e: Exception) {
                                        isBackupLoading = false
                                        Toast.makeText(context, "Erro ao exportar dados: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("3. Manter Novo (Substituir nuvem)", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Mantém apenas os dados atuais do aparelho e atualiza a nuvem com eles.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { 
                        showConflictDialog = false
                        backupJsonToRestore = null
                    }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

private fun addOneDayToIso(isoDate: String): String? {
    return try {
        val date = java.time.LocalDate.parse(isoDate)
        date.plusDays(1).toString()
    } catch (e: Exception) {
        null
    }
}

private fun compileNotesText(
    mensagens: List<MensagemDia>,
    timelineNotas: List<TimelineNota>,
    selectedLanguage: String
): String {
    val notesMap = timelineNotas.groupBy { it.data }
    return buildString {
        appendLine("==================================================")
        appendLine(getLocalizedString(selectedLanguage, "export_titulo_txt"))
        appendLine("==================================================")
        appendLine()
        
        var hasContent = false
        for (msg in mensagens) {
            val dailyNote = msg.anotacao ?: ""
            val timelineNotesList = mutableListOf<Pair<Int, String>>()
            
            val dataIso = parseToIsoDate(msg.data) ?: msg.data
            val dayNotes = notesMap[dataIso] ?: emptyList()
            for (hour in 0..23) {
                val note = dayNotes.firstOrNull { it.hora == hour }?.texto ?: ""
                if (note.isNotEmpty()) {
                    timelineNotesList.add(Pair(hour, note))
                }
            }
            
            if (dailyNote.isNotEmpty() || timelineNotesList.isNotEmpty()) {
                hasContent = true
                appendLine("[${msg.data}]")
                appendLine("${getLocalizedString(selectedLanguage, "ref")}: ${msg.referencia}")
                
                if (dailyNote.isNotEmpty()) {
                    appendLine("📝 ${getLocalizedString(selectedLanguage, "anotacao_diaria")}:")
                    appendLine(dailyNote)
                }
                
                if (timelineNotesList.isNotEmpty()) {
                    appendLine("⏰ ${getLocalizedString(selectedLanguage, "notas_timeline")}:")
                    for ((hour, note) in timelineNotesList) {
                        appendLine(String.format("- %02d:00: %s", hour, note))
                    }
                }
                appendLine("--------------------------------------------------")
                appendLine()
            }
        }
        
        if (!hasContent) {
            appendLine(getLocalizedString(selectedLanguage, "nenhuma_anotacao_exportar"))
        }
    }
}

private fun handleExportWhatsapp(
    context: Context,
    mensagens: List<MensagemDia>,
    timelineNotas: List<TimelineNota>,
    selectedLanguage: String
) {
    val text = compileNotesText(mensagens, timelineNotas, selectedLanguage)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    
    val whatsappIntent = Intent(intent).apply {
        setPackage("com.whatsapp")
    }
    
    try {
        context.startActivity(whatsappIntent)
    } catch (e: Exception) {
        val whatsappBusinessIntent = Intent(intent).apply {
            setPackage("com.whatsapp.w4b")
        }
        try {
            context.startActivity(whatsappBusinessIntent)
        } catch (ex: Exception) {
            context.startActivity(Intent.createChooser(intent, getLocalizedString(selectedLanguage, "exportar")))
        }
    }
}

private fun handleExportEmail(
    context: Context,
    mensagens: List<MensagemDia>,
    timelineNotas: List<TimelineNota>,
    selectedLanguage: String
) {
    val text = compileNotesText(mensagens, timelineNotas, selectedLanguage)
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_SUBJECT, getLocalizedString(selectedLanguage, "export_titulo_txt"))
        putExtra(Intent.EXTRA_TEXT, text)
    }
    
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getLocalizedString(selectedLanguage, "export_titulo_txt"))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(fallbackIntent, getLocalizedString(selectedLanguage, "exportar")))
    }
}

private fun generateIcsFile(
    context: Context,
    mensagens: List<MensagemDia>,
    timelineNotas: List<TimelineNota>,
    selectedLanguage: String
): Uri? {
    val notesMap = timelineNotas.groupBy { it.data }
    val icsString = buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//Agenda Devocional//NONSGML v1.0//${selectedLanguage.uppercase()}")
        
        for (msg in mensagens) {
            val dailyNote = msg.anotacao ?: ""
            val timelineNotesList = mutableListOf<Pair<Int, String>>()
            
            val isoDate = parseToIsoDate(msg.data) ?: continue
            val dayNotes = notesMap[isoDate] ?: emptyList()
            for (hour in 0..23) {
                val note = dayNotes.firstOrNull { it.hora == hour }?.texto ?: ""
                if (note.isNotEmpty()) {
                    timelineNotesList.add(Pair(hour, note))
                }
            }
            
            if (dailyNote.isNotEmpty() || timelineNotesList.isNotEmpty()) {
                val cleanIso = isoDate.replace("-", "")
                
                val descBuilder = StringBuilder()
                descBuilder.append("Versículo: \\\"${msg.versiculo}\\\" (${msg.referencia})\\n\\n")
                if (dailyNote.isNotEmpty()) {
                    descBuilder.append("📝 Anotação Diária:\\n$dailyNote\\n\\n")
                }
                if (timelineNotesList.isNotEmpty()) {
                    descBuilder.append("⏰ Notas da Timeline:\\n")
                    for ((hour, note) in timelineNotesList) {
                        descBuilder.append(String.format("- %02d:00: %s\\n", hour, note))
                    }
                }
                val cleanDesc = descBuilder.toString()
                    .replace("\n", "\\n")
                    .replace("\r", "")
                
                appendLine("BEGIN:VEVENT")
                appendLine("UID:devocional_${cleanIso}@agendadevocional.com")
                appendLine("DTSTAMP:${cleanIso}T080000Z")
                appendLine("DTSTART;VALUE=DATE:${cleanIso}")
                val endIso = addOneDayToIso(isoDate) ?: cleanIso
                appendLine("DTEND;VALUE=DATE:${endIso.replace("-", "")}")
                appendLine("SUMMARY:Devocional - ${msg.referencia}")
                appendLine("DESCRIPTION:$cleanDesc")
                appendLine("END:VEVENT")
            }
        }
        appendLine("END:VCALENDAR")
    }
    
    return try {
        val cacheFile = File(context.cacheDir, "devocional_anotacoes.ics")
        cacheFile.writeText(icsString, Charsets.UTF_8)
        
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun handleExportIcs(
    context: Context,
    mensagens: List<MensagemDia>,
    timelineNotas: List<TimelineNota>,
    selectedLanguage: String
) {
    try {
        val uri = generateIcsFile(context, mensagens, timelineNotas, selectedLanguage)
        if (uri == null) {
            Toast.makeText(context, getLocalizedString(selectedLanguage, "erro_gerar_calendario"), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, getLocalizedString(selectedLanguage, "exportar_calendario_titulo")))
    } catch (e: Exception) {
        e.printStackTrace()
        val errText = String.format(getLocalizedString(selectedLanguage, "erro_exportar_prefix"), e.message ?: "")
        Toast.makeText(context, errText, Toast.LENGTH_SHORT).show()
    }
}

private fun handleOpenLocalCalendarApp(
    context: Context,
    mensagens: List<MensagemDia>,
    timelineNotas: List<TimelineNota>,
    selectedLanguage: String
) {
    try {
        val uri = generateIcsFile(context, mensagens, timelineNotas, selectedLanguage)
        if (uri == null) {
            Toast.makeText(context, getLocalizedString(selectedLanguage, "erro_gerar_calendario"), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/calendar")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, getLocalizedString(selectedLanguage, "exportar_local_app")))
    } catch (e: Exception) {
        e.printStackTrace()
        val errText = String.format(getLocalizedString(selectedLanguage, "erro_abrir_agenda_prefix"), e.message ?: "")
        Toast.makeText(context, errText, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ThemeColorDot(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable(role = Role.Button, onClick = onClick)
    )
}

@Composable
private fun NavigationTabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val indicatorWidth = 56.dp
    val indicatorHeight = 32.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = indicatorWidth, height = indicatorHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

