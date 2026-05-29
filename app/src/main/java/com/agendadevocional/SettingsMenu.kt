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
    onChangeLanguageClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    val scrollState = rememberScrollState()
    var isSyncing by remember { mutableStateOf(false) }
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
                        val activeUrl = when (selectedLanguage) {
                            "en" -> prefs.getString("sync_url_en", "https://raw.githubusercontent.com/usuario/repo/main/agenda_en.json")
                            "es" -> prefs.getString("sync_url_es", "https://raw.githubusercontent.com/usuario/repo/main/agenda_es.json")
                            else -> prefs.getString("sync_url_pt", "https://raw.githubusercontent.com/usuario/repo/main/agenda.json")
                        } ?: ""
                        onSync(activeUrl) { success ->
                            isSyncing = false
                            if (success) {
                                Toast.makeText(context, getLocalizedString(selectedLanguage, "sucesso_sincronizacao"), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, getLocalizedString(selectedLanguage, "erro_sincronizacao"), Toast.LENGTH_SHORT).show()
                            }
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
