package com.agendadevocional

import com.agendadevocional.model.MensagemDia
import com.agendadevocional.model.TimelineNota
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import android.media.AudioManager
import android.media.ToneGenerator
import java.io.File

@Composable
fun AnotacoesSection(
    mensagemDia: MensagemDia,
    onSaveAnotacao: (MensagemDia, String?) -> Unit,
    onSaveAudioPath: (MensagemDia, String?) -> Unit,
    audioRecorder: AndroidAudioRecorder,
    audioPlayer: AndroidAudioPlayer,
    fontSizeMultiplier: Float,
    selectedLanguage: String,
    onTimelineClick: (() -> Unit)? = null,
    timelineOpened: Boolean = false,
    timelineNotas: List<TimelineNota> = emptyList()
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = Texto, 1 = Áudio

    val note0800 = remember(mensagemDia.data, timelineNotas) {
        val dataIso = parseToIsoDate(mensagemDia.data) ?: mensagemDia.data
        timelineNotas.firstOrNull { it.data == dataIso && it.hora == 8 }?.texto ?: ""
    }

    val requestRecordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, getLocalizedString(selectedLanguage, "permissao_negada"), Toast.LENGTH_SHORT).show()
        }
    }

    // Audio recorder state
    var isRecording by remember { mutableStateOf(false) }
    var recordTimeSeconds by remember { mutableStateOf(0) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var isLongAudioMode by remember { mutableStateOf(false) }
    var recordingLimitSeconds by remember { mutableStateOf(60) }

    // Audio player state
    var playingFilePath by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playProgressMs by remember { mutableStateOf(0) }
    var audioDurationMs by remember { mutableStateOf(0) }

    // Sound alert generator
    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
        } catch (e: Exception) {
            null
        }
    }

    fun playBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Control timers for recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordTimeSeconds = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordTimeSeconds += 1
                val remaining = recordingLimitSeconds - recordTimeSeconds

                if (recordingLimitSeconds > 60) {
                    if (remaining == 60) {
                        playBeep()
                    } else if (remaining == 30) {
                        playBeep()
                    } else if (remaining in 1..10) {
                        playBeep()
                    }
                }

                if (recordTimeSeconds >= recordingLimitSeconds) {
                    audioRecorder.stop()
                    isRecording = false
                    val fileSaved = recordingFile
                    if (fileSaved != null) {
                        val currentPaths = mensagemDia.audioPath?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
                        val newPaths = currentPaths + fileSaved.absolutePath
                        onSaveAudioPath(mensagemDia, newPaths.joinToString("|"))
                        Toast.makeText(context, getLocalizedString(selectedLanguage, "limite_audio_atingido"), Toast.LENGTH_LONG).show()
                    }
                    recordingFile = null
                    break
                }
            }
        }
    }

    // UI Layout
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.TextSnippet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = getLocalizedString(selectedLanguage, "anotacoes").uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }
            if (onTimelineClick != null) {
                IconButton(onClick = { onTimelineClick() }) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = getLocalizedString(selectedLanguage, "tab_agenda"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Tabs clara/escura customizada com design premium
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(getLocalizedString(selectedLanguage, "tab_texto_ditado")) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(getLocalizedString(selectedLanguage, "tab_mensagem_voz")) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedTab == 0) {
            // ABA TEXTO
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTimelineClick?.invoke() }
            ) {
                OutlinedTextField(
                    value = note0800,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text(getLocalizedString(selectedLanguage, "hint_escreva")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    maxLines = 10
                )
                // Transparent overlay to catch clicks
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { onTimelineClick?.invoke() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botão de transcrever
                IconButton(
                    onClick = {
                        onTimelineClick?.invoke()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = getLocalizedString(selectedLanguage, "gravar"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            // ABA ÁUDIO (MENSAGEM DE VOZ)
            val audioFileList = remember(mensagemDia.audioPath) {
                mensagemDia.audioPath?.split("|")?.filter { it.isNotBlank() }?.map { File(it) } ?: emptyList()
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (audioFileList.isNotEmpty()) {
                    audioFileList.forEachIndexed { index, file ->
                        if (file.exists()) {
                            val isThisPlaying = playingFilePath == file.absolutePath && isPlaying
                            val progress = if (playingFilePath == file.absolutePath && audioDurationMs > 0) {
                                playProgressMs.toFloat() / audioDurationMs
                            } else 0f

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(16.dp)
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Play/Pause Button
                                        IconButton(
                                            onClick = {
                                                if (isThisPlaying) {
                                                    audioPlayer.pause()
                                                    isPlaying = false
                                                } else {
                                                    if (playingFilePath == file.absolutePath) {
                                                        audioPlayer.resume(
                                                            onProgress = { current, total ->
                                                                playProgressMs = current
                                                                audioDurationMs = total
                                                            }
                                                        )
                                                        isPlaying = true
                                                    } else {
                                                        playingFilePath = file.absolutePath
                                                        audioPlayer.playFile(
                                                            file = file,
                                                            onProgress = { current, total ->
                                                                playProgressMs = current
                                                                audioDurationMs = total
                                                            },
                                                            onCompletion = {
                                                                isPlaying = false
                                                                playingFilePath = null
                                                                playProgressMs = 0
                                                            }
                                                        )
                                                        isPlaying = true
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isThisPlaying) getLocalizedString(selectedLanguage, "pausar") else getLocalizedString(selectedLanguage, "tocar"),
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Progress Slider & Timers
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = String.format(getLocalizedString(selectedLanguage, "audio_titulo"), index + 1),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                EqualizerVisualizer(isPlaying = isThisPlaying, barColor = MaterialTheme.colorScheme.primary)
                                            }
                                            Slider(
                                                value = progress,
                                                onValueChange = { fraction ->
                                                    if (playingFilePath == file.absolutePath && audioDurationMs > 0) {
                                                        val targetPosition = (fraction * audioDurationMs).toInt()
                                                        playProgressMs = targetPosition
                                                        audioPlayer.seekTo(targetPosition)
                                                    }
                                                },
                                                colors = SliderDefaults.colors(
                                                    thumbColor = MaterialTheme.colorScheme.primary,
                                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                                )
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = if (playingFilePath == file.absolutePath) formatTimeMs(playProgressMs) else "00:00",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                                Text(
                                                    text = if (playingFilePath == file.absolutePath && audioDurationMs > 0) {
                                                        formatTimeMs(audioDurationMs)
                                                    } else {
                                                        "00:00"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Delete Audio Button
                                        IconButton(
                                            onClick = {
                                                if (playingFilePath == file.absolutePath) {
                                                    audioPlayer.stop()
                                                    isPlaying = false
                                                    playingFilePath = null
                                                    playProgressMs = 0
                                                    audioDurationMs = 0
                                                }
                                                try {
                                                    if (file.exists()) {
                                                        file.delete()
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                                val remainingPaths = audioFileList.filter { it != file }.map { it.absolutePath }
                                                onSaveAudioPath(mensagemDia, if (remainingPaths.isEmpty()) null else remainingPaths.joinToString("|"))
                                                Toast.makeText(context, getLocalizedString(selectedLanguage, "audio_removido"), Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = getLocalizedString(selectedLanguage, "excluir_audio"),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val recordPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    )

                    // Botão vermelho de gravação e Switch de Áudio Longo ao lado
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Spacer à esquerda para equilibrar o layout e manter o botão centralizado
                        Spacer(modifier = Modifier.width(64.dp))

                        IconButton(
                            onClick = {
                                if (recordPermission == PackageManager.PERMISSION_GRANTED) {
                                    if (isRecording) {
                                        audioRecorder.stop()
                                        isRecording = false
                                        val fileSaved = recordingFile
                                        if (fileSaved != null) {
                                            val currentPaths = mensagemDia.audioPath?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
                                            val newPaths = currentPaths + fileSaved.absolutePath
                                            onSaveAudioPath(mensagemDia, newPaths.joinToString("|"))
                                            Toast.makeText(context, getLocalizedString(selectedLanguage, "sucesso_audio"), Toast.LENGTH_SHORT).show()
                                        }
                                        recordingFile = null
                                    } else {
                                        audioPlayer.stop() // Para qualquer reprodução ativa
                                        isPlaying = false
                                        playingFilePath = null
                                        recordingLimitSeconds = if (isLongAudioMode) 3600 else 60

                                        val timestamp = System.currentTimeMillis()
                                        val newFile = File(context.filesDir, "audio_reflection_${mensagemDia.data.replace(" ", "_")}_$timestamp.m4a")
                                        recordingFile = newFile
                                        audioRecorder.start(newFile)
                                        isRecording = true
                                    }
                                } else {
                                    requestRecordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    CircleShape
                                )
                                .border(
                                    2.dp,
                                    if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isRecording) getLocalizedString(selectedLanguage, "parar_gravacao") else getLocalizedString(selectedLanguage, "gravar_audio"),
                                tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Switch(
                                checked = isLongAudioMode,
                                onCheckedChange = { checked ->
                                    isLongAudioMode = checked
                                    if (isRecording) {
                                        recordingLimitSeconds = if (checked) 3600 else 60
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.scale(0.85f)
                            )
                            Text(
                                text = getLocalizedString(selectedLanguage, "modo_audio_longo"),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val isApproachingLimit = isRecording && !isLongAudioMode && recordTimeSeconds >= 50
                    val isApproachingLongLimit = isRecording && isLongAudioMode && (recordingLimitSeconds - recordTimeSeconds <= 60)
                    
                    val timerColor = if (isApproachingLimit || isApproachingLongLimit) {
                        if (recordTimeSeconds % 2 == 0) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error
                    } else if (isRecording) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }

                    Text(
                        text = if (isRecording) {
                            String.format(getLocalizedString(selectedLanguage, "gravando") + " %02d:%02d", recordTimeSeconds / 60, recordTimeSeconds % 60)
                        } else {
                            getLocalizedString(selectedLanguage, "hint_gravar")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isRecording) FontWeight.Bold else FontWeight.Normal,
                        color = timerColor,
                        textAlign = TextAlign.Center
                    )

                    if (isApproachingLongLimit) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { recordingLimitSeconds += 3600 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(getLocalizedString(selectedLanguage, "estender_mais_uma_hora"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EqualizerVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val anims = listOf(
        transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b1"
        ),
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 350, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b2"
        ),
        transition.animateFloat(
            initialValue = 0.1f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b3"
        ),
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b4"
        ),
        transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b5"
        )
    )

    Row(
        modifier = modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        anims.forEach { anim ->
            val scale = if (isPlaying) anim.value else 0.15f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp * scale + 4.dp)
                    .background(barColor, shape = RoundedCornerShape(1.5.dp))
            )
        }
    }
}

fun formatTimeMs(timeMs: Int): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
