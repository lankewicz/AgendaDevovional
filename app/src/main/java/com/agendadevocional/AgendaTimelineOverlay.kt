package com.agendadevocional

import com.agendadevocional.model.MensagemDia
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                        val isNotesSlot = hour == 15
                        
                        val reminderKey = "reminder_${mensagemDia.data}_$hour"
                        var isReminderSet by remember(mensagemDia.data, hour) {
                            mutableStateOf(prefs.getBoolean(reminderKey, false))
                        }

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
                                    .border(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                when {
                                    isContextSlot -> {
                                        if (hasSavedContent) {
                                            Text(
                                                text = mensagemDia.contexto,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeMultiplier).sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        } else {
                                            Text(
                                                text = "",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                modifier = Modifier.height(24.dp)
                                            )
                                        }
                                    }
                                    isMeaningSlot -> {
                                        if (hasSavedContent) {
                                            Text(
                                                text = mensagemDia.significado,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeMultiplier).sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        } else {
                                            Text(
                                                text = "",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                modifier = Modifier.height(24.dp)
                                            )
                                        }
                                    }
                                    isMessageSlot -> {
                                        if (hasSavedContent) {
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
                                                            3 -> "“Todo tiene su tempo.” — Eclesiástes 3:1"
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
                                        } else {
                                            Text(
                                                text = "",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                modifier = Modifier.height(24.dp)
                                            )
                                        }
                                    }
                                    isNotesSlot -> {
                                        AnotacoesSection(
                                            mensagemDia = mensagemDia,
                                            onSaveAnotacao = onSaveAnotacao,
                                            onSaveAudioPath = onSaveAudioPath,
                                            audioRecorder = audioRecorder,
                                            audioPlayer = audioPlayer,
                                            fontSizeMultiplier = fontSizeMultiplier,
                                            selectedLanguage = selectedLanguage
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }
                                }
                            }
                            
                            // Reminder Toggle (Right Column) - omit on the Notes slot
                            if (!isNotesSlot) {
                                IconButton(
                                    onClick = {
                                        val newState = !isReminderSet
                                        prefs.edit().putBoolean(reminderKey, newState).apply()
                                        isReminderSet = newState
                                        val msg = if (newState) getLocalizedString(selectedLanguage, "reminder_set") else getLocalizedString(selectedLanguage, "reminder_removed")
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isReminderSet) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                        contentDescription = "Lembrete",
                                        tint = if (isReminderSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(48.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
