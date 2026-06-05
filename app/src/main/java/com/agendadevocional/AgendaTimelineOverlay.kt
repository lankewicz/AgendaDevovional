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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaTimelineDayPage(
    mensagemDia: MensagemDia,
    onClose: () -> Unit,
    onSaveAnotacao: (MensagemDia, String?) -> Unit,
    onSaveAudioPath: (MensagemDia, String?) -> Unit,
    audioRecorder: AndroidAudioRecorder,
    audioPlayer: AndroidAudioPlayer,
    fontSizeMultiplier: Float,
    selectedLanguage: String,
    viewMode: String,
    onViewModeChange: (String) -> Unit,
    onDateSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    
    val hoursList = (0..23).toList()
    
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
                        val selectedIso = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toLocalDate()
                            .toString()
                        onDateSelected(selectedIso)
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
    
    val msgIso = parseToIsoDate(mensagemDia.data)
    val isFuture = msgIso != null && msgIso > getTodayIso()
    val (displayVerse, displayReferencia, displayMensagem) = getDisplayDevotional(mensagemDia, selectedLanguage)
    val hasSavedContent = !mensagemDia.anotacao.isNullOrBlank() || !mensagemDia.audioPath.isNullOrBlank()
    
    val dataParts = mensagemDia.data.split(" de ")
    val dayNum = dataParts.getOrNull(0) ?: "01"
    val monthYear = if (dataParts.size >= 3) "${dataParts[1]} ${dataParts[2]}" else mensagemDia.data
    
    val dayOfWeekName = if (msgIso != null) {
        try {
            val sdfInput = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdfInput.parse(msgIso)
            if (date != null) {
                val locale = when (selectedLanguage) {
                    "en" -> Locale.US
                    "es" -> Locale.forLanguageTag("es")
                    else -> Locale.forLanguageTag("pt-BR")
                }
                val sdfOutput = java.text.SimpleDateFormat("EEEE", locale)
                sdfOutput.format(date).replaceFirstChar { it.uppercase() }
            } else {
                "Sexta-feira"
            }
        } catch (e: Exception) {
            "Sexta-feira"
        }
    } else {
        "Sexta-feira"
    }

    // Hourly notes state map
    val hourlyNotes = remember(mensagemDia.data) { mutableStateMapOf<Int, String>() }
    
    LaunchedEffect(mensagemDia.data) {
        val listToLoad = (0..23).toList()
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
    var editingText by remember(editingHour, mensagemDia.data) {
        val saved = editingHour?.let { prefs.getString("note_${mensagemDia.data}_$it", "") } ?: ""
        mutableStateOf(saved)
    }

    // Speech dictation state
    var isListening by remember { mutableStateOf(false) }
    var listeningFeedback by remember { mutableStateOf("") }

    val currentOnResult by rememberUpdatedState { partialResult: String ->
        editingText = if (editingText.isEmpty()) partialResult else "$editingText $partialResult"
    }
    val currentOnError by rememberUpdatedState { errorMsg: String ->
        isListening = false
        listeningFeedback = ""
        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
    }
    val currentOnReadyForSpeech by rememberUpdatedState {
        listeningFeedback = getLocalizedString(selectedLanguage, "ouvindo")
    }
    val currentOnEndOfSpeech by rememberUpdatedState {
        isListening = false
        listeningFeedback = ""
    }

    val speechToTextHelper = remember {
        SpeechToTextHelper(
            context = context,
            onResult = { currentOnResult(it) },
            onError = { currentOnError(it) },
            onReadyForSpeech = { currentOnReadyForSpeech() },
            onEndOfSpeech = { currentOnEndOfSpeech() },
            onPreparing = { listeningFeedback = it }
        )
    }

    DisposableEffect(speechToTextHelper) {
        onDispose {
            speechToTextHelper.stopListening()
        }
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
                        text = "“$displayVerse” ($displayReferencia)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            color = Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
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
                    
                    Column(modifier = Modifier.weight(1f)) {
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isAgendaActive = viewMode == "agenda"
                        IconButton(
                            onClick = { onViewModeChange("agenda") },
                            modifier = Modifier
                                .background(
                                    if (isAgendaActive) MaterialTheme.colorScheme.primaryContainer 
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), 
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = getLocalizedString(selectedLanguage, "tab_agenda"),
                                tint = if (isAgendaActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        val isNotepadActive = viewMode == "notepad"
                        IconButton(
                            onClick = { onViewModeChange("notepad") },
                            modifier = Modifier
                                .background(
                                    if (isNotepadActive) MaterialTheme.colorScheme.primaryContainer 
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), 
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = getLocalizedString(selectedLanguage, "tab_bloco_notas"),
                                tint = if (isNotepadActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Selecionar Data",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (viewMode == "agenda") {
                // 3. Visual Grid/Timeline
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val listState = rememberLazyListState()
                    
                    LaunchedEffect(Unit) {
                        listState.scrollToItem(8)
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 8.dp)
                    ) {
                        items(hoursList) { hour ->
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
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(
                                                    onDone = {
                                                        if (isListening) {
                                                            speechToTextHelper.stopListening()
                                                            isListening = false
                                                        }
                                                        val key = "note_${mensagemDia.data}_$hour"
                                                        if (editingText.isNotBlank()) {
                                                            prefs.edit().putString(key, editingText.trim()).apply()
                                                            hourlyNotes[hour] = editingText.trim()
                                                            if (hour == 8) {
                                                                onSaveAnotacao(mensagemDia, editingText.trim())
                                                            }
                                                        } else {
                                                            prefs.edit().remove(key).apply()
                                                            hourlyNotes.remove(hour)
                                                            if (hour == 8) {
                                                                onSaveAnotacao(mensagemDia, null)
                                                            }
                                                        }
                                                        editingHour = null
                                                    }
                                                ),
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
                                                // Delete button (Lixeirinha)
                                                IconButton(
                                                    onClick = {
                                                        if (isListening) {
                                                            speechToTextHelper.stopListening()
                                                            isListening = false
                                                        }
                                                        val key = "note_${mensagemDia.data}_$hour"
                                                        prefs.edit().remove(key).apply()
                                                        hourlyNotes.remove(hour)
                                                        if (hour == 8) {
                                                            onSaveAnotacao(mensagemDia, null)
                                                        }
                                                        editingHour = null
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Excluir",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
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
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                // Confirm (Save) button
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
                                                            if (hour == 8) {
                                                                onSaveAnotacao(mensagemDia, editingText.trim())
                                                            }
                                                        } else {
                                                            prefs.edit().remove(key).apply()
                                                            hourlyNotes.remove(hour)
                                                            if (hour == 8) {
                                                                onSaveAnotacao(mensagemDia, null)
                                                            }
                                                        }
                                                        editingHour = null
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Confirmar",
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
                                                isContextSlot && hasSavedContent && !isFuture -> {
                                                    Text(
                                                        text = mensagemDia.contexto,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeMultiplier).sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                isMeaningSlot && hasSavedContent && !isFuture -> {
                                                    Text(
                                                        text = mensagemDia.significado,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeMultiplier).sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                isMessageSlot && hasSavedContent -> {
                                                    val sectionTitle = if (isFuture) "" else getLocalizedString(selectedLanguage, "mensagem")
                                                    if (sectionTitle.isNotEmpty()) {
                                                        Text(
                                                            text = sectionTitle.uppercase(),
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.tertiary
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                    }
                                                    if (isFuture) {
                                                        if (displayMensagem.isNotEmpty()) {
                                                            Text(
                                                                text = displayMensagem,
                                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                                    fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeMultiplier).sp,
                                                                    fontStyle = FontStyle.Italic
                                                                ),
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                            )
                                                        }
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
                                            } else {
                                                val isShowingContent = when {
                                                    isContextSlot && hasSavedContent -> true
                                                    isMeaningSlot && hasSavedContent -> true
                                                    isMessageSlot && hasSavedContent -> true
                                                    else -> false
                                                }
                                                if (!isShowingContent) {
                                                    Text(
                                                        text = "",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.height(24.dp)
                                                    )
                                                }
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
            } else {
                // BLOCO DE NOTAS (Notepad) view
                val dailyNoteKey = "note_${mensagemDia.data}_8"
                var noteText by remember(mensagemDia.data) {
                    mutableStateOf(prefs.getString(dailyNoteKey, "") ?: "")
                }
                var isListeningNotepad by remember { mutableStateOf(false) }
                var listeningFeedbackNotepad by remember { mutableStateOf("") }

                val currentOnResultNotepad by rememberUpdatedState { partialResult: String ->
                    noteText = if (noteText.isEmpty()) partialResult else "$noteText $partialResult"
                    prefs.edit().putString(dailyNoteKey, noteText).apply()
                    hourlyNotes[8] = noteText
                    onSaveAnotacao(mensagemDia, noteText)
                }

                val speechToTextHelperNotepad = remember {
                    SpeechToTextHelper(
                        context = context,
                        onResult = { currentOnResultNotepad(it) },
                        onError = { errorMsg ->
                            isListeningNotepad = false
                            listeningFeedbackNotepad = ""
                            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                        },
                        onReadyForSpeech = {
                            listeningFeedbackNotepad = getLocalizedString(selectedLanguage, "ouvindo")
                        },
                        onEndOfSpeech = {
                            isListeningNotepad = false
                            listeningFeedbackNotepad = ""
                        },
                        onPreparing = { listeningFeedbackNotepad = it }
                    )
                }

                DisposableEffect(speechToTextHelperNotepad) {
                    onDispose {
                        speechToTextHelperNotepad.stopListening()
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { newVal ->
                            noteText = newVal
                            prefs.edit().putString(dailyNoteKey, newVal).apply()
                            hourlyNotes[8] = newVal
                            onSaveAnotacao(mensagemDia, newVal)
                        },
                        placeholder = { Text(getLocalizedString(selectedLanguage, "hint_escreva")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        shape = RoundedCornerShape(16.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeMultiplier).sp
                        )
                    )

                    if (isListeningNotepad && listeningFeedbackNotepad.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = listeningFeedbackNotepad,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isListeningNotepad) {
                                    speechToTextHelperNotepad.stopListening()
                                    isListeningNotepad = false
                                } else {
                                    val recordPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                    if (recordPermission == PackageManager.PERMISSION_GRANTED) {
                                        isListeningNotepad = true
                                        speechToTextHelperNotepad.startListening()
                                    } else {
                                        requestRecordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    if (isListeningNotepad) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    CircleShape
                                )
                                .border(
                                    2.dp,
                                    if (isListeningNotepad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isListeningNotepad) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Ditar",
                                tint = if (isListeningNotepad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AgendaTimelineOverlay(
    mensagens: List<MensagemDia>,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onClose: () -> Unit,
    onSaveAnotacao: (MensagemDia, String?) -> Unit,
    onSaveAudioPath: (MensagemDia, String?) -> Unit,
    audioRecorder: AndroidAudioRecorder,
    audioPlayer: AndroidAudioPlayer,
    fontSizeMultiplier: Float,
    selectedLanguage: String
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, mensagens.lastIndex),
        pageCount = { mensagens.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var viewMode by remember { mutableStateOf(prefs.getString("last_timeline_view_mode", "agenda") ?: "agenda") }

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            if (page < mensagens.size) {
                AgendaTimelineDayPage(
                    mensagemDia = mensagens[page],
                    onClose = onClose,
                    onSaveAnotacao = onSaveAnotacao,
                    onSaveAudioPath = onSaveAudioPath,
                    audioRecorder = audioRecorder,
                    audioPlayer = audioPlayer,
                    fontSizeMultiplier = fontSizeMultiplier,
                    selectedLanguage = selectedLanguage,
                    viewMode = viewMode,
                    onViewModeChange = { newMode ->
                        viewMode = newMode
                        prefs.edit().putString("last_timeline_view_mode", newMode).apply()
                    },
                    onDateSelected = { selectedIso ->
                        val index = mensagens.indexOfFirst {
                            parseToIsoDate(it.data) == selectedIso
                        }
                        if (index >= 0) {
                            coroutineScope.launch {
                                pagerState.scrollToPage(index)
                            }
                        }
                    }
                )
            }
        }
    }
}
