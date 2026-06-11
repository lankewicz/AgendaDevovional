package com.agendadevocional

import com.agendadevocional.model.MensagemDia
import com.agendadevocional.model.TimelineNota
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    onDateSelected: (String) -> Unit,
    onShowDatePicker: () -> Unit,
    timelineNotas: List<TimelineNota>,
    onSaveTimelineNota: (String, Int, String) -> Unit,
    onDeleteTimelineNota: (String, Int) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    
    val hoursList = (0..23).toList()
    

    
    val msgIso = parseToIsoDate(mensagemDia.data)
    val isFuture = msgIso != null && msgIso > getTodayIso()
    val (displayVerse, displayReferencia, displayMensagem) = getDisplayDevotional(mensagemDia, selectedLanguage)
    val hasSavedContent = !mensagemDia.anotacao.isNullOrBlank() || !mensagemDia.audioPath.isNullOrBlank()
    
    val parsedDate = if (msgIso != null) {
        try {
            java.time.LocalDate.parse(msgIso)
        } catch (e: Exception) {
            null
        }
    } else null

    val dayNum = parsedDate?.dayOfMonth?.toString()?.padStart(2, '0') ?: "08"
    val monthStr = parsedDate?.let {
        val locale = when (selectedLanguage) {
            "en" -> Locale.US
            "es" -> Locale.forLanguageTag("es")
            else -> Locale.forLanguageTag("pt-BR")
        }
        it.month.getDisplayName(java.time.format.TextStyle.FULL, locale)
    } ?: "junho"
    val yearStr = parsedDate?.year?.toString() ?: "2026"

    val monthNum = when (monthStr.lowercase(Locale.getDefault()).trim()) {
        "janeiro", "january", "enero", "jan" -> "01"
        "fevereiro", "february", "febrero", "feb" -> "02"
        "março", "march", "marzo", "mar" -> "03"
        "abril", "april", "abr" -> "04"
        "maio", "may", "mayo" -> "05"
        "junho", "june", "junio", "jun" -> "06"
        "julho", "july", "julio", "jul" -> "07"
        "agosto", "august", "ago" -> "08"
        "setembro", "september", "septiembre", "set", "sep" -> "09"
        "outubro", "october", "octubre", "out", "oct" -> "10"
        "novembro", "november", "noviembre", "nov" -> "11"
        "dezembro", "december", "diciembre", "dez", "dec" -> "12"
        else -> {
            msgIso?.split("-")?.getOrNull(1) ?: "01"
        }
    }
    
    val dayOfWeekName = run {
        val locale = when (selectedLanguage) {
            "en" -> Locale.US
            "es" -> Locale.forLanguageTag("es")
            else -> Locale.forLanguageTag("pt-BR")
        }
        val date = if (msgIso != null) {
            try {
                java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(msgIso)
            } catch (e: Exception) {
                null
            }
        } else null
        
        val targetDate = date ?: java.util.Date()
        try {
            java.text.SimpleDateFormat("EEEE", locale).format(targetDate).replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            when (selectedLanguage) {
                "en" -> "Friday"
                "es" -> "Viernes"
                else -> "Sexta-feira"
            }
        }
    }

    // Hourly notes state map
    val hourlyNotes = remember(mensagemDia.data, timelineNotas) {
        val map = mutableStateMapOf<Int, String>()
        val dataIso = parseToIsoDate(mensagemDia.data) ?: mensagemDia.data
        timelineNotas.filter { it.data == dataIso }.forEach {
            map[it.hora] = it.texto
        }
        map
    }

    var editingHour by remember { mutableStateOf<Int?>(null) }
    var editingText by remember(editingHour, mensagemDia.data) {
        val saved = editingHour?.let { hourlyNotes[it] } ?: ""
        mutableStateOf(TextFieldValue(text = saved, selection = TextRange(saved.length)))
    }

    // Speech dictation state
    var isListening by remember { mutableStateOf(false) }
    var listeningFeedback by remember { mutableStateOf("") }
    var initialText by remember { mutableStateOf("") }

    val currentOnResult by rememberUpdatedState { partialResult: String ->
        val newText = if (initialText.isEmpty()) partialResult else "$initialText $partialResult"
        editingText = TextFieldValue(text = newText, selection = TextRange(newText.length))
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
            initialText = editingText.text
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = getLocalizedString(selectedLanguage, "desc_voltar"),
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
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val screenWidth = configuration.screenWidthDp
                val isSmallScreen = screenWidth < 600

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isSmallScreen) 12.dp else 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Date and Calendar block
                    if (isSmallScreen) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onShowDatePicker() }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = dayNum,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            val shortYear = if (yearStr.length >= 2) yearStr.takeLast(2) else yearStr
                            Text(
                                text = "$monthNum/$shortYear",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onShowDatePicker() }
                        ) {
                            // Calendar Day Card (Increased by 30%)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = dayNum,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                                Text(
                                    text = dayOfWeekName.take(3).lowercase(Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Month & Year next to the Card
                            Column {
                                Text(
                                    text = monthStr.lowercase(Locale.getDefault()),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = yearStr,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(if (isSmallScreen) 8.dp else 16.dp))

                    // Title centered in the middle
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (viewMode == "notepad") {
                                getLocalizedString(selectedLanguage, "tab_bloco_notas").uppercase()
                            } else {
                                getLocalizedString(selectedLanguage, "app_title").uppercase()
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isSmallScreen) 14.sp else 18.sp,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(if (isSmallScreen) 8.dp else 16.dp))

                    if (isSmallScreen) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val isAgendaActive = viewMode == "agenda"
                            IconButton(
                                onClick = { onViewModeChange("agenda") },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isAgendaActive) MaterialTheme.colorScheme.primaryContainer 
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), 
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = getLocalizedString(selectedLanguage, "tab_agenda"),
                                    tint = if (isAgendaActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            val isNotepadActive = viewMode == "notepad"
                            IconButton(
                                onClick = { onViewModeChange("notepad") },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isNotepadActive) MaterialTheme.colorScheme.primaryContainer 
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), 
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = getLocalizedString(selectedLanguage, "tab_bloco_notas"),
                                    tint = if (isNotepadActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
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
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
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
                                                     .defaultMinSize(minHeight = 36.dp)
                                                     .focusRequester(focusRequester)
                                                     .onFocusChanged { focusState ->
                                                         if (!focusState.isFocused) {
                                                             val textToSave = editingText.text.trim()
                                                             if (textToSave.isNotEmpty()) {
                                                                 onSaveTimelineNota(mensagemDia.data, hour, textToSave)
                                                                 if (hour == 8) {
                                                                     onSaveAnotacao(mensagemDia, textToSave)
                                                                 }
                                                             } else {
                                                                 onDeleteTimelineNota(mensagemDia.data, hour)
                                                                 if (hour == 8) {
                                                                     onSaveAnotacao(mensagemDia, null)
                                                                 }
                                                             }
                                                         }
                                                     },
                                                 keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                 keyboardActions = KeyboardActions(
                                                     onDone = {
                                                         if (isListening) {
                                                             speechToTextHelper.stopListening()
                                                             isListening = false
                                                         }
                                                         val textToSave = editingText.text.trim()
                                                         if (textToSave.isNotEmpty()) {
                                                             onSaveTimelineNota(mensagemDia.data, hour, textToSave)
                                                             if (hour == 8) {
                                                                 onSaveAnotacao(mensagemDia, textToSave)
                                                             }
                                                         } else {
                                                             onDeleteTimelineNota(mensagemDia.data, hour)
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
                                                         if (editingText.text.isEmpty()) {
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

                                            if (!isFuture) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    val items = listOf(
                                                         Pair(getLocalizedString(selectedLanguage, "chip_versiculo"), "$displayVerse ($displayReferencia)"),
                                                         Pair(getLocalizedString(selectedLanguage, "chip_contexto"), mensagemDia.contexto),
                                                         Pair(getLocalizedString(selectedLanguage, "chip_significado"), mensagemDia.significado),
                                                         Pair(getLocalizedString(selectedLanguage, "chip_mensagem"), displayMensagem)
                                                     )
                                                    items.forEach { (label, value) ->
                                                        if (value.isNotBlank()) {
                                                            SuggestionChip(
                                                                onClick = {
                                                                    val newText = if (editingText.text.isEmpty()) value else "${editingText.text}\n$value"
                                                                    editingText = TextFieldValue(text = newText, selection = TextRange(newText.length))
                                                                },
                                                                label = { Text(text = label, fontSize = 11.sp) },
                                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                                                    labelColor = MaterialTheme.colorScheme.primary
                                                                ),
                                                                border = null
                                                            )
                                                        }
                                                    }
                                                }
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
                                                                initialText = editingText.text
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
                                                        contentDescription = getLocalizedString(selectedLanguage, "gravar"),
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
                                                        onDeleteTimelineNota(mensagemDia.data, hour)
                                                        if (hour == 8) {
                                                            onSaveAnotacao(mensagemDia, null)
                                                        }
                                                        editingHour = null
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = getLocalizedString(selectedLanguage, "excluir_audio"),
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
                                                        contentDescription = getLocalizedString(selectedLanguage, "cancelar"),
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
                                                        val textToSave = editingText.text.trim()
                                                        if (textToSave.isNotEmpty()) {
                                                            onSaveTimelineNota(mensagemDia.data, hour, textToSave)
                                                            if (hour == 8) {
                                                                onSaveAnotacao(mensagemDia, textToSave)
                                                            }
                                                        } else {
                                                            onDeleteTimelineNota(mensagemDia.data, hour)
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
                                                        contentDescription = getLocalizedString(selectedLanguage, "confirmar"),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // READ MODE OR DEFAULT VIEW FOR THE CARD
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            if (noteText.isNotEmpty()) {
                                                Text(
                                                    text = noteText,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeMultiplier).sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                Text(
                                                    text = "",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.height(36.dp)
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
                                            contentDescription = getLocalizedString(selectedLanguage, "config_horario"),
                                            tint = if (isReminderSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showReminderMenu,
                                        onDismissRequest = { showReminderMenu = false }
                                    ) {
                                        if (isReminderSet) {
                                            DropdownMenuItem(
                                                text = { Text(getLocalizedString(selectedLanguage, "desc_desativar")) },
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
                                                0 -> getLocalizedString(selectedLanguage, "reminder_on_time")
                                                60 -> getLocalizedString(selectedLanguage, "reminder_one_hour_before")
                                                else -> String.format(getLocalizedString(selectedLanguage, "reminder_minutes_before"), offset)
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
                var noteText by remember(mensagemDia.data, timelineNotas) {
                    val dataIso = parseToIsoDate(mensagemDia.data) ?: mensagemDia.data
                    val saved = timelineNotas.firstOrNull { it.data == dataIso && it.hora == 8 }?.texto ?: ""
                    mutableStateOf(TextFieldValue(text = saved, selection = TextRange(saved.length)))
                }
                var isListeningNotepad by remember { mutableStateOf(false) }
                var listeningFeedbackNotepad by remember { mutableStateOf("") }
                var initialTextNotepad by remember { mutableStateOf("") }

                val currentOnResultNotepad by rememberUpdatedState { partialResult: String ->
                    val newText = if (initialTextNotepad.isEmpty()) partialResult else "$initialTextNotepad $partialResult"
                    noteText = TextFieldValue(text = newText, selection = TextRange(newText.length))
                    onSaveTimelineNota(mensagemDia.data, 8, newText)
                    onSaveAnotacao(mensagemDia, newText)
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

                val requestRecordPermissionLauncherNotepad = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        initialTextNotepad = noteText.text
                        isListeningNotepad = true
                        speechToTextHelperNotepad.startListening()
                    } else {
                        Toast.makeText(context, getLocalizedString(selectedLanguage, "permissao_negada"), Toast.LENGTH_SHORT).show()
                    }
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
                            onSaveTimelineNota(mensagemDia.data, 8, newVal.text)
                            onSaveAnotacao(mensagemDia, newVal.text)
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
                                        initialTextNotepad = noteText.text
                                        isListeningNotepad = true
                                        speechToTextHelperNotepad.startListening()
                                    } else {
                                        requestRecordPermissionLauncherNotepad.launch(Manifest.permission.RECORD_AUDIO)
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
                                contentDescription = getLocalizedString(selectedLanguage, "gravar"),
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
    selectedLanguage: String,
    timelineNotas: List<TimelineNota>,
    onSaveTimelineNota: (String, Int, String) -> Unit,
    onDeleteTimelineNota: (String, Int) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, mensagens.lastIndex),
        pageCount = { mensagens.size }
    )
    val coroutineScope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
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
// Date picker dialog
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val selectedDate = java.time.Instant.ofEpochMilli(selectedMillis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                            val selectedIso = selectedDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                            val targetIndex = mensagens.indexOfFirst { parseToIsoDate(it.data) == selectedIso }
                            if (targetIndex >= 0) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(targetIndex)
                                }
                            } else {
                                Toast.makeText(context, "Data não encontrada", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        // Horizontal pager
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
                    },
                    onShowDatePicker = { showDatePicker = true },
                    timelineNotas = timelineNotas,
                    onSaveTimelineNota = onSaveTimelineNota,
                    onDeleteTimelineNota = onDeleteTimelineNota
                )
            }
        }
    }
}
