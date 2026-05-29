package com.agendadevocional

import com.agendadevocional.model.MensagemDia
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import java.io.File

@Composable
fun AnotacoesSection(
    mensagemDia: MensagemDia,
    onSaveAnotacao: (MensagemDia, String?) -> Unit,
    onSaveAudioPath: (MensagemDia, String?) -> Unit,
    audioRecorder: AndroidAudioRecorder,
    audioPlayer: AndroidAudioPlayer,
    fontSizeMultiplier: Float,
    selectedLanguage: String
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = Texto, 1 = Áudio

    // Estado do Texto
    var userNotes by remember(mensagemDia.data) { mutableStateOf(mensagemDia.anotacao ?: "") }
    val isModified = remember(userNotes, mensagemDia.anotacao) {
        userNotes.trim() != (mensagemDia.anotacao ?: "").trim()
    }

    // Speech recognition state
    var isListening by remember { mutableStateOf(false) }
    var listeningFeedback by remember { mutableStateOf("") }

    val speechToTextHelper = remember {
        SpeechToTextHelper(
            context = context,
            onResult = { partialResult ->
                userNotes = if (userNotes.isEmpty()) partialResult else "$userNotes $partialResult"
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
            if (selectedTab == 0) {
                // Modo ditado
                isListening = true
                speechToTextHelper.startListening()
            }
        } else {
            Toast.makeText(context, getLocalizedString(selectedLanguage, "permissao_negada"), Toast.LENGTH_SHORT).show()
        }
    }

    // Audio recorder state
    var isRecording by remember { mutableStateOf(false) }
    var recordTimeSeconds by remember { mutableStateOf(0) }
    var audioFile by remember(mensagemDia.data) {
        mutableStateOf(mensagemDia.audioPath?.let { File(it) })
    }

    // Audio player state
    var isPlaying by remember { mutableStateOf(false) }
    var playProgressMs by remember { mutableStateOf(0) }
    var audioDurationMs by remember { mutableStateOf(0) }

    // Control timers for recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordTimeSeconds = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordTimeSeconds += 1
            }
        }
    }

    // UI Layout
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
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

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            // ABA TEXTO
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = userNotes,
                    onValueChange = { userNotes = it },
                    placeholder = { Text(getLocalizedString(selectedLanguage, "hint_escreva")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    maxLines = 10
                )

                if (isListening) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = listeningFeedback,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    isListening = false
                                    speechToTextHelper.stopListening()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(getLocalizedString(selectedLanguage, "parar"), color = Color.White)
                            }
                        }
                    }
                }
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
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isListening) MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = getLocalizedString(selectedLanguage, "gravar"),
                        tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                // Botão de salvar
                if (isModified) {
                    Button(
                        onClick = {
                            onSaveAnotacao(mensagemDia, userNotes.takeIf { it.isNotBlank() })
                            Toast.makeText(context, getLocalizedString(selectedLanguage, "sucesso_salvar"), Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getLocalizedString(selectedLanguage, "salvar"))
                    }
                }
            }
        } else {
            // ABA ÁUDIO (MENSAGEM DE VOZ)
            val currentAudioFile = audioFile
            if (currentAudioFile == null || !currentAudioFile.exists()) {
                // NÃO TEM GRAVAÇÃO: Exibe o botão de gravar
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

                    // Botão vermelho de gravação
                    IconButton(
                        onClick = {
                            if (recordPermission == PackageManager.PERMISSION_GRANTED) {
                                if (isRecording) {
                                    audioRecorder.stop()
                                    isRecording = false
                                    val savedFile = File(context.filesDir, "audio_reflection_${mensagemDia.data.replace(" ", "_")}.m4a")
                                    audioFile = savedFile
                                    onSaveAudioPath(mensagemDia, savedFile.absolutePath)
                                    Toast.makeText(context, getLocalizedString(selectedLanguage, "sucesso_audio"), Toast.LENGTH_SHORT).show()
                                } else {
                                    audioPlayer.stop() // Para qualquer reprodução ativa
                                    isPlaying = false
                                    val savedFile = File(context.filesDir, "audio_reflection_${mensagemDia.data.replace(" ", "_")}.m4a")
                                    audioRecorder.start(savedFile)
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isRecording) {
                            String.format(getLocalizedString(selectedLanguage, "gravando") + " %02d:%02d", recordTimeSeconds / 60, recordTimeSeconds % 60)
                        } else {
                            getLocalizedString(selectedLanguage, "hint_gravar")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isRecording) FontWeight.Bold else FontWeight.Normal,
                        color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // TEM GRAVAÇÃO: Exibe o player de áudio premium
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
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
                                    if (isPlaying) {
                                        audioPlayer.pause()
                                        isPlaying = false
                                    } else {
                                        if (audioPlayer.getCurrentPosition() > 0 && playProgressMs > 0) {
                                            audioPlayer.resume(
                                                onProgress = { current, total ->
                                                    playProgressMs = current
                                                    audioDurationMs = total
                                                }
                                            )
                                            isPlaying = true
                                        } else {
                                            audioPlayer.playFile(
                                                file = currentAudioFile,
                                                onProgress = { current, total ->
                                                    playProgressMs = current
                                                    audioDurationMs = total
                                                },
                                                onCompletion = {
                                                    isPlaying = false
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
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) getLocalizedString(selectedLanguage, "pausar") else getLocalizedString(selectedLanguage, "tocar"),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Progress Slider & Timers
                            Column(modifier = Modifier.weight(1f)) {
                                Slider(
                                    value = if (audioDurationMs > 0) playProgressMs.toFloat() / audioDurationMs else 0f,
                                    onValueChange = { fraction ->
                                        if (audioDurationMs > 0) {
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
                                        text = formatTimeMs(playProgressMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = formatTimeMs(audioDurationMs.takeIf { it > 0 } ?: audioPlayer.getDuration()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Delete Audio Button
                            IconButton(
                                onClick = {
                                    audioPlayer.stop()
                                    isPlaying = false
                                    playProgressMs = 0
                                    audioDurationMs = 0
                                    try {
                                        if (currentAudioFile.exists()) {
                                            currentAudioFile.delete()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    audioFile = null
                                    onSaveAudioPath(mensagemDia, null)
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
}

fun formatTimeMs(timeMs: Int): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
