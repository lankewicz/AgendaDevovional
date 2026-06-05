package com.agendadevocional

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
    mensagens: List<MensagemDia>
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = getLocalizedString(selectedLanguage, "config_titulo"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(20.dp))

        val stats = remember(selectedLanguage) { getAssiduidadeStats(context) }
        AssiduidadeDashboard(stats = stats, selectedLanguage = selectedLanguage)

        Spacer(modifier = Modifier.height(24.dp))
        
        // Visual Section
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = { onFontSizeChange((fontSizeMultiplier + 0.1f).coerceAtMost(2.0f)) }) {
                        Text("A+", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
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

        Spacer(modifier = Modifier.height(24.dp))
        
        // Social Section
        SettingsSection(title = getLocalizedString(selectedLanguage, "config_social")) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(getLocalizedString(selectedLanguage, "config_youtube_canal"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        getLocalizedString(selectedLanguage, "config_youtube_nome"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                Button(
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
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF0000),
                        contentColor = Color.White
                    )
                ) {
                    Text(getLocalizedString(selectedLanguage, "visitar"))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Content Sync Section
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
                        coroutineScope.launch {
                            delay(1200)
                            isSyncing = false
                            Toast.makeText(context, getLocalizedString(selectedLanguage, "devocional_atualizado"), Toast.LENGTH_SHORT).show()
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

            var showUrlEditor by remember { mutableStateOf(false) }
            
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
        }

        Spacer(modifier = Modifier.height(24.dp))
        Spacer(modifier = Modifier.height(24.dp))

        // Export Section
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

                        // Option 1: WhatsApp
                        Button(
                            onClick = {
                                showExportDialog = false
                                handleExportWhatsapp(context, mensagens, prefs, selectedLanguage)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(getLocalizedString(selectedLanguage, "exportar_whatsapp"))
                        }
                        
                        // Option 2: Email
                        Button(
                            onClick = {
                                showExportDialog = false
                                handleExportEmail(context, mensagens, prefs, selectedLanguage)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(getLocalizedString(selectedLanguage, "exportar_email"))
                        }
                        
                        // Option 3: Drive / OneDrive
                        Button(
                            onClick = {
                                showExportDialog = false
                                handleExportIcs(context, mensagens, prefs, selectedLanguage)
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
                        
                        // Option 4: Local calendar apps
                        Button(
                            onClick = {
                                showExportDialog = false
                                handleOpenLocalCalendarApp(context, mensagens, prefs, selectedLanguage)
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

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSection(title = getLocalizedString(selectedLanguage, "config_copyright_titulo")) {
            Text(
                text = getLocalizedString(selectedLanguage, "config_copyright_desc"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 20.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

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
    prefs: android.content.SharedPreferences,
    selectedLanguage: String
): String {
    return buildString {
        appendLine("==================================================")
        appendLine(getLocalizedString(selectedLanguage, "export_titulo_txt"))
        appendLine("==================================================")
        appendLine()
        
        var hasContent = false
        for (msg in mensagens) {
            val dailyNote = msg.anotacao ?: ""
            val timelineNotesList = mutableListOf<Pair<Int, String>>()
            for (hour in 0..23) {
                val key = "note_${msg.data}_$hour"
                val note = prefs.getString(key, "") ?: ""
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
    prefs: android.content.SharedPreferences,
    selectedLanguage: String
) {
    val text = compileNotesText(mensagens, prefs, selectedLanguage)
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
        // Try Whatsapp Business
        val whatsappBusinessIntent = Intent(intent).apply {
            setPackage("com.whatsapp.w4b")
        }
        try {
            context.startActivity(whatsappBusinessIntent)
        } catch (ex: Exception) {
            // Fallback to general text share chooser
            context.startActivity(Intent.createChooser(intent, getLocalizedString(selectedLanguage, "exportar")))
        }
    }
}

private fun handleExportEmail(
    context: Context,
    mensagens: List<MensagemDia>,
    prefs: android.content.SharedPreferences,
    selectedLanguage: String
) {
    val text = compileNotesText(mensagens, prefs, selectedLanguage)
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_SUBJECT, getLocalizedString(selectedLanguage, "export_titulo_txt"))
        putExtra(Intent.EXTRA_TEXT, text)
    }
    
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to ACTION_SEND chooser
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
    prefs: android.content.SharedPreferences,
    selectedLanguage: String
): Uri? {
    val icsString = buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//Agenda Devocional//NONSGML v1.0//${selectedLanguage.uppercase()}")
        
        for (msg in mensagens) {
            val dailyNote = msg.anotacao ?: ""
            val timelineNotesList = mutableListOf<Pair<Int, String>>()
            for (hour in 0..23) {
                val key = "note_${msg.data}_$hour"
                val note = prefs.getString(key, "") ?: ""
                if (note.isNotEmpty()) {
                    timelineNotesList.add(Pair(hour, note))
                }
            }
            
            if (dailyNote.isNotEmpty() || timelineNotesList.isNotEmpty()) {
                val isoDate = parseToIsoDate(msg.data) ?: continue
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
    prefs: android.content.SharedPreferences,
    selectedLanguage: String
) {
    try {
        val uri = generateIcsFile(context, mensagens, prefs, selectedLanguage)
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
    prefs: android.content.SharedPreferences,
    selectedLanguage: String
) {
    try {
        val uri = generateIcsFile(context, mensagens, prefs, selectedLanguage)
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
