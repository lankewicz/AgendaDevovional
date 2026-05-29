package com.agendadevocional

import com.agendadevocional.model.MensagemDia
import android.content.Context
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.util.Locale

@Composable
fun AgendaTimelineOverlay(
    mensagemDia: MensagemDia,
    onClose: () -> Unit,
    onSaveAnotacao: (MensagemDia, String?) -> Unit,
    onSaveAudioPath: (MensagemDia, String?) -> Unit,
    audioRecorder: AndroidAudioRecorder,
    audioPlayer: AndroidAudioPlayer,
    fontSizeMultiplier: Float,
    selectedLanguage: String
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var showFullDay by remember { mutableStateOf(false) }
    
    val hoursList = if (showFullDay) (0..23).toList() else (7..18).toList()
    
    val msgIso = parseToIsoDate(mensagemDia.data)
    val isFuture = msgIso != null && msgIso > LocalDate.now().toString()
    val hasSavedContent = !mensagemDia.anotacao.isNullOrBlank() || !mensagemDia.audioPath.isNullOrBlank()
    
    val dataParts = mensagemDia.data.split(" de ")
    val dayNum = dataParts.getOrNull(0) ?: "01"
    val monthYear = if (dataParts.size >= 3) "${dataParts[1]} ${dataParts[2]}" else mensagemDia.data
    
    val localDate = try {
        LocalDate.parse(msgIso)
    } catch (e: Exception) {
        LocalDate.now()
    }
    
    val dayOfWeek = localDate.dayOfWeek
    val dayOfWeekName = when (selectedLanguage) {
        "en" -> dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.US)
        "es" -> dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag("es"))
        else -> dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag("pt-BR"))
    }.replaceFirstChar { it.uppercase() }

    // Hourly notes state map
    val hourlyNotes = remember(mensagemDia.data) { mutableStateMapOf<Int, String>() }
    
    LaunchedEffect(mensagemDia.data, showFullDay) {
        val listToLoad = if (showFullDay) (0..23).toList() else (7..18).toList()
        listToLoad.forEach { hour ->
            val key = "note_${mensagemDia.data}_$hour"
            val savedValue = prefs.getString(key, "") ?: ""
            if (savedValue.isNotEmpty()) {
                hourlyNotes[hour] = savedValue
            } else {
                hourlyNotes.remove(hour)
            }
        }
    }

    var editingHour by remember { mutableStateOf<Int?>(null) }
    var editingText by remember(editingHour) {
        mutableStateOf(editingHour?.let { hourlyNotes[it] } ?: "")
    }

    // Speech dictation state
    var isListening by remember { mutableStateOf(false) }
    var listeningFeedback by remember { mutableStateOf("") }

    val speechToTextHelper = remember {
        SpeechToTextHelper(
            context = context,
            onResult = { partialResult ->
                editingText = if (editingText.isEmpty()) partialResult else "$editingText $partialResult"
            },
            onError = { errorMsg ->
                isListening = false
                listeningFeedback = ""
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            },
            onReadyForSpeech = {
                listeningFeedback = getLocalizedString(selectedLanguage, "ouvindo")
            },
            onEndOfSpeech = {
                isListening = false
                listeningFeedback = ""
            }
        )
    }

    val requestRecordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isListening = true
            speechToTextHelper.startListening()
        } else {
            Toast.makeText(context, getLocalizedString(selectedLanguage, "permissao_negada"), Toast.LENGTH_SHORT).show()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Header with the Verse
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "“${mensagemDia.versiculo}” (${mensagemDia.referencia})",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            color = Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Filter Toggle Button
                    TextButton(
                        onClick = { showFullDay = !showFullDay },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text(
                            text = if (showFullDay) "24h" else "7h-18h",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // 2. Date detail block (Outlook day view header)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = dayNum,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = dayOfWeekName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(24.dp))
                    
                    Column {
                        Text(
                            text = monthYear,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = getLocalizedString(selectedLanguage, "app_title"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // 3. Visual Grid/Timeline
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(vertical = 8.dp)
                ) {
                    hoursList.forEach { hour ->
                        val hourStr = String.format("%02d:00", hour)
                        
                        val isContextSlot = hour == 9
                        val isMeaningSlot = hour == 11
                        val isMessageSlot = hour == 13
                        
                        val reminderKey = "reminder_${mensagemDia.data}_$hour"
                        val offsetKey = "reminder_offset_${mensagemDia.data}_$hour"
                        var isReminderSet by remember(mensagemDia.data, hour) {
                            mutableStateOf(prefs.getBoolean(reminderKey, false))
                        }
                        var reminderOffset by remember(mensagemDia.data, hour) {
                            mutableStateOf(prefs.getInt(offsetKey, 5))
                        }
                        var showReminderMenu by remember { mutableStateOf(false) }

                        val isCommercial = hour in 8..18
                        val slotBg = if (isCommercial) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }

                        val noteText = hourlyNotes[hour] ?: ""

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Hour Column (Left)
                            Text(
                                text = hourStr,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .width(60.dp)
                                    .padding(top = 4.dp)
                            )
                            
                            // Event/Devotional Card Column (Right)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(slotBg, RoundedCornerShape(8.dp))
                                    .border(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        editingHour = hour
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                if (editingHour == hour) {
                                    // INLINE EDIT MODE
                                    val focusRequester = remember { FocusRequester() }
                                    LaunchedEffect(Unit) {
                                        focusRequester.requestFocus()
                                    }
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        BasicTextField(
                                            value = editingText,
                                            onValueChange = { editingText = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(focusRequester),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeMultiplier).sp
                                            ),
                                            decorationBox = { innerTextField ->
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    if (editingText.isEmpty()) {
                                                        Text(
                                                            text = getLocalizedString(selectedLanguage, "hint_escreva"),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                        
                                        if (isListening && listeningFeedback.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = listeningFeedback,
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Mic button
                                            IconButton(
                                                onClick = {
                                                    if (isListening) {
                                                        speechToTextHelper.stopListening()
                                                        isListening = false
                                                    } else {
                                                        val recordPermission = ContextCompat.checkSelfPermission(
                                                            context,
                                                            Manifest.permission.RECORD_AUDIO
                                                        )
                                                        if (recordPermission == PackageManager.PERMISSION_GRANTED) {
                                                            isListening = true
                                                            speechToTextHelper.startListening()
                                                        } else {
                                                            requestRecordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                                    contentDescription = "Ditar",
                                                    tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
                                            // Cancel button
                                            IconButton(
                                                onClick = {
                                                    if (isListening) {
                                                        speechToTextHelper.stopListening()
                                                        isListening = false
                                                    }
                                                    editingHour = null
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Cancelar",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            // Save button
                                            IconButton(
                                                onClick = {
                                                    if (isListening) {
                                                        speechToTextHelper.stopListening()
                                                        isListening = false
                                                    }
                                                    val key = "note_${mensagemDia.data}_$hour"
                                                    if (editingText.isNotBlank()) {
                                                        prefs.edit().putString(key, editingText.trim()).apply()
                                                        hourlyNotes[hour] = editingText.trim()
                                                    } else {
                                                        prefs.edit().remove(key).apply()
                                                        hourlyNotes.remove(hour)
                                                    }
                                                    editingHour = null
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Save,
                                                    contentDescription = "Salvar",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // READ MODE OR DEFAULT VIEW FOR THE CARD
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Devotional items if applicable (displayed side-by-side or above custom notes)
                                        when {
                                            isContextSlot && hasSavedContent -> {
                                                Text(
                                                    text = mensagemDia.contexto,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeMultiplier).sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            isMeaningSlot && hasSavedContent -> {
                                                Text(
                                                    text = mensagemDia.significado,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeMultiplier).sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            isMessageSlot && hasSavedContent -> {
                                                Text(
                                                    text = getLocalizedString(selectedLanguage, "mensagem").uppercase(),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                if (isFuture) {
                                                    val stableHash = kotlin.math.abs(mensagemDia.data.hashCode())
                                                    val verseIdx = stableHash % 5
                                                    val lockedText = when (selectedLanguage) {
                                                        "en" -> {
                                                            val v = when (verseIdx) {
                                                                0 -> "“My times are in your hand.” — Psalm 31:15"
                                                                1 -> "“Therefore don’t be anxious for tomorrow.” — Matthew 6:34"
                                                                2 -> "“If the Lord wills…” — James 4:15"
                                                                3 -> "“For everything there is a season.” — Ecclesiastes 3:1"
                                                                else -> "“Yahweh directs his steps.” — Proverbs 16:9"
                                                            }
                                                            "This devotional will be available at the right time.\n\n$v\n\nCome back on this day and walk with us through another reflection on God’s Word."
                                                        }
                                                        "es" -> {
                                                            val v = when (verseIdx) {
                                                                0 -> "“En tu mano están mis tiempos.” — Salmos 31:15"
                                                                1 -> "“No os afanéis por el día de mañana.” — Mateo 6:34"
                                                                2 -> "“Si el Señor quiere…” — Santiago 4:15"
                                                                3 -> "“Todo tiene su tiempo.” — Eclesiástes 3:1"
                                                                else -> "“Jehová endereza sus pasos.” — Proverbios 16:9"
                                                            }
                                                            "Este devocional estará disponible en el tempo señalado.\n\n$v\n\nVuelve en este día y camina con nosotros en una nueva reflexión en la Palabra de Dios."
                                                        }
                                                        else -> {
                                                            val v = when (verseIdx) {
                                                                0 -> "“Os meus tempos estão nas tuas mãos.” — Salmo 31:15"
                                                                1 -> "“Não vos inquieteis pelo dia de amanhã.” — Mateus 6:34"
                                                                2 -> "“Se o Senhor quiser...” — Tiago 4:15"
                                                                3 -> "“Tudo tem o seu tempo determinado.” — Eclesiastes 3:1"
                                                                else -> "“O Senhor lhe dirige os passos.” — Provérbios 16:9"
                                                            }
                                                            "Este devocional estará disponível no tempo certo.\n\n$v\n\nVolte neste dia e caminhe conosco em mais uma reflexão na Palavra de Deus."
                                                        }
                                                    }
                                                    Text(
                                                        text = lockedText,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeMultiplier).sp,
                                                            fontStyle = FontStyle.Italic
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                    )
                                                } else {
                                                    Text(
                                                        text = mensagemDia.mensagem,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeMultiplier).sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }

                                        // Display custom notes for the slot (if there is any, otherwise show spacer or empty note placeholder)
                                        if (noteText.isNotEmpty()) {
                                            if ((isContextSlot || isMeaningSlot || isMessageSlot) && hasSavedContent) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                            Text(
                                                text = noteText,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeMultiplier).sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else if (!isContextSlot && !isMeaningSlot && !isMessageSlot) {
                                            Text(
                                                text = "",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.height(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Alarm Reminder Config
                            Box(modifier = Modifier.padding(start = 8.dp)) {
                                IconButton(
                                    onClick = { showReminderMenu = true }
                                ) {
                                    Icon(
                                        imageVector = if (isReminderSet) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                        contentDescription = "Lembrete",
                                        tint = if (isReminderSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showReminderMenu,
                                    onDismissRequest = { showReminderMenu = false }
                                ) {
                                    if (isReminderSet) {
                                        DropdownMenuItem(
                                            text = { Text(getLocalizedString(selectedLanguage, "desativar") ?: "Desativar") },
                                            onClick = {
                                                prefs.edit()
                                                    .putBoolean(reminderKey, false)
                                                    .remove(offsetKey)
                                                    .apply()
                                                isReminderSet = false
                                                showReminderMenu = false
                                                Toast.makeText(context, getLocalizedString(selectedLanguage, "reminder_removed"), Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                    val offsets = listOf(0, 5, 10, 15, 30, 60)
                                    offsets.forEach { offset ->
                                        val label = when (offset) {
                                            0 -> "No horário"
                                            60 -> "1 hora antes"
                                            else -> "$offset minutos antes"
                                        }
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                prefs.edit()
                                                    .putBoolean(reminderKey, true)
                                                    .putInt(offsetKey, offset)
                                                    .apply()
                                                isReminderSet = true
                                                reminderOffset = offset
                                                showReminderMenu = false
                                                val formatMsg = when (selectedLanguage) {
                                                    "en" -> "Reminder set for $label"
                                                    "es" -> "Recordatorio establecido para $label"
                                                    else -> "Lembrete definido para $label"
                                                }
                                                Toast.makeText(context, formatMsg, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
